package io.github.ufml.launcher.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class PathSafety {
    private PathSafety() {
    }

    public static Path resolveInside(Path root, String relative) {
        if (relative == null || relative.isBlank()) {
            throw new IllegalArgumentException("Empty path");
        }
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path resolved = normalizedRoot.resolve(relative.replace('\\', '/')).normalize();
        if (!resolved.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Unsafe path outside " + normalizedRoot + ": " + relative);
        }
        return resolved;
    }

    public static void extractZip(Path zip, Path destination, boolean executable) throws IOException {
        extractZip(zip, destination, List.of(), executable);
    }

    public static void extractZip(Path zip, Path destination, List<String> excludes, boolean executable)
            throws IOException {
        Path root = destination.toAbsolutePath().normalize();
        Files.createDirectories(root);
        List<String> normalizedExcludes = new ArrayList<>();
        for (String exclude : excludes) {
            normalizedExcludes.add(exclude.replace('\\', '/'));
        }
        try (ZipFile archive = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = archive.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName().replace('\\', '/');
                if (name.startsWith("META-INF/") || name.equals("META-INF")) {
                    continue;
                }
                boolean excluded = false;
                for (String pattern : normalizedExcludes) {
                    if (name.equals(pattern) || name.startsWith(pattern + "/")) {
                        excluded = true;
                        break;
                    }
                }
                if (excluded) continue;
                Path target = resolveInside(root, name);
                if (entry.isDirectory()) {
                    Files.createDirectories(target);
                    continue;
                }
                Files.createDirectories(target.getParent());
                try (InputStream input = archive.getInputStream(entry)) {
                    Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
                }
                if (executable) {
                    target.toFile().setExecutable(true, false);
                }
            }
        }
    }

    public static void deleteDirectory(Path directory, Path allowedRoot) throws IOException {
        if (directory == null || !Files.exists(directory)) {
            return;
        }
        Path root = allowedRoot.toAbsolutePath().normalize();
        Path target = directory.toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)) {
            throw new IllegalArgumentException("Refusing to delete outside allowed root: " + target);
        }
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    public static String safeName(String value) {
        if (value == null || value.isBlank()) {
            return "default";
        }
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return safe.isBlank() ? "default" : safe;
    }
}
