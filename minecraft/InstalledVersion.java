package io.github.ufml.launcher.minecraft;

import java.nio.file.Path;
import java.util.List;

public record InstalledVersion(
        String id,
        Path versionDirectory,
        Path clientJar,
        Path nativesDirectory,
        Path assetsDirectory,
        String assetsIndexName,
        Path loggingConfig,
        List<Path> classpath
) { }
