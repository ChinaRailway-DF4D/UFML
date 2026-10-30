package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record VersionManifest(String latestRelease, String latestSnapshot, List<VersionSummary> versions) {
    public static VersionManifest fromJson(Map<String, Object> json) {
        Map<String, Object> latest = Json.object(json, "latest");
        List<VersionSummary> versions = new ArrayList<>();
        for (Object item : Json.array(json, "versions")) {
            versions.add(VersionSummary.fromJson(Json.object(item)));
        }
        return new VersionManifest(
                Json.string(latest, "release", ""),
                Json.string(latest, "snapshot", ""),
                List.copyOf(versions)
        );
    }
}
