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
        String command = ProcessHandle.current().info().command().orElse("");
        if (isPackagedExecutable(command)) {
            return executableDirectory(command);
        }
        Path developmentDirectory = classLocationDirectory();
        for (Path candidate = developmentDirectory; candidate != null; candidate = candidate.getParent()) {
            if (hasPortableData(candidate)) return candidate;
            Path nested = candidate.resolve("UFML");
            if (hasPortableData(nested)) return nested;
        }
        return developmentDirectory;
    }

    private static boolean hasPortableData(Path directory) {
        return directory != null
                && (Files.isDirectory(directory.resolve("Config"))
                || Files.isDirectory(directory.resolve("Minecraft")));
    }

    private static boolean isPackagedExecutable(String command) {
        if (command == null || command.isBlank()) return false;
        String executable = Path.of(command).getFileName().toString().toLowerCase();
        return !executable.startsWith("java");
    }

    private static Path executableDirectory(String command) {
        Path path = Path.of(command).toAbsolutePath().normalize();
        return Files.isDirectory(path) ? path : path.getParent();
    }

    private static Path classLocationDirectory() {
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
`````

### package.ps1

`````powershell
$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$buildDir = Join-Path $root "build"

& (Join-Path $root "build.ps1")
if ($LASTEXITCODE -ne 0) { throw "主项目构建失败。" }

$jpackageCommand = Get-Command jpackage -ErrorAction SilentlyContinue
$jpackage = if ($jpackageCommand) { $jpackageCommand.Source } else { $null }
if (-not $jpackage -and $env:JAVA_HOME) {
    $candidate = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    if (Test-Path -LiteralPath $candidate) { $jpackage = $candidate }
}
if (-not $jpackage) {
    $candidates = Get-ChildItem "C:\Program Files\Java" -Filter jpackage.exe -Recurse -Depth 3 -ErrorAction SilentlyContinue
    if ($candidates) { $jpackage = ($candidates | Select-Object -First 1).FullName }
}
if (-not $jpackage) { throw "未找到 jpackage，请使用完整 JDK 17 或更高版本。" }

$inputDir = Join-Path $buildDir "jpackage-input"
$distDir = Join-Path $buildDir "dist"
$appDir = Join-Path $distDir "UFML-Launcher"
$iconDir = Join-Path $buildDir "icon"
$iconClasses = Join-Path $buildDir "icon-classes"
$icoPath = Join-Path $buildDir "ufml.ico"
$zipPath = Join-Path $buildDir "UFML-Launcher-Windows-x64.zip"
$packageDir = Join-Path $buildDir "package"
$installRoot = Join-Path $root "UFML"

foreach ($path in @($inputDir, $distDir, $iconDir, $iconClasses, $packageDir)) {
    $resolved = [System.IO.Path]::GetFullPath($path)
    if (-not $resolved.StartsWith([System.IO.Path]::GetFullPath($buildDir) + [System.IO.Path]::DirectorySeparatorChar,
            [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "构建路径不在 build 目录内: $resolved"
    }
    if (Test-Path -LiteralPath $resolved) { Remove-Item -LiteralPath $resolved -Recurse -Force }
}
New-Item -ItemType Directory -Path $inputDir, $iconDir, $iconClasses -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $buildDir "UFML-Launcher.jar") -Destination $inputDir

Write-Host "生成应用图标..." -ForegroundColor Cyan
& javac --release 17 -encoding UTF-8 -cp (Join-Path $buildDir "classes") -d $iconClasses (Join-Path $root "tools\IconGenerator.java")
if ($LASTEXITCODE -ne 0) { throw "图标生成器编译失败。" }
& java "-Djava.awt.headless=true" -cp "$(Join-Path $buildDir 'classes');$iconClasses" IconGenerator $iconDir
if ($LASTEXITCODE -ne 0) { throw "图标生成失败。" }

$sizes = @(16, 32, 48, 64, 128, 256)
$images = [System.Collections.Generic.List[byte[]]]::new()
foreach ($size in $sizes) {
    $images.Add([System.IO.File]::ReadAllBytes((Join-Path $iconDir "icon-$size.png")))
}
$stream = [System.IO.MemoryStream]::new()
$writer = [System.IO.BinaryWriter]::new($stream)
try {
    $writer.Write([UInt16]0)
    $writer.Write([UInt16]1)
    $writer.Write([UInt16]$sizes.Count)
    $offset = 6 + 16 * $sizes.Count
    for ($index = 0; $index -lt $sizes.Count; $index++) {
        $size = $sizes[$index]
        $bytes = $images[$index]
        $dimension = if ($size -eq 256) { 0 } else { $size }
        $writer.Write([byte]$dimension)
        $writer.Write([byte]$dimension)
        $writer.Write([byte]0)
        $writer.Write([byte]0)
        $writer.Write([UInt16]1)
        $writer.Write([UInt16]32)
        $writer.Write([UInt32]$bytes.Length)
        $writer.Write([UInt32]$offset)
        $offset += $bytes.Length
    }
    foreach ($bytes in $images) { $writer.Write($bytes) }
    $icoBytes = $stream.ToArray()
} finally {
    $writer.Dispose()
    $stream.Dispose()
}
[System.IO.File]::WriteAllBytes($icoPath, $icoBytes)

Write-Host "使用 jpackage 生成绿色版 EXE..." -ForegroundColor Cyan
$stageRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("ufml-jpackage-" + [guid]::NewGuid().ToString("N"))
& $jpackage --type app-image --name UFML-Launcher --dest $stageRoot --input $inputDir `
    --main-jar "UFML-Launcher.jar" --main-class "io.github.ufml.launcher.Main" `
    --app-version "1.0.1" --vendor "UFML" --description "Minecraft Java 启动器" `
    --icon $icoPath --java-options "-Dfile.encoding=UTF-8"
$stagedApp = Join-Path $stageRoot "UFML-Launcher"
if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath (Join-Path $stagedApp "UFML-Launcher.exe"))) {
    throw "jpackage 打包失败。"
}
New-Item -ItemType Directory -Path $distDir -Force | Out-Null
Copy-Item -LiteralPath $stagedApp -Destination $distDir -Recurse -Force
if (-not (Test-Path -LiteralPath (Join-Path $appDir "UFML-Launcher.exe"))) {
    throw "复制 jpackage 产物失败。"
}

Write-Host "安装绿色版到便携目录..." -ForegroundColor Cyan
New-Item -ItemType Directory -Path $installRoot, (Join-Path $installRoot "Config"),
    (Join-Path $installRoot "Minecraft") -Force | Out-Null
foreach ($name in @("app", "runtime")) {
    $oldRuntime = Join-Path $installRoot $name
    if (Test-Path -LiteralPath $oldRuntime) {
        $resolved = [System.IO.Path]::GetFullPath($oldRuntime)
        if (-not $resolved.StartsWith([System.IO.Path]::GetFullPath($installRoot) + [System.IO.Path]::DirectorySeparatorChar,
                [System.StringComparison]::OrdinalIgnoreCase)) {
            throw "安装路径不在便携目录内: $resolved"
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
Get-ChildItem -LiteralPath $appDir -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $installRoot -Recurse -Force
}

Write-Host "压缩分发包..." -ForegroundColor Cyan
$packageApp = Join-Path $packageDir "UFML"
New-Item -ItemType Directory -Path $packageApp, (Join-Path $packageApp "Config"),
    (Join-Path $packageApp "Minecraft") -Force | Out-Null
Get-ChildItem -LiteralPath $appDir -Force | ForEach-Object {
    Copy-Item -LiteralPath $_.FullName -Destination $packageApp -Recurse -Force
}
Compress-Archive -Path $packageApp -DestinationPath $zipPath -CompressionLevel Optimal -Force

$tempRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath())
$stageFull = [System.IO.Path]::GetFullPath($stageRoot)
if ($stageFull.StartsWith($tempRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    Remove-Item -LiteralPath $stageFull -Recurse -Force -ErrorAction SilentlyContinue
}

$exe = Join-Path $installRoot "UFML-Launcher.exe"
$sizeMb = [Math]::Round((Get-Item -LiteralPath $zipPath).Length / 1MB, 1)
Write-Host "打包完成:" -ForegroundColor Green
Write-Host "  EXE: $exe"
Write-Host "  Minecraft: $(Join-Path $installRoot 'Minecraft')"
Write-Host "  Config: $(Join-Path $installRoot 'Config')"
Write-Host "  ZIP: $zipPath ($sizeMb MB)"
Write-Host "绿色版目录包含内置 Java，目标电脑无需安装 Java。"
