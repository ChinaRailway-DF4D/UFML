package io.github.ufml.launcher.util;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class AppPaths {
    private static final String HOME_PROPERTY = "ufml.home";
    private static final Path APP_HOME = determineHome();
    private static final Path CONFIG_DIRECTORY = APP_HOME.resolve("Config");

    private AppPaths() {
    }

    private static Path determineHome() {
        String override = System.getProperty(HOME_PROPERTY);
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        Path launcherDirectory = launcherDirectory();
        for (Path candidate = launcherDirectory; candidate != null; candidate = candidate.getParent()) {
            if (hasPortableData(candidate)) return candidate;
            Path nested = candidate.resolve("UFML");
            if (hasPortableData(nested)) return nested;
        }
        return launcherDirectory.resolve("UFML").toAbsolutePath().normalize();
    }

    private static boolean hasPortableData(Path directory) {
        return directory != null
                && (Files.isDirectory(directory.resolve("Config"))
                || Files.isDirectory(directory.resolve("Minecraft")));
    }

    private static Path launcherDirectory() {
        String command = ProcessHandle.current().info().command().orElse("");
        String executable = Path.of(command).getFileName().toString().toLowerCase();
        if (!command.isBlank() && !executable.startsWith("java")) {
            Path path = Path.of(command).toAbsolutePath().normalize();
            return Files.isDirectory(path) ? path : path.getParent();
        }
        try {
            URI location = AppPaths.class.getProtectionDomain().getCodeSource().getLocation().toURI();
            Path path = Path.of(location).toAbsolutePath().normalize();
            return Files.isDirectory(path) ? path : path.getParent();
        } catch (Exception ex) {
            return Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        }
    }

    public static Path home() {
        return APP_HOME;
    }

    public static Path configDirectory() {
        return CONFIG_DIRECTORY;
    }

    public static Path configFile() {
        return CONFIG_DIRECTORY.resolve("config.json");
    }

    public static Path cache() {
        return CONFIG_DIRECTORY.resolve("cache");
    }

    public static Path runtimes() {
        return CONFIG_DIRECTORY.resolve("runtimes");
    }

    public static Path logs() {
        return CONFIG_DIRECTORY.resolve("logs");
    }

    public static Path versionSettingsFile() {
        return CONFIG_DIRECTORY.resolve("version-settings.json");
    }

    public static Path defaultGameDirectory() {
        return APP_HOME.resolve("Minecraft");
    }

    public static void initializeLocalData() {
        try {
            Files.createDirectories(CONFIG_DIRECTORY);
            Files.createDirectories(defaultGameDirectory());
            Files.createDirectories(cache());
            Files.createDirectories(runtimes());
            Files.createDirectories(logs());
            migrateLegacyData();
        } catch (IOException ex) {
            LauncherLog.warn("初始化便携目录失败: " + ex.getMessage());
        }
    }

    private static void migrateLegacyData() {
        String appData = System.getenv("APPDATA");
        if (appData == null || appData.isBlank()) return;
        Path legacy = Path.of(appData, "UFML").toAbsolutePath().normalize();
        if (!Files.isDirectory(legacy) || legacy.equals(APP_HOME)) return;

        migrateConfig(legacy);
        migrateDirectory(legacy.resolve("minecraft"), defaultGameDirectory(), legacy, "游戏实例");
        migrateDirectory(legacy.resolve("runtimes"), runtimes(), legacy, "Java 运行时");
        migrateDirectory(legacy.resolve("cache"), cache(), legacy, "元数据缓存");
        migrateDirectory(legacy.resolve("logs"), logs(), legacy, "运行日志");
        removeEmptyLegacyRoot(legacy);
    }

    private static void migrateConfig(Path legacy) {
        Path oldConfig = legacy.resolve("config.json");
        if (!Files.isRegularFile(oldConfig)) return;
        try {
            if (!Files.isRegularFile(configFile())) {
                Map<String, Object> json = Json.object(Json.parse(Files.readString(oldConfig, StandardCharsets.UTF_8)));
                json.put("gameDirectory", defaultGameDirectory().toString());
                Files.createDirectories(configFile().getParent());
                Files.writeString(configFile(), Json.stringifyPretty(json) + System.lineSeparator(),
                        StandardCharsets.UTF_8);
                LauncherLog.info("已迁移配置文件到 " + configFile());
            } else if (Files.mismatch(oldConfig, configFile()) != -1) {
                Path archived = legacyCopyPath(configFile());
                Files.copy(oldConfig, archived);
                LauncherLog.warn("旧配置文件与当前配置不同，已保留为 " + archived);
            }
            Files.deleteIfExists(oldConfig);
        } catch (Exception ex) {
            LauncherLog.warn("迁移配置文件失败: " + ex.getMessage());
        }
    }

    private static void migrateDirectory(Path source, Path target, Path legacyRoot, String label) {
        if (!Files.isDirectory(source)) return;
        try {
            Files.createDirectories(target);
            Map<Path, Path> merged = mergeTree(source, target);
            for (Map.Entry<Path, Path> entry : merged.entrySet()) {
                if (Files.mismatch(entry.getKey(), entry.getValue()) != -1) {
                    throw new IOException("迁移校验失败: " + entry.getKey());
                }
            }
            PathSafety.deleteDirectory(source, legacyRoot);
            LauncherLog.info("已合并" + label + "到 " + target + "（" + merged.size() + " 个文件）");
        } catch (Exception ex) {
            LauncherLog.warn("合并" + label + "失败，已保留旧数据: " + ex.getMessage());
        }
    }

    public static Map<Path, Path> mergeTree(Path source, Path target) throws IOException {
        Path targetRoot = target.toAbsolutePath().normalize();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(source)) {
            walk.filter(Files::isRegularFile).forEach(files::add);
        }
        Map<Path, Path> stored = new LinkedHashMap<>();
        for (Path file : files) {
            Path destination = targetRoot.resolve(source.relativize(file).toString()).normalize();
            if (!destination.startsWith(targetRoot)) {
                throw new IOException("不安全的迁移路径: " + file);
            }
            Files.createDirectories(destination.getParent());
            if (!Files.exists(destination)) {
                copyFile(file, destination);
                stored.put(file, destination);
            } else if (Files.isRegularFile(destination) && Files.mismatch(file, destination) == -1) {
                stored.put(file, destination);
            } else {
                Path archived = legacyCopyPath(destination);
                Files.copy(file, archived);
                stored.put(file, archived);
                LauncherLog.warn("迁移文件冲突，保留两侧副本: " + destination + " -> " + archived);
            }
        }
        return stored;
    }

    private static void copyFile(Path source, Path destination) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".ufml-tmp-" + UUID.randomUUID());
        try {
            Files.copy(source, temporary, StandardCopyOption.REPLACE_EXISTING);
            try {
                Files.move(temporary, destination,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static Path legacyCopyPath(Path destination) {
        Path parent = destination.getParent();
        Path candidate = parent.resolve(destination.getFileName() + ".legacy");
        int index = 2;
        while (Files.exists(candidate)) {
            candidate = parent.resolve(destination.getFileName() + ".legacy-" + index++);
        }
        return candidate;
    }

    private static void removeEmptyLegacyRoot(Path legacy) {
        try (Stream<Path> walk = Files.walk(legacy)) {
            if (walk.anyMatch(Files::isRegularFile)) return;
        } catch (IOException ex) {
            return;
        }
        try {
            PathSafety.deleteDirectory(legacy, legacy.getParent());
            LauncherLog.info("旧版数据目录已清理: " + legacy);
        } catch (Exception ex) {
            LauncherLog.warn("无法清理旧版数据目录: " + ex.getMessage());
        }
    }

}
