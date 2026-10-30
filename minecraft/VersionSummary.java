package io.github.ufml.launcher.minecraft;

import java.nio.file.Path;
import java.util.Map;

public record VersionSummary(String id, String type, String url, String releaseTime, boolean local,
                             Path manifestPath) {
    public VersionSummary(String id, String type, String url, String releaseTime) {
        this(id, type, url, releaseTime, false, null);
    }

    public static VersionSummary fromJson(Map<String, Object> json) {
        return new VersionSummary(
                String.valueOf(json.get("id")),
                String.valueOf(json.getOrDefault("type", "release")),
                json.get("url") == null ? null : String.valueOf(json.get("url")),
                json.get("releaseTime") == null ? "" : String.valueOf(json.get("releaseTime")),
                false,
                null
        );
    }

    public static VersionSummary local(String id, String type, String releaseTime, Path manifestPath) {
        return new VersionSummary(id, type, null, releaseTime, true, manifestPath);
    }

    public boolean snapshot() { return "snapshot".equals(type); }

    public VersionCategory category() { return VersionCategory.of(this); }

    public String typeLabel() {
        VersionCategory category = category();
        if (category == VersionCategory.LEGACY) {
            return "old_beta".equals(type) ? "远古 Beta" : "远古 Alpha";
        }
        return category.label();
    }

    @Override
    public String toString() {
        return id + "    ·  " + typeLabel() + (local ? "  ·  本地" : "");
    }
}
