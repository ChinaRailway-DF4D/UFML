package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.AppPaths;
import io.github.ufml.launcher.util.Json;
import io.github.ufml.launcher.util.LauncherLog;
import io.github.ufml.launcher.util.PathSafety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class MojangService {
    public static final String VERSION_MANIFEST =
            "https://piston-meta.mojang.com" + "/mc/game/" + "version_manifest_v2.json";
    public static final String JAVA_RUNTIME_MANIFEST =
            "https://launchermeta.mojang.com" + "/v1/products/java-runtime/"
                    + "2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private final Downloader downloader;

    public MojangService(Downloader downloader) {
        this.downloader = downloader;
    }

    public VersionManifest loadVersionManifest() throws IOException, InterruptedException {
        Path cache = AppPaths.cache().resolve("version_manifest_v2.json");
        try {
            byte[] bytes = downloader.fetchBytes(VERSION_MANIFEST);
            Map<String, Object> json = Json.object(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
            Files.createDirectories(cache.getParent());
            Files.write(cache, bytes);
            return VersionManifest.fromJson(json);
        } catch (Exception onlineFailure) {
            LauncherLog.warn("获取在线版本清单失败，尝试本地缓存: " + onlineFailure.getMessage());
            if (Files.isRegularFile(cache)) {
                try {
                    Map<String, Object> json = Json.object(Json.parse(Files.readString(cache, StandardCharsets.UTF_8)));
                    LauncherLog.info("已使用缓存版本清单");
                    return VersionManifest.fromJson(json);
                } catch (Exception cacheFailure) {
                    throw new IOException("在线清单与本地缓存均不可用", cacheFailure);
                }
            }
            throw new IOException("无法获取版本清单，请检查网络连接", onlineFailure);
        }
    }

    /** Reads the cached manifest without making any network request. */
    public VersionManifest loadCachedVersionManifest() throws IOException {
        Path cache = AppPaths.cache().resolve("version_manifest_v2.json");
        if (!Files.isRegularFile(cache)) return null;
        Map<String, Object> json = Json.object(Json.parse(Files.readString(cache, StandardCharsets.UTF_8)));
        return VersionManifest.fromJson(json);
    }

    /** Indexes installed manifests under versions/ without contacting any server. */
    public List<VersionSummary> loadLocalVersions(Path gameDirectory) throws IOException {
        Path versionsDirectory = gameDirectory.resolve("versions");
        if (!Files.isDirectory(versionsDirectory)) return List.of();
        List<VersionSummary> versions = new ArrayList<>();
        try (Stream<Path> entries = Files.list(versionsDirectory)) {
            for (Path directory : entries.filter(Files::isDirectory).toList()) {
                Path manifest = findLocalManifest(directory);
                if (manifest == null) continue;
                try {
                    Map<String, Object> json = Json.object(Json.parse(Files.readString(manifest, StandardCharsets.UTF_8)));
                    String id = directory.getFileName().toString();
                    versions.add(VersionSummary.local(id,
                            Json.string(json, "type", "release"),
                            Json.string(json, "releaseTime", ""), manifest));
                } catch (Exception ex) {
                    LauncherLog.warn("忽略无法读取的本地版本 " + manifest + ": " + ex.getMessage());
                }
            }
        }
        versions.sort(Comparator.comparing(VersionSummary::releaseTime).reversed()
                .thenComparing(VersionSummary::id));
        return List.copyOf(versions);
    }

    private static Path findLocalManifest(Path directory) {
        Path conventional = directory.resolve(directory.getFileName().toString() + ".json");
        return Files.isRegularFile(conventional) ? conventional : null;
    }

    public GameVersion loadVersion(VersionSummary summary, Path gameDirectory)
            throws IOException, InterruptedException {
        GameVersion local = loadLocalVersion(summary, gameDirectory);
        if (local != null) return local;
        if (summary.url() == null || summary.url().isBlank()) {
            throw new IOException("版本 " + summary.id() + " 尚未安装且没有下载地址");
        }
        byte[] bytes = downloader.fetchBytes(summary.url());
        String text = new String(bytes, StandardCharsets.UTF_8);
        Map<String, Object> json = Json.object(Json.parse(text));
        Path cache = AppPaths.cache().resolve("versions")
                .resolve(PathSafety.safeName(summary.id()) + ".json");
        Files.createDirectories(cache.getParent());
        Path temporary = cache.resolveSibling(cache.getFileName() + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            Files.move(temporary, cache, StandardCopyOption.REPLACE_EXISTING);
        }
        return GameVersion.fromJson(json);
    }

    /** Reads installed or cached metadata only. Returns null when no local copy exists. */
    public GameVersion loadLocalVersion(VersionSummary summary, Path gameDirectory) throws IOException {
        if (summary.manifestPath() != null && Files.isRegularFile(summary.manifestPath())) {
            return VersionInheritance.resolve(readVersion(summary.manifestPath()), gameDirectory);
        }
        Path safeId = Path.of(PathSafety.safeName(summary.id()));
        Path installed = gameDirectory.resolve("versions").resolve(safeId).resolve(summary.id() + ".json");
        if (Files.isRegularFile(installed)) {
            return VersionInheritance.resolve(readVersion(installed), gameDirectory);
        }
        Path cache = AppPaths.cache().resolve("versions").resolve(safeId + ".json");
        if (Files.isRegularFile(cache)) {
            return VersionInheritance.resolve(readVersion(cache), gameDirectory);
        }
        return null;
    }

    public Map<String, Object> loadJavaRuntimeManifest() throws IOException, InterruptedException {
        Path cache = AppPaths.cache().resolve("java-runtime-all.json");
        byte[] bytes;
        try {
            bytes = downloader.fetchBytes(JAVA_RUNTIME_MANIFEST);
            Files.createDirectories(cache.getParent());
            Files.write(cache, bytes);
        } catch (Exception onlineFailure) {
            if (!Files.isRegularFile(cache)) throw new IOException("无法获取 Java 运行时清单", onlineFailure);
            bytes = Files.readAllBytes(cache);
        }
        return Json.object(Json.parse(new String(bytes, StandardCharsets.UTF_8)));
    }

    private static GameVersion readVersion(Path file) throws IOException {
        return GameVersion.fromJson(Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8))));
    }
}
