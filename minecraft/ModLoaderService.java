package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.AppPaths;
import io.github.ufml.launcher.util.Json;
import io.github.ufml.launcher.util.LauncherLog;
import io.github.ufml.launcher.util.PathSafety;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public final class ModLoaderService {
    public static final String FABRIC_META = "https://meta.fabricmc.net/v2";
    public static final String FORGE_MAVEN = "https://maven.minecraftforge.net";
    public static final String MODRINTH_API = "https://api.modrinth.com/v2";

    private static final Pattern XML_VERSION = Pattern.compile("<version>([^<]+)</version>");
    private final Downloader downloader;
    private final MinecraftInstaller installer;

    public ModLoaderService(Downloader downloader, MinecraftInstaller installer) {
        this.downloader = downloader;
        this.installer = installer;
    }

    public LoaderResult install(ModLoaderType type, String minecraftVersion, Path gameDirectory,
                                Path javaExecutable, ProgressListener progress)
            throws IOException, InterruptedException {
        String id;
        String detail;
        switch (type) {
            case VANILLA -> {
                return new LoaderResult(minecraftVersion, "Minecraft " + minecraftVersion);
            }
            case FABRIC -> {
                id = installFabric(minecraftVersion, gameDirectory, progress);
                detail = "Fabric Loader 已安装";
            }
            case FABRIC_API -> {
                id = installFabric(minecraftVersion, gameDirectory, progress);
                String api = installFabricApi(minecraftVersion, gameDirectory, progress);
                detail = "Fabric Loader 与 " + api + " 已安装";
            }
            case FORGE -> {
                if (javaExecutable == null) throw new IOException("安装 Forge 需要可用的 Java");
                id = installForge(minecraftVersion, gameDirectory, javaExecutable, progress);
                detail = "Forge 已安装";
            }
            default -> throw new IllegalStateException("未知加载器: " + type);
        }
        return new LoaderResult(id, detail + " · 实例 " + id);
    }

    public String installFabric(String minecraftVersion, Path gameDirectory, ProgressListener progress)
            throws IOException, InterruptedException {
        progress.stage("准备 Fabric", "查询可用的 Fabric Loader", -1);
        String versionsUrl = FABRIC_META + "/versions/loader/" + encode(minecraftVersion);
        List<Object> loaders = Json.array(Json.parse(downloader.fetchString(versionsUrl)));
        if (loaders.isEmpty()) {
            throw new IOException("Fabric 暂不支持 Minecraft " + minecraftVersion);
        }
        Map<String, Object> chosen = null;
        for (Object raw : loaders) {
            Map<String, Object> entry = Json.object(raw);
            Map<String, Object> loader = Json.object(entry, "loader");
            if (chosen == null) chosen = entry;
            if (Json.bool(loader, "stable", false)) {
                chosen = entry;
                break;
            }
        }
        String loaderVersion = Json.string(Json.object(chosen, "loader"), "version");
        if (loaderVersion == null || loaderVersion.isBlank()) {
            throw new IOException("Fabric 元数据缺少 Loader 版本");
        }

        progress.stage("准备 Fabric", "下载 Fabric " + loaderVersion + " 配置", -1);
        String profileUrl = versionsUrl + "/" + encode(loaderVersion) + "/profile/json";
        Map<String, Object> profile = Json.object(Json.parse(downloader.fetchString(profileUrl)));
        String id = Json.string(profile, "id");
        if (id == null || id.isBlank()) throw new IOException("Fabric 配置缺少版本 ID");
        if (!minecraftVersion.equals(Json.string(profile, "inheritsFrom"))) {
            throw new IOException("Fabric 配置的基础版本不匹配");
        }
        Path directory = gameDirectory.resolve("versions").resolve(PathSafety.safeName(id));
        Files.createDirectories(directory);
        Path manifest = directory.resolve(id + ".json");
        writeAtomically(manifest, Json.stringifyPretty(profile) + System.lineSeparator());
        progress.stage("安装 Fabric", "补齐 Loader 运行库", 0.08);
        installer.install(GameVersion.fromJson(profile), gameDirectory, progress);
        LauncherLog.info("Fabric " + loaderVersion + " 已安装: " + id);
        return id;
    }

    public String installFabricApi(String minecraftVersion, Path gameDirectory, ProgressListener progress)
            throws IOException, InterruptedException {
        progress.stage("安装 Fabric API", "查询适配 Minecraft " + minecraftVersion + " 的版本", -1);
        String gameList = encode("[\"" + minecraftVersion + "\"]");
        String loaderList = encode("[\"fabric\"]");
        String url = MODRINTH_API + "/project/fabric-api/version?game_versions=" + gameList
                + "&loaders=" + loaderList;
        List<Object> versions = Json.array(Json.parse(downloader.fetchString(url)));
        if (versions.isEmpty()) {
            throw new IOException("没有找到适用于 Minecraft " + minecraftVersion + " 的 Fabric API");
        }
        Map<String, Object> version = Json.object(versions.get(0));
        for (Object raw : versions) {
            Map<String, Object> candidate = Json.object(raw);
            if ("release".equals(Json.string(candidate, "version_type"))) {
                version = candidate;
                break;
            }
        }
        List<Object> files = Json.array(version, "files");
        if (files.isEmpty()) throw new IOException("Fabric API 版本没有可下载文件");
        Map<String, Object> file = Json.object(files.get(0));
        for (Object raw : files) {
            Map<String, Object> candidate = Json.object(raw);
            if (Json.bool(candidate, "primary", false)) {
                file = candidate;
                break;
            }
        }
        String fileUrl = Json.string(file, "url");
        String filename = Json.string(file, "filename", "fabric-api.jar");
        if (fileUrl == null || fileUrl.isBlank()) throw new IOException("Fabric API 文件缺少下载地址");
        Path mods = gameDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path destination = mods.resolve(PathSafety.safeName(filename));
        Map<String, Object> hashes = Json.object(file, "hashes");
        downloader.ensureFile(fileUrl, destination, Json.string(hashes, "sha1"),
                Json.number(file, "size", -1));
        String number = Json.string(version, "version_number", filename);
        LauncherLog.info("Fabric API 已保存: " + destination);
        progress.stage("安装 Fabric API", number, 0.96);
        return "Fabric API " + number;
    }

    public String installForge(String minecraftVersion, Path gameDirectory, Path javaExecutable,
                               ProgressListener progress)
            throws IOException, InterruptedException {
        progress.stage("准备 Forge", "查询 Forge 官方版本目录", -1);
        String metadata = downloader.fetchString(FORGE_MAVEN + "/net/minecraftforge/forge/maven-metadata.xml");
        String forgeVersion = latestForge(metadata, minecraftVersion);
        if (forgeVersion == null) {
            throw new IOException("Forge 尚未提供 Minecraft " + minecraftVersion + " 的安装器");
        }
        String coordinate = minecraftVersion + "-" + forgeVersion;
        String fileName = "forge-" + coordinate + "-installer.jar";
        String url = FORGE_MAVEN + "/net/minecraftforge/forge/" + encode(coordinate) + "/"
                + encode(fileName);
        Path cache = AppPaths.cache().resolve("forge");
        Files.createDirectories(cache);
        Path installerFile = cache.resolve(fileName);
        String sha1 = null;
        try {
            String sidecar = downloader.fetchString(url + ".sha1").trim();
            if (!sidecar.isBlank()) sha1 = sidecar.split("\\s+")[0];
        } catch (Exception ex) {
            LauncherLog.warn("无法读取 Forge 安装器校验值: " + ex.getMessage());
        }
        progress.stage("下载 Forge", coordinate, 0.04);
        downloader.ensureFile(url, installerFile, sha1, -1);

        Set<String> before = localProfileIds(gameDirectory);
        progress.stage("安装 Forge", "官方安装器正在处理运行库", 0.18);
        runForgeInstaller(javaExecutable, installerFile, gameDirectory);

        String installedId = readInstallerProfileId(installerFile);
        if (installedId == null || !Files.isRegularFile(gameDirectory.resolve("versions")
                .resolve(PathSafety.safeName(installedId)).resolve(installedId + ".json"))) {
            installedId = findNewProfile(gameDirectory, minecraftVersion, before);
        }
        if (installedId == null) {
            throw new IOException("Forge 安装器结束，但没有找到生成的版本配置");
        }
        Path manifest = gameDirectory.resolve("versions").resolve(PathSafety.safeName(installedId))
                .resolve(installedId + ".json");
        Map<String, Object> profile = Json.object(Json.parse(Files.readString(manifest, StandardCharsets.UTF_8)));
        progress.stage("整理 Forge", "校验 Forge 实例文件", 0.9);
        installer.install(GameVersion.fromJson(profile), gameDirectory, progress);
        LauncherLog.info("Forge " + coordinate + " 已安装: " + installedId);
        return installedId;
    }

    public static String latestForge(String metadata, String minecraftVersion) {
        String prefix = minecraftVersion + "-";
        String best = null;
        Matcher matcher = XML_VERSION.matcher(metadata);
        while (matcher.find()) {
            String coordinate = matcher.group(1).trim();
            if (!coordinate.startsWith(prefix)) continue;
            String version = coordinate.substring(prefix.length());
            if (version.isBlank()) continue;
            if (best == null || compareVersions(version, best) > 0) best = version;
        }
        return best;
    }

    private void runForgeInstaller(Path javaExecutable, Path installerFile, Path gameDirectory)
            throws IOException, InterruptedException {
        List<String> command = List.of(javaExecutable.toString(), "-jar", installerFile.toString(),
                "--installClient", gameDirectory.toAbsolutePath().toString());
        LauncherLog.info("运行 Forge 安装器: " + String.join(" ", command));
        Process process = new ProcessBuilder(command)
                .directory(AppPaths.cache().toFile())
                .redirectErrorStream(true)
                .start();
        List<String> tail = Collections.synchronizedList(new ArrayList<>());
        Thread reader = new Thread(() -> {
            try (BufferedReader input = new BufferedReader(new InputStreamReader(
                    process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = input.readLine()) != null) {
                    LauncherLog.info("[Forge] " + line);
                    tail.add(line);
                    if (tail.size() > 30) tail.remove(0);
                }
            } catch (IOException ex) {
                LauncherLog.warn("读取 Forge 安装器输出失败: " + ex.getMessage());
            }
        }, "ufml-forge-installer");
        reader.setDaemon(true);
        reader.start();
        boolean finished = process.waitFor(20, TimeUnit.MINUTES);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Forge 安装器超时");
        }
        reader.join(5000);
        if (process.exitValue() != 0) {
            String detail = tail.isEmpty() ? "退出代码 " + process.exitValue()
                    : tail.get(tail.size() - 1);
            throw new IOException("Forge 安装器失败: " + detail);
        }
    }

    private static String readInstallerProfileId(Path installerFile) {
        try (ZipFile zip = new ZipFile(installerFile.toFile())) {
            var entry = zip.getEntry("version.json");
            if (entry == null) return null;
            try (var input = zip.getInputStream(entry)) {
                Map<String, Object> json = Json.object(Json.parse(new String(
                        input.readAllBytes(), StandardCharsets.UTF_8)));
                return Json.string(json, "id");
            }
        } catch (Exception ex) {
            LauncherLog.warn("无法读取 Forge 安装器版本信息: " + ex.getMessage());
            return null;
        }
    }

    private static String findNewProfile(Path gameDirectory, String minecraftVersion, Set<String> before)
            throws IOException {
        Path versions = gameDirectory.resolve("versions");
        if (!Files.isDirectory(versions)) return null;
        try (var entries = Files.list(versions)) {
            for (Path directory : entries.filter(Files::isDirectory).toList()) {
                String id = directory.getFileName().toString();
                if (before.contains(id)) continue;
                Path manifest = directory.resolve(id + ".json");
                if (!Files.isRegularFile(manifest)) continue;
                try {
                    Map<String, Object> json = Json.object(Json.parse(
                            Files.readString(manifest, StandardCharsets.UTF_8)));
                    if (minecraftVersion.equals(Json.string(json, "inheritsFrom"))) return id;
                } catch (Exception ignored) { }
            }
        }
        return null;
    }

    private static Set<String> localProfileIds(Path gameDirectory) throws IOException {
        Path versions = gameDirectory.resolve("versions");
        Set<String> ids = new HashSet<>();
        if (!Files.isDirectory(versions)) return ids;
        try (var entries = Files.list(versions)) {
            for (Path directory : entries.filter(Files::isDirectory).toList()) {
                ids.add(directory.getFileName().toString());
            }
        }
        return ids;
    }

    private static void writeAtomically(Path destination, String value) throws IOException {
        Path temporary = destination.resolveSibling(destination.getFileName() + ".tmp");
        Files.writeString(temporary, value, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ex) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.toLowerCase(Locale.ROOT).split("[^0-9]+");
        String[] b = right.toLowerCase(Locale.ROOT).split("[^0-9]+");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length && !a[i].isBlank() ? Integer.parseInt(a[i]) : 0;
            int y = i < b.length && !b[i].isBlank() ? Integer.parseInt(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return left.compareTo(right);
    }

    public record LoaderResult(String versionId, String detail) { }
}
