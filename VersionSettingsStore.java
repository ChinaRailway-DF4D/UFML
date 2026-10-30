package io.github.ufml.launcher;

import io.github.ufml.launcher.util.AppPaths;
import io.github.ufml.launcher.util.Json;
import io.github.ufml.launcher.util.LauncherLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VersionSettingsStore {
    private final Path file;
    private final Map<String, VersionSettings> settings = new LinkedHashMap<>();

    public VersionSettingsStore() {
        this(AppPaths.versionSettingsFile());
    }

    VersionSettingsStore(Path file) {
        this.file = file;
        load();
    }

    public VersionSettings get(String versionId) {
        return settings.getOrDefault(versionId, VersionSettings.defaults());
    }

    public void put(String versionId, VersionSettings value) {
        settings.put(versionId, value);
        save();
    }

    public void remove(String versionId) {
        if (settings.remove(versionId) != null) save();
    }

    private void load() {
        if (!Files.isRegularFile(file)) return;
        try {
            Map<String, Object> root = Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            Map<String, Object> versions = Json.object(root, "versions");
            for (Map.Entry<String, Object> entry : versions.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?>)) continue;
                Map<String, Object> value = Json.object(entry.getValue());
                settings.put(entry.getKey(), new VersionSettings(
                        Json.string(value, "javaPath", ""),
                        Json.integer(value, "memoryMb", 0),
                        Json.bool(value, "fullscreen", false),
                        Json.integer(value, "windowWidth", VersionSettings.DEFAULT_WIDTH),
                        Json.integer(value, "windowHeight", VersionSettings.DEFAULT_HEIGHT),
                        Json.string(value, "jvmArguments", ""),
                        Json.string(value, "gameArguments", "")));
            }
        } catch (Exception ex) {
            LauncherLog.warn("读取版本设置失败: " + ex.getMessage());
        }
    }

    private void save() {
        Map<String, Object> versions = new LinkedHashMap<>();
        for (Map.Entry<String, VersionSettings> entry : settings.entrySet()) {
            VersionSettings value = entry.getValue();
            Map<String, Object> json = new LinkedHashMap<>();
            json.put("javaPath", value.javaPath());
            json.put("memoryMb", value.memoryMb());
            json.put("fullscreen", value.fullscreen());
            json.put("windowWidth", value.windowWidth());
            json.put("windowHeight", value.windowHeight());
            json.put("jvmArguments", value.jvmArguments());
            json.put("gameArguments", value.gameArguments());
            versions.put(entry.getKey(), json);
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("versions", versions);
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, Json.stringifyPretty(root) + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            LauncherLog.warn("保存版本设置失败: " + ex.getMessage());
        }
    }
}
