package io.github.ufml.launcher.minecraft;

import java.util.Set;

public enum VersionCategory {
    RELEASE("正式版"),
    SNAPSHOT("快照版"),
    LEGACY("远古版"),
    APRIL_FOOLS("愚人节版");

    private static final Set<String> APRIL_FOOLS_IDS = Set.of(
            "2.0",
            "15w14a",
            "1.RV-Pre1",
            "3D Shareware v1.34",
            "20w14infinite",
            "22w13oneBlockAtATime",
            "23w13a_or_b",
            "24w14potato",
            "25w14craftmine"
    );

    private final String label;

    VersionCategory(String label) {
        this.label = label;
    }

    public String label() { return label; }

    public static VersionCategory of(VersionSummary version) {
        if (version == null) return RELEASE;
        if (isAprilFools(version.id()) || isAprilFoolsDate(version.releaseTime())) return APRIL_FOOLS;
        if ("old_alpha".equals(version.type()) || "old_beta".equals(version.type())) return LEGACY;
        if ("release".equals(version.type())) return RELEASE;
        if ("snapshot".equals(version.type())) return SNAPSHOT;
        return SNAPSHOT;
    }

    public static boolean isAprilFools(String id) {
        return id != null && APRIL_FOOLS_IDS.contains(id);
    }

    private static boolean isAprilFoolsDate(String releaseTime) {
        return releaseTime != null && releaseTime.length() >= 10
                && "-04-01".equals(releaseTime.substring(4, 10));
    }
}
