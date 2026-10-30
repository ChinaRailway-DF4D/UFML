package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.Json;

import java.util.List;
import java.util.Map;

public final class GameVersion {
    private final Map<String, Object> raw;
    private final Map<String, Object> storedRaw;
    private final String clientVersionId;
    private final boolean inheritanceResolved;

    private GameVersion(Map<String, Object> raw, Map<String, Object> storedRaw,
                        String clientVersionId, boolean inheritanceResolved) {
        this.raw = raw;
        this.storedRaw = storedRaw;
        this.clientVersionId = clientVersionId;
        this.inheritanceResolved = inheritanceResolved;
    }

    public static GameVersion fromJson(Map<String, Object> raw) {
        String id = Json.string(raw, "id", "unknown");
        return new GameVersion(raw, raw, id, false);
    }

    public static GameVersion inherited(Map<String, Object> mergedRaw, Map<String, Object> storedRaw,
                                        String clientVersionId) {
        return new GameVersion(mergedRaw, storedRaw, clientVersionId, true);
    }

    public Map<String, Object> raw() { return raw; }
    /** The original manifest written under versions/&lt;id&gt;/&lt;id&gt;.json. */
    public Map<String, Object> storedRaw() { return storedRaw; }
    public String clientVersionId() { return clientVersionId; }
    public boolean inheritanceResolved() { return inheritanceResolved; }
    public String id() { return Json.string(raw, "id", "unknown"); }
    public String type() { return Json.string(raw, "type", "release"); }

    public String mainClass() {
        String mainClass = Json.string(raw, "mainClass");
        if (mainClass == null || mainClass.isBlank()) {
            throw new IllegalArgumentException("版本元数据缺少 mainClass");
        }
        return mainClass;
    }

    public String assets() { return Json.string(raw, "assets", "legacy"); }
    public Map<String, Object> assetIndex() { return Json.object(raw, "assetIndex"); }
    public Map<String, Object> downloads() { return Json.object(raw, "downloads"); }

    public Map<String, Object> clientDownload() {
        Object client = downloads().get("client");
        return client == null ? Map.of() : Json.object(client);
    }

    public List<Object> libraries() { return Json.array(raw, "libraries"); }

    public List<Object> jvmArguments() {
        Object arguments = raw.get("arguments");
        return arguments == null ? List.of() : Json.array(Json.object(arguments), "jvm");
    }

    public List<Object> gameArguments() {
        Object arguments = raw.get("arguments");
        return arguments == null ? List.of() : Json.array(Json.object(arguments), "game");
    }

    public String legacyArguments() { return Json.string(raw, "minecraftArguments"); }

    public Map<String, Object> loggingClient() {
        Object logging = raw.get("logging");
        if (logging == null) return Map.of();
        Object client = Json.object(logging).get("client");
        return client == null ? Map.of() : Json.object(client);
    }

    public int requiredJavaMajor() {
        return Json.integer(Json.object(raw, "javaVersion"), "majorVersion", 8);
    }

    public String requiredRuntimeComponent() {
        return Json.string(Json.object(raw, "javaVersion"), "component", "");
    }

    public String inheritsFrom() { return Json.string(raw, "inheritsFrom"); }
}
