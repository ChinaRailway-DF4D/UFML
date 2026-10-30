package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.Json;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class RuleEvaluator {
    public static final String OS_NAME = detectOsName();

    private RuleEvaluator() { }

    public static boolean allows(Map<String, Object> item, Map<String, Boolean> features) {
        List<Object> rules = item.get("rules") == null ? List.of() : Json.array(item, "rules");
        if (rules.isEmpty()) return true;
        boolean allowed = false;
        for (Object rawRule : rules) {
            Map<String, Object> rule = Json.object(rawRule);
            if (!matches(rule, features)) continue;
            allowed = "allow".equalsIgnoreCase(Json.string(rule, "action", "allow"));
        }
        return allowed;
    }

    private static boolean matches(Map<String, Object> rule, Map<String, Boolean> features) {
        Object rawOs = rule.get("os");
        if (rawOs != null) {
            Map<String, Object> os = Json.object(rawOs);
            String name = Json.string(os, "name");
            if (name != null && !name.isBlank() && !OS_NAME.equals(name)) return false;
            String arch = Json.string(os, "arch");
            if (arch != null && !arch.isBlank() && !matchesArch(arch)) return false;
            String version = Json.string(os, "version");
            if (version != null && !version.isBlank()
                    && !Pattern.compile(version).matcher(System.getProperty("os.version", "")).matches()) {
                return false;
            }
        }
        Object rawFeatures = rule.get("features");
        if (rawFeatures != null) {
            for (Map.Entry<String, Object> entry : Json.object(rawFeatures).entrySet()) {
                boolean expected = Boolean.TRUE.equals(entry.getValue());
                boolean actual = Boolean.TRUE.equals(features.get(entry.getKey()));
                if (expected != actual) return false;
            }
        }
        return true;
    }

    private static boolean matchesArch(String expected) {
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        return switch (expected) {
            case "x86" -> arch.equals("x86") || arch.equals("i386") || arch.equals("i686");
            case "x86_64" -> arch.equals("amd64") || arch.equals("x86_64");
            case "arm64" -> arch.equals("aarch64") || arch.equals("arm64");
            default -> arch.equals(expected.toLowerCase(Locale.ROOT));
        };
    }

    public static String detectOsName() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return "windows";
        if (os.contains("mac") || os.contains("darwin")) return "osx";
        return "linux";
    }
}
