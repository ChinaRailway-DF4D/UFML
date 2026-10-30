package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.Json;
import io.github.ufml.launcher.util.PathSafety;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Resolves loader profiles that inherit their client and assets from a vanilla version. */
public final class VersionInheritance {
    private VersionInheritance() { }

    public static GameVersion resolve(GameVersion version, Path gameDirectory) throws IOException {
        if (version.inheritanceResolved() || version.inheritsFrom() == null) return version;
        return resolve(version.raw(), gameDirectory.resolve("versions"), new HashSet<>());
    }

    private static GameVersion resolve(Map<String, Object> raw, Path versionsRoot, Set<String> chain)
            throws IOException {
        String id = Json.string(raw, "id", "unknown");
        String parentId = Json.string(raw, "inheritsFrom");
        if (parentId == null || parentId.isBlank()) return GameVersion.fromJson(raw);
        if (!chain.add(id)) {
            throw new IOException("版本继承链出现循环: " + id);
        }

        Path parentFile = versionsRoot.resolve(PathSafety.safeName(parentId))
                .resolve(parentId + ".json");
        if (!Files.isRegularFile(parentFile)) {
            throw new IOException("缺少基础版本 " + parentId + "，请在“游戏管理”中补全该版本");
        }
        Map<String, Object> parentRaw = Json.object(Json.parse(
                Files.readString(parentFile, StandardCharsets.UTF_8)));
        GameVersion parent = resolve(parentRaw, versionsRoot, chain);
        chain.remove(id);

        Map<String, Object> merged = merge(parent.raw(), raw);
        return GameVersion.inherited(merged, raw, parent.clientVersionId());
    }

    static Map<String, Object> merge(Map<String, Object> parent, Map<String, Object> child) {
        Map<String, Object> merged = new LinkedHashMap<>(parent);
        for (Map.Entry<String, Object> entry : child.entrySet()) {
            String key = entry.getKey();
            if ("libraries".equals(key)) {
                merged.put(key, mergeLibraries(parent.get(key), entry.getValue()));
            } else if ("arguments".equals(key)) {
                merged.put(key, mergeArguments(parent.get(key), entry.getValue()));
            } else if ("downloads".equals(key)) {
                Map<String, Object> result = new LinkedHashMap<>(Json.object(parent, key));
                result.putAll(Json.object(entry.getValue()));
                merged.put(key, result);
            } else if (entry.getValue() instanceof Map<?, ?> map && map.isEmpty()) {
                // Empty loader maps must not erase a usable parent section.
                if (!merged.containsKey(key)) merged.put(key, entry.getValue());
            } else {
                merged.put(key, entry.getValue());
            }
        }
        return merged;
    }

    private static List<Object> mergeLibraries(Object rawParent, Object rawChild) {
        List<Object> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object library : Json.array(rawChild)) {
            if (seen.add(libraryKey(Json.object(library)))) result.add(library);
        }
        for (Object library : Json.array(rawParent)) {
            if (seen.add(libraryKey(Json.object(library)))) result.add(library);
        }
        return result;
    }

    private static Map<String, Object> mergeArguments(Object rawParent, Object rawChild) {
        Map<String, Object> parent = Json.object(rawParent);
        Map<String, Object> child = Json.object(rawChild);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String section : List.of("jvm", "game")) {
            List<Object> values = new ArrayList<>();
            values.addAll(Json.array(parent, section));
            values.addAll(Json.array(child, section));
            result.put(section, values);
        }
        return result;
    }

    private static String libraryKey(Map<String, Object> library) {
        String name = Json.string(library, "name");
        if (name != null && !name.isBlank()) {
            String coordinate = name.contains("@") ? name.substring(0, name.indexOf('@')) : name;
            String[] parts = coordinate.split(":");
            if (parts.length >= 2) {
                String classifier = parts.length >= 4 ? ":" + parts[3] : "";
                return parts[0] + ":" + parts[1] + classifier;
            }
            return coordinate;
        }
        Map<String, Object> artifact = Json.object(Json.object(library, "downloads"), "artifact");
        return Json.string(artifact, "path", Json.stringify(library));
    }
}
