package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.AppPaths;
import io.github.ufml.launcher.util.Hashing;
import io.github.ufml.launcher.util.Json;
import io.github.ufml.launcher.util.LauncherLog;
import io.github.ufml.launcher.util.PathSafety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JavaRuntimeManager {
    private static final Pattern VERSION_PATTERN = Pattern.compile("version \\\"([^\\\"]+)\\\"");
    private final MojangService mojang;
    private final Downloader downloader;
    private volatile List<JavaInfo> cached;

    public JavaRuntimeManager(MojangService mojang, Downloader downloader) {
        this.mojang = mojang;
        this.downloader = downloader;
    }

    public JavaInfo resolve(String configuredPath, int requiredMajor, String component,
                            ProgressListener progress) throws IOException, InterruptedException {
        if (configuredPath != null && !configuredPath.isBlank()) {
            JavaInfo configured = probeConfigured(Path.of(configuredPath.trim().replace("\"", "")));
            if (!configured.found()) {
                throw new IOException("无法运行指定的 Java: " + configuredPath);
            }
            if (configured.major() < requiredMajor) {
                LauncherLog.warn("指定 Java " + configured.major() + " 低于该版本建议的 Java "
                        + requiredMajor + "，可能无法启动");
            }
            return configured;
        }

        List<JavaInfo> discovered = discovered();
        for (JavaInfo info : discovered) {
            if (info.major() == requiredMajor) return info;
        }
        JavaInfo managed = findManaged(component, requiredMajor);
        if (managed != null) return managed;

        progress.stage("准备 Java", "未找到 Java " + requiredMajor + "，开始自动下载", -1);
        try {
            JavaInfo downloaded = installRuntime(component, requiredMajor, progress);
            cached = null;
            return downloaded;
        } catch (Exception downloadFailure) {
            LauncherLog.warn("自动准备 Java 失败: " + downloadFailure.getMessage());
            JavaInfo fallback = discovered.stream()
                    .filter(info -> info.major() >= requiredMajor)
                    .findFirst()
                    .orElse(discovered.isEmpty() ? null : discovered.get(0));
            if (fallback == null) {
                throw new IOException("未找到可用的 Java " + requiredMajor + "，自动下载也失败。"
                        + "请在设置中选择 Java 可执行文件", downloadFailure);
            }
            LauncherLog.warn("回退到 Java " + fallback.version() + "，可能与游戏版本不兼容");
            return fallback;
        }
    }

    /** Resolves Java from local installations only. This method never downloads a runtime. */
    public JavaInfo resolveInstalled(String configuredPath, int requiredMajor, String component)
            throws IOException {
        if (configuredPath != null && !configuredPath.isBlank()) {
            JavaInfo configured = probeConfigured(Path.of(configuredPath.trim().replace("\"", "")));
            if (!configured.found()) {
                throw new IOException("无法运行指定的 Java: " + configuredPath);
            }
            if (configured.major() < requiredMajor) {
                LauncherLog.warn("指定 Java " + configured.major() + " 低于该版本建议的 Java "
                        + requiredMajor + "，可能无法启动");
            }
            return configured;
        }

        List<JavaInfo> discovered = discovered();
        for (JavaInfo info : discovered) {
            if (info.major() == requiredMajor) return info;
        }
        JavaInfo managed = findManaged(component, requiredMajor);
        if (managed != null) return managed;
        JavaInfo fallback = discovered.stream()
                .filter(info -> info.major() >= requiredMajor)
                .findFirst()
                .orElse(null);
        if (fallback != null) {
            LauncherLog.warn("未找到 Java " + requiredMajor + "，改用本地 Java " + fallback.version());
            return fallback;
        }
        throw new IOException("本地未找到适用于该版本的 Java " + requiredMajor
                + "。请先在“下载游戏”菜单安装该版本，或在运行设置中选择 Java。");
    }

    public List<JavaInfo> discovered() {
        List<JavaInfo> existing = cached;
        if (existing != null) return existing;
        synchronized (this) {
            if (cached != null) return cached;
            LinkedHashMap<Path, Path> candidates = new LinkedHashMap<>();
            addJavaHome(candidates, System.getProperty("java.home"));
            addJavaHome(candidates, System.getenv("JAVA_HOME"));
            addFromPath(candidates);
            Path appData = Path.of(System.getenv().getOrDefault("APPDATA", ""));
            if (!appData.toString().isBlank()) {
                addGlob(candidates, appData.resolve(".minecraft").resolve("runtime"), 6);
            }
            addGlob(candidates, AppPaths.runtimes(), 4);
            addGlob(candidates, envPath("ProgramFiles", "Java"), 3);
            addGlob(candidates, envPath("ProgramFiles", "Eclipse Adoptium"), 3);
            addGlob(candidates, envPath("ProgramFiles", "Microsoft"), 3);
            addGlob(candidates, envPath("LOCALAPPDATA", "Programs", "Eclipse Adoptium"), 3);
            addGlob(candidates, envPath("LOCALAPPDATA", "Programs", "Microsoft"), 3);

            List<JavaInfo> result = new ArrayList<>();
            for (Path java : candidates.values()) {
                JavaInfo info = probe(java);
                if (info.found()) result.add(info);
            }
            result.sort((a, b) -> Integer.compare(b.major(), a.major()));
            cached = List.copyOf(result);
            LauncherLog.info("检测到 " + cached.size() + " 个 Java 运行时");
            return cached;
        }
    }

    public void refresh() {
        cached = null;
    }

    private JavaInfo installRuntime(String requestedComponent, int requiredMajor, ProgressListener progress)
            throws IOException, InterruptedException {
        progress.stage("准备 Java", "获取官方运行库清单", -1);
        Map<String, Object> all = mojang.loadJavaRuntimeManifest();
        String platform = platformKey();
        Object rawPlatform = all.get(platform);
        if (rawPlatform == null) {
            throw new IOException("Mojang 未提供 " + platform + " 平台的 Java 运行时");
        }
        Map<String, Object> components = Json.object(rawPlatform);
        RuntimeChoice choice = chooseRuntime(components, requestedComponent, requiredMajor);

        progress.stage("准备 Java", "下载 Java " + choice.version + " 元数据", 0.02);
        Map<String, Object> manifestInfo = Json.object(choice.entry, "manifest");
        String manifestUrl = Json.string(manifestInfo, "url");
        if (manifestUrl == null) throw new IOException("Java 运行时元数据缺少下载地址");
        byte[] manifestBytes = downloader.fetchBytes(manifestUrl);
        String expectedHash = Json.string(manifestInfo, "sha1");
        if (expectedHash != null && !expectedHash.equalsIgnoreCase(Hashing.sha1(manifestBytes))) {
            throw new IOException("Java 运行时元数据校验失败");
        }
        Map<String, Object> manifest = Json.object(Json.parse(new String(manifestBytes, StandardCharsets.UTF_8)));
        Map<String, Object> files = Json.object(manifest, "files");
        Path runtimeDirectory = AppPaths.runtimes().resolve(PathSafety.safeName(choice.component));
        Files.createDirectories(runtimeDirectory);

        List<RuntimeTask> tasks = new ArrayList<>();
        for (Map.Entry<String, Object> fileEntry : files.entrySet()) {
            Map<String, Object> file = Json.object(fileEntry.getValue());
            Path destination = PathSafety.resolveInside(runtimeDirectory, fileEntry.getKey());
            String type = Json.string(file, "type", "file");
            if ("directory".equals(type)) {
                Files.createDirectories(destination);
                continue;
            }
            Map<String, Object> downloads = Json.object(file, "downloads");
            Map<String, Object> raw = Json.object(downloads, "raw");
            String url = Json.string(raw, "url");
            if (url == null) {
                throw new IOException("Java 运行时文件缺少原始下载地址: " + fileEntry.getKey());
            }
            tasks.add(new RuntimeTask(url, destination, Json.string(raw, "sha1"),
                    Json.number(raw, "size", -1), Json.bool(file, "executable", false)));
        }

        downloadRuntimeFiles(tasks, choice.component, progress);
        for (RuntimeTask task : tasks) {
            if (task.executable) task.destination.toFile().setExecutable(true, false);
        }
        Path java = normalizeJava(runtimeDirectory);
        if (java == null) throw new IOException("Java 运行时下载完成但未找到 java 可执行文件");
        JavaInfo info = probe(java);
        if (!info.found() || info.major() != requiredMajor) {
            throw new IOException("下载的 Java 运行时不可用");
        }
        Files.writeString(runtimeDirectory.resolve(".ufml-runtime"),
                choice.component + System.lineSeparator() + choice.version + System.lineSeparator(),
                StandardCharsets.UTF_8);
        LauncherLog.info("Java " + info.version() + " 已准备: " + runtimeDirectory);
        return info;
    }

    private void downloadRuntimeFiles(List<RuntimeTask> tasks, String component, ProgressListener progress)
            throws IOException, InterruptedException {
        if (tasks.isEmpty()) return;
        ExecutorService pool = Executors.newFixedThreadPool(8, runnable -> {
            Thread thread = new Thread(runnable, "ufml-java-download");
            thread.setDaemon(true);
            return thread;
        });
        try {
            CompletionService<Boolean> completion = new ExecutorCompletionService<>(pool);
            List<Future<Boolean>> futures = new ArrayList<>(tasks.size());
            for (RuntimeTask task : tasks) {
                futures.add(completion.submit(() -> downloader.ensureFile(
                        task.url, task.destination, task.sha1, task.size)));
            }
            long lastUpdate = 0;
            for (int i = 0; i < tasks.size(); i++) {
                try {
                    completion.take().get();
                } catch (ExecutionException ex) {
                    for (Future<Boolean> future : futures) future.cancel(true);
                    Throwable cause = ex.getCause();
                    if (cause instanceof IOException io) throw io;
                    if (cause instanceof InterruptedException interrupted) throw interrupted;
                    throw new IOException("Java 运行时文件下载失败", cause);
                }
                int done = i + 1;
                long now = System.nanoTime();
                if (now - lastUpdate > TimeUnit.MILLISECONDS.toNanos(120) || done == tasks.size()) {
                    progress.stage("下载 Java", done + " / " + tasks.size(),
                            0.05 + 0.9 * done / tasks.size());
                    lastUpdate = now;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        progress.stage("准备 Java", "校验 Java 运行时", 0.98);
    }

    private RuntimeChoice chooseRuntime(Map<String, Object> components, String requested, int requiredMajor)
            throws IOException {
        if (requested != null && !requested.isBlank() && components.get(requested) != null) {
            List<Object> entries = Json.array(components.get(requested));
            if (!entries.isEmpty()) {
                Map<String, Object> entry = Json.object(entries.get(0));
                return new RuntimeChoice(requested, versionOf(entry), entry);
            }
        }
        RuntimeChoice best = null;
        int bestVersion = -1;
        for (Map.Entry<String, Object> component : components.entrySet()) {
            List<Object> entries = Json.array(component.getValue());
            for (Object rawEntry : entries) {
                Map<String, Object> entry = Json.object(rawEntry);
                String version = versionOf(entry);
                int major = majorOf(version);
                if (major == requiredMajor && compareVersions(version, best == null ? "" : best.version) > 0) {
                    best = new RuntimeChoice(component.getKey(), version, entry);
                    bestVersion = major;
                }
            }
        }
        if (best == null || bestVersion != requiredMajor) {
            throw new IOException("Mojang 未提供适用于 Java " + requiredMajor + " 的运行时");
        }
        return best;
    }

    private static String versionOf(Map<String, Object> entry) {
        return Json.string(Json.object(entry, "version"), "name", "0");
    }

    private static int compareVersions(String left, String right) {
        String[] a = left.split("\\.");
        String[] b = right.split("\\.");
        int length = Math.max(a.length, b.length);
        for (int i = 0; i < length; i++) {
            int x = i < a.length ? parseInt(a[i]) : 0;
            int y = i < b.length ? parseInt(b[i]) : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int parseInt(String value) {
        try { return Integer.parseInt(value.replaceAll("[^0-9].*", "")); }
        catch (NumberFormatException ex) { return 0; }
    }

    private static String platformKey() {
        String os = RuleEvaluator.OS_NAME;
        String arch = System.getProperty("os.arch", "").toLowerCase();
        boolean arm64 = arch.contains("aarch64") || arch.contains("arm64");
        boolean x86 = arch.equals("x86") || arch.contains("i386") || arch.contains("i686");
        return switch (os) {
            case "windows" -> arm64 ? "windows-arm64" : x86 ? "windows-x86" : "windows-x64";
            case "osx" -> arm64 ? "mac-os-arm64" : "mac-os";
            default -> x86 ? "linux-i386" : "linux";
        };
    }

    private record RuntimeChoice(String component, String version, Map<String, Object> entry) { }
    private record RuntimeTask(String url, Path destination, String sha1, long size, boolean executable) { }

    private JavaInfo probeConfigured(Path path) {
        Path java = normalizeJava(path);
        if (java == null) return JavaInfo.missing();
        return probe(java);
    }

    private JavaInfo findManaged(String component, int requiredMajor) {
        if (component == null || component.isBlank()) return null;
        Path runtime = AppPaths.runtimes().resolve(PathSafety.safeName(component));
        Path marker = runtime.resolve(".ufml-runtime");
        if (!Files.isRegularFile(marker)) return null;
        Path java = normalizeJava(runtime);
        if (java == null) return null;
        JavaInfo info = probe(java);
        return info.found() && info.major() == requiredMajor ? info : null;
    }

    private static Path normalizeJava(Path path) {
        if (path == null) return null;
        Path normalized = path.toAbsolutePath().normalize();
        if (Files.isDirectory(normalized)) {
            Path exe = normalized.resolve("bin").resolve(windows() ? "java.exe" : "java");
            return Files.isRegularFile(exe) ? exe : null;
        }
        if (!Files.isRegularFile(normalized)) return null;
        String name = normalized.getFileName().toString().toLowerCase();
        if (windows() && name.equals("javaw.exe")) {
            Path java = normalized.resolveSibling("java.exe");
            if (Files.isRegularFile(java)) return java;
        }
        return normalized;
    }

    private static JavaInfo probe(Path java) {
        try {
            Process process = new ProcessBuilder(java.toString(), "-version")
                    .redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return JavaInfo.missing();
            }
            if (process.exitValue() != 0) return JavaInfo.missing();
            Matcher matcher = VERSION_PATTERN.matcher(output);
            if (!matcher.find()) return JavaInfo.missing();
            String version = matcher.group(1);
            int major = majorOf(version);
            Path executable = java;
            if (windows()) {
                Path javaw = java.resolveSibling("javaw.exe");
                if (Files.isRegularFile(javaw)) executable = javaw;
            }
            String label = "Java " + major + "  ·  " + shorten(java.getParent().getParent().toString());
            return new JavaInfo(executable, java, major, version, label, true);
        } catch (Exception ex) {
            return JavaInfo.missing();
        }
    }

    private static int majorOf(String version) {
        String cleaned = version.startsWith("1.") ? version.substring(2) : version;
        int dot = cleaned.indexOf('.');
        String major = dot >= 0 ? cleaned.substring(0, dot) : cleaned;
        try { return Integer.parseInt(major); } catch (NumberFormatException ex) { return 0; }
    }

    private static String shorten(String path) {
        String home = System.getProperty("user.home", "");
        if (!home.isBlank() && path.startsWith(home)) return "~" + path.substring(home.length());
        return path.length() > 58 ? "..." + path.substring(path.length() - 55) : path;
    }

    private static boolean windows() {
        return Marshal.windows();
    }

    private static Path envPath(String variable, String... children) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) return null;
        Path path = Path.of(value);
        for (String child : children) path = path.resolve(child);
        return path;
    }

    private static void addJavaHome(LinkedHashMap<Path, Path> candidates, String home) {
        if (home == null || home.isBlank()) return;
        Path java = normalizeJava(Path.of(home));
        if (java != null) candidates.putIfAbsent(java.toAbsolutePath().normalize(), java);
    }

    private static void addFromPath(LinkedHashMap<Path, Path> candidates) {
        try {
            Process process = new ProcessBuilder(windows() ? "where" : "which",
                    windows() ? "java.exe" : "java").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor(5, TimeUnit.SECONDS);
            for (String line : output.split("\\R")) {
                if (line.isBlank()) continue;
                Path java = normalizeJava(Path.of(line.trim()));
                if (java != null) candidates.putIfAbsent(java.toAbsolutePath().normalize(), java);
            }
        } catch (Exception ignored) { }
    }

    private static void addGlob(LinkedHashMap<Path, Path> candidates, Path root, int depth) {
        if (root == null || !Files.isDirectory(root)) return;
        try (var paths = Files.walk(root, depth)) {
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().equals(windows() ? "java.exe" : "java"))
                    .limit(80)
                    .forEach(path -> candidates.putIfAbsent(path.toAbsolutePath().normalize(), path));
        } catch (IOException ignored) { }
    }

    private static final class Marshal {
        private static boolean windows() {
            return System.getProperty("os.name", "").toLowerCase().contains("win");
        }
    }

    public record JavaInfo(Path executable, Path probeExecutable, int major, String version,
                           String label, boolean usable) {
        public static JavaInfo missing() { return new JavaInfo(null, null, 0, "", "", false); }
        public boolean found() { return usable && major > 0 && executable != null; }
    }
}
