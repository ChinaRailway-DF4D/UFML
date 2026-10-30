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

public final class ConfigStore {
    public LauncherConfig load() {
        LauncherConfig config = new LauncherConfig();
        Path file = AppPaths.configFile();
        if (!Files.isRegularFile(file)) {
            return config;
        }
        try {
            Map<String, Object> json = Json.object(Json.parse(Files.readString(file, StandardCharsets.UTF_8)));
            config.username(Json.string(json, "username", config.username()));
            config.versionId(Json.string(json, "versionId", config.versionId()));
            config.javaPath(Json.string(json, "javaPath", config.javaPath()));
            config.memoryMb(Json.integer(json, "memoryMb", config.memoryMb()));
            config.showSnapshots(Json.bool(json, "showSnapshots", config.showSnapshots()));
        } catch (Exception ex) {
            LauncherLog.warn("无法读取配置，已使用默认设置: " + ex.getMessage());
        }
        return config;
    }

    public void save(LauncherConfig config) {
        Path file = AppPaths.configFile();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("username", config.username());
        json.put("versionId", config.versionId());
        json.put("gameDirectory", AppPaths.defaultGameDirectory().toString());
        json.put("javaPath", config.javaPath());
        json.put("memoryMb", config.memoryMb());
        json.put("showSnapshots", config.showSnapshots());
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temporary, Json.stringifyPretty(json) + System.lineSeparator(), StandardCharsets.UTF_8);
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            LauncherLog.warn("保存配置失败: " + ex.getMessage());
        }
    }
}
