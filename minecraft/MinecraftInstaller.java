package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.Json;
import io.github.ufml.launcher.util.LauncherLog;
import io.github.ufml.launcher.util.PathSafety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class MinecraftInstaller {
    private final Downloader downloader;
    private final String assetBaseUrl;
    private final ExecutorService pool;

    public MinecraftInstaller(Downloader downloader) {
        this(downloader, "https://resources." + "download.minecraft.net/");
    }

    public MinecraftInstaller(Downloader downloader, String assetBaseUrl) {
        this.downloader = downloader;
        this.assetBaseUrl = assetBaseUrl.endsWith("/") ? assetBaseUrl : assetBaseUrl + "/";
        this.pool = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "ufml-download");
            thread.setDaemon(true);
            return thread;
        });
    }

    public InstalledVersion install(GameVersion version, Path gameDirectory, ProgressListener progress)
            throws IOException, InterruptedException {
        return install(version, gameDirectory, progress, true);
    }

    /** Prepares an already installed version and refuses to download missing game files. */
    public InstalledVersion prepareInstalled(GameVersion version, Path gameDirectory, ProgressListener progress)
            throws IOException, InterruptedException {
        return install(version, gameDirectory, progress, false);
    }

    private InstalledVersion install(GameVersion version, Path gameDirectory, ProgressListener progress,
                                     boolean allowDownloads)
            throws IOException, InterruptedException {
        version = VersionInheritance.resolve(version, gameDirectory);
        String versionId = version.id();
        Path versionDirectory = gameDirectory.resolve("versions")
                .resolve(PathSafety.safeName(versionId)).toAbsolutePath().normalize();
        Path librariesDirectory = gameDirectory.resolve("libraries").toAbsolutePath().normalize();
        Path assetsDirectory = gameDirectory.resolve("assets").toAbsolutePath().normalize();
        Path nativesDirectory = versionDirectory.resolve("natives").toAbsolutePath().normalize();
        String clientVersionId = version.clientVersionId();
        Path clientJar = gameDirectory.resolve("versions")
                .resolve(PathSafety.safeName(clientVersionId))
                .resolve(clientVersionId + ".jar").toAbsolutePath().normalize();
        Files.createDirectories(versionDirectory);
        Files.createDirectories(librariesDirectory);
        Files.createDirectories(assetsDirectory.resolve("indexes"));
        Files.createDirectories(assetsDirectory.resolve("objects"));
        Files.writeString(versionDirectory.resolve(versionId + ".json"),
                Json.stringifyPretty(version.storedRaw()) + System.lineSeparator(), StandardCharsets.UTF_8);

        progress.stage("检查文件", "准备客户端与运行库", 0.01);
        List<DownloadTask> coreTasks = new ArrayList<>();
        Set<Path> classpath = new LinkedHashSet<>();
        List<NativeArchive> nativeArchives = new ArrayList<>();

        Map<String, Object> client = version.clientDownload();
        if (client.isEmpty() && !Files.isRegularFile(clientJar)) {
            throw new IOException("版本缺少客户端下载信息");
        }
        if (!client.isEmpty()) coreTasks.add(task("客户端", client, clientJar));
        classpath.add(clientJar);

        Map<String, Object> logging = version.loggingClient();
        Path loggingConfig = null;
        if (!logging.isEmpty()) {
            Map<String, Object> loggingFile = Json.object(logging, "file");
            String logId = Json.string(loggingFile, "id", "client-log.xml");
            loggingConfig = assetsDirectory.resolve("log_configs").resolve(PathSafety.safeName(logId));
            if (Json.string(loggingFile, "url") != null) {
                coreTasks.add(task("日志配置", loggingFile, loggingConfig));
            }
        }

        for (Object rawLibrary : version.libraries()) {
            Map<String, Object> library = Json.object(rawLibrary);
            if (!RuleEvaluator.allows(library, defaultFeatures())) continue;
            Map<String, Object> downloads = Json.object(library, "downloads");
            Map<String, Object> artifact = Json.object(downloads, "artifact");
            if (Json.string(artifact, "path") != null) {
                Path destination = PathSafety.resolveInside(librariesDirectory, Json.string(artifact, "path"));
                classpath.add(destination);
                String url = Json.string(artifact, "url");
                String sha1 = Json.string(artifact, "sha1");
                long size = Json.number(artifact, "size", -1);
                if (url == null || url.isBlank()) {
                    MavenArtifact maven = mavenArtifact(library, null);
                    if (maven != null) {
                        url = maven.url;
                        if (sha1 == null) sha1 = maven.sha1;
                        if (size <= 0) size = maven.size;
                    }
                }
                if (url != null && !url.isBlank()) {
                    coreTasks.add(new DownloadTask("运行库", url, destination, sha1, size));
                } else if (!Files.isRegularFile(destination)) {
                    throw new IOException("运行库缺少下载地址: " + destination.getFileName());
                }
            } else {
                MavenArtifact maven = mavenArtifact(library, null);
                if (maven != null) {
                    Path destination = PathSafety.resolveInside(librariesDirectory, maven.path);
                    classpath.add(destination);
                    if (maven.url != null && !maven.url.isBlank()) {
                        coreTasks.add(new DownloadTask("运行库", maven.url, destination,
                                maven.sha1, maven.size));
                    } else if (!Files.isRegularFile(destination)) {
                        throw new IOException("运行库缺少下载地址: " + maven.path);
                    }
                }
            }

            Object rawNatives = library.get("natives");
            if (rawNatives != null) {
                Map<String, Object> natives = Json.object(rawNatives);
                String classifier = Json.string(natives, RuleEvaluator.OS_NAME);
                if (classifier != null) {
                    classifier = classifier.replace("${arch}", is64Bit() ? "64" : "32");
                    Map<String, Object> classifiers = Json.object(downloads, "classifiers");
                    Object rawNativeArtifact = classifiers.get(classifier);
                    if (rawNativeArtifact != null) {
                        Map<String, Object> nativeArtifact = Json.object(rawNativeArtifact);
                        String path = Json.string(nativeArtifact, "path");
                        if (path != null) {
                            Path destination = PathSafety.resolveInside(librariesDirectory, path);
                            List<String> excludes = new ArrayList<>();
                            Object extract = library.get("extract");
                            if (extract != null) {
                                for (Object exclude : Json.array(Json.object(extract), "exclude")) {
                                    excludes.add(String.valueOf(exclude));
                                }
                            }
                            nativeArchives.add(new NativeArchive(destination, List.copyOf(excludes)));
                            String url = Json.string(nativeArtifact, "url");
                            if (url != null && !url.isBlank()) {
                                coreTasks.add(task("原生库", nativeArtifact, destination));
                            } else if (!Files.isRegularFile(destination)) {
                                throw new IOException("原生库缺少下载地址: " + path);
                            }
                        }
                    } else {
                        MavenArtifact maven = mavenArtifact(library, classifier);
                        if (maven != null) {
                            Path destination = PathSafety.resolveInside(librariesDirectory, maven.path);
                            List<String> excludes = new ArrayList<>();
                            Object extract = library.get("extract");
                            if (extract != null) {
                                for (Object exclude : Json.array(Json.object(extract), "exclude")) {
                                    excludes.add(String.valueOf(exclude));
                                }
                            }
                            nativeArchives.add(new NativeArchive(destination, List.copyOf(excludes)));
                            if (maven.url != null && !maven.url.isBlank()) {
                                coreTasks.add(new DownloadTask("原生库", maven.url, destination,
                                        maven.sha1, maven.size));
                            } else if (!Files.isRegularFile(destination)) {
                                throw new IOException("原生库缺少下载地址: " + maven.path);
                            }
                        }
                    }
                }
            }
        }

        Map<String, Object> assetIndex = version.assetIndex();
        String assetIndexName = Json.string(assetIndex, "id", version.assets());
        Path assetIndexFile = assetsDirectory.resolve("indexes")
                .resolve(PathSafety.safeName(assetIndexName) + ".json");
        if (Json.string(assetIndex, "url") != null && !Json.string(assetIndex, "url").isBlank()) {
            coreTasks.add(task("资源索引", assetIndex, assetIndexFile));
        } else if (!Files.isRegularFile(assetIndexFile)) {
            throw new IOException("版本缺少资源索引下载地址: " + assetIndexName);
        }

        downloadAll(coreTasks, "下载核心文件", 0.02, 0.20, progress, allowDownloads);
        progress.stage("检查资源", "读取资源索引", 0.21);
        List<DownloadTask> assetTasks = assetDownloads(assetIndexFile, assetsDirectory);
        downloadAll(assetTasks, "下载游戏资源", 0.22, 0.94, progress, allowDownloads);

        progress.stage("整理资源", "准备旧版资源目录", 0.95);
        copyVirtualAssets(assetIndexFile, assetsDirectory, assetIndexName);
        progress.stage("解压原生库", "准备本地库", 0.97);
        extractNatives(versionDirectory, nativesDirectory, nativeArchives);
        Files.createDirectories(nativesDirectory.resolve("java"));
        Files.createDirectories(nativesDirectory.resolve("jna"));
        Files.createDirectories(nativesDirectory.resolve("lwjgl"));
        Files.createDirectories(nativesDirectory.resolve("netty"));

        progress.stage("准备完成", "文件校验通过", 1.0);
        LauncherLog.info("版本 " + versionId + " 已准备，类路径 " + classpath.size() + " 项");
        return new InstalledVersion(versionId, versionDirectory, clientJar, nativesDirectory,
                assetsDirectory, assetIndexName, loggingConfig, List.copyOf(classpath));
    }

    public void shutdown() {
        pool.shutdownNow();
    }

    private void downloadAll(List<DownloadTask> tasks, String stage, double start, double end,
                             ProgressListener progress, boolean allowDownloads)
            throws IOException, InterruptedException {
        if (tasks.isEmpty()) return;
        if (!allowDownloads) {
            for (DownloadTask task : tasks) {
                boolean present = Files.isRegularFile(task.destination)
                        && (task.size <= 0 || Files.size(task.destination) == task.size);
                if (!present) {
                    throw new IOException("本地版本文件不完整，缺少" + task.label
                            + "。请先在“下载游戏”菜单重新安装该版本。");
                }
            }
            progress.stage(stage, "本地文件已齐全", start);
            return;
        }
        CompletionService<Boolean> completion = new ExecutorCompletionService<>(pool);
        List<Future<Boolean>> futures = new ArrayList<>(tasks.size());
        for (DownloadTask task : tasks) {
            futures.add(completion.submit(() -> downloader.ensureFile(
                    task.url, task.destination, task.sha1, task.size)));
        }
        AtomicInteger completed = new AtomicInteger();
        long lastUiUpdate = 0;
        try {
            for (int i = 0; i < tasks.size(); i++) {
                try {
                    completion.take().get();
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    if (cause instanceof IOException io) throw io;
                    if (cause instanceof InterruptedException interrupted) throw interrupted;
                    throw new IOException("文件下载失败", cause);
                }
                int done = completed.incrementAndGet();
                long now = System.nanoTime();
                if (now - lastUiUpdate > TimeUnit.MILLISECONDS.toNanos(120) || done == tasks.size()) {
                    double fraction = start + (end - start) * done / tasks.size();
                    progress.stage(stage, done + " / " + tasks.size(), fraction);
                    lastUiUpdate = now;
                }
            }
        } catch (InterruptedException | IOException | RuntimeException ex) {
            for (Future<Boolean> future : futures) future.cancel(true);
            throw ex;
        }
    }

    private List<DownloadTask> assetDownloads(Path assetIndexFile, Path assetsDirectory) throws IOException {
        if (!Files.isRegularFile(assetIndexFile)) return List.of();
        Map<String, Object> index = Json.object(Json.parse(Files.readString(assetIndexFile, StandardCharsets.UTF_8)));
        Map<String, Object> objects = Json.object(index, "objects");
        List<DownloadTask> tasks = new ArrayList<>(objects.size());
        Set<Path> seen = new HashSet<>();
        for (Object rawObject : objects.values()) {
            Map<String, Object> object = Json.object(rawObject);
            String hash = Json.string(object, "hash");
            if (hash == null || hash.length() < 2) continue;
            long size = Json.number(object, "size", 0);
            Path destination = assetsDirectory.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
            if (seen.add(destination)) {
                String url = assetBaseUrl + hash.substring(0, 2) + "/" + hash;
                tasks.add(new DownloadTask("资源文件", url, destination, hash, size));
            }
        }
        return tasks;
    }

    private void copyVirtualAssets(Path assetIndexFile, Path assetsDirectory, String indexName) throws IOException {
        if (!Files.isRegularFile(assetIndexFile)) return;
        Map<String, Object> index = Json.object(Json.parse(Files.readString(assetIndexFile, StandardCharsets.UTF_8)));
        boolean virtual = Json.bool(index, "virtual", false);
        boolean mapToResources = Json.bool(index, "map_to_resources", false);
        if (!virtual && !mapToResources) return;
        Path targetRoot = mapToResources
                ? assetsDirectory.getParent().resolve("resources")
                : assetsDirectory.resolve("virtual").resolve(PathSafety.safeName(indexName));
        Map<String, Object> objects = Json.object(index, "objects");
        for (Map.Entry<String, Object> entry : objects.entrySet()) {
            Map<String, Object> object = Json.object(entry.getValue());
            String hash = Json.string(object, "hash");
            if (hash == null || hash.length() < 2) continue;
            Path source = assetsDirectory.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
            if (!Files.isRegularFile(source)) continue;
            Path target = PathSafety.resolveInside(targetRoot, entry.getKey());
            Files.createDirectories(target.getParent());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void extractNatives(Path versionDirectory, Path nativesDirectory, List<NativeArchive> archives)
            throws IOException {
        try {
            PathSafety.deleteDirectory(nativesDirectory, versionDirectory);
        } catch (IOException ex) {
            LauncherLog.warn("无法清理旧原生库目录: " + ex.getMessage());
        }
        Files.createDirectories(nativesDirectory);
        Set<Path> extracted = new HashSet<>();
        for (NativeArchive archive : archives) {
            if (!Files.isRegularFile(archive.path) || !extracted.add(archive.path)) continue;
            PathSafety.extractZip(archive.path, nativesDirectory, archive.excludes, false);
        }
    }

    private static Map<String, Boolean> defaultFeatures() {
        return Map.of(
                "is_demo_user", false,
                "has_custom_resolution", false,
                "has_quick_plays_support", false,
                "is_quick_play_singleplayer", false,
                "is_quick_play_multiplayer", false,
                "is_quick_play_realms", false
        );
    }

    private static boolean is64Bit() {
        String arch = System.getProperty("os.arch", "");
        return arch.contains("64") || arch.equals("aarch64");
    }

    private static DownloadTask task(String label, Map<String, Object> artifact, Path destination) {
        String url = Json.string(artifact, "url");
        if (url == null) throw new IllegalArgumentException(label + " 缺少下载地址");
        return new DownloadTask(label, url, destination,
                Json.string(artifact, "sha1"), Json.number(artifact, "size", -1));
    }

    private static MavenArtifact mavenArtifact(Map<String, Object> library, String classifier) {
        String name = Json.string(library, "name");
        if (name == null || name.isBlank()) return null;
        String extension = "jar";
        int at = name.indexOf('@');
        if (at >= 0) {
            extension = name.substring(at + 1);
            name = name.substring(0, at);
        }
        String[] parts = name.split(":", -1);
        if (parts.length < 3 || parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
            return null;
        }
        String actualClassifier = classifier;
        if ((actualClassifier == null || actualClassifier.isBlank()) && parts.length >= 4) {
            actualClassifier = parts[3];
        }
        String suffix = actualClassifier == null || actualClassifier.isBlank() ? "" : "-" + actualClassifier;
        String path = parts[0].replace('.', '/') + "/" + parts[1] + "/" + parts[2] + "/"
                + parts[1] + "-" + parts[2] + suffix + "." + extension;
        String base = Json.string(library, "url", "https://libraries.minecraft.net/");
        if (base == null || base.isBlank()) base = "https://libraries.minecraft.net/";
        if (!base.endsWith("/")) base += "/";
        String sha1 = Json.string(library, "sha1");
        if (sha1 == null || sha1.isBlank()) {
            List<Object> checksums = Json.array(library, "checksums");
            if (!checksums.isEmpty()) sha1 = String.valueOf(checksums.get(0));
        }
        return new MavenArtifact(path, base + path, sha1,
                Json.number(library, "size", -1));
    }

    private record DownloadTask(String label, String url, Path destination, String sha1, long size) { }
    private record NativeArchive(Path path, List<String> excludes) { }
    private record MavenArtifact(String path, String url, String sha1, long size) { }
}
