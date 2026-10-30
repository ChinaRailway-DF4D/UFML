package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ArgumentBuilder {
    private ArgumentBuilder() { }

    public static List<String> resolve(List<Object> arguments, Map<String, Boolean> features) {
        List<String> resolved = new ArrayList<>();
        for (Object raw : arguments) {
            if (raw instanceof String text) {
                resolved.add(text);
            } else if (raw instanceof Map<?, ?>) {
                Map<String, Object> conditional = Json.object(raw);
                if (!RuleEvaluator.allows(conditional, features)) continue;
                Object value = conditional.get("value");
                if (value instanceof String text) {
                    resolved.add(text);
                } else if (value instanceof List<?>) {
                    for (Object child : Json.array(value)) {
                        resolved.add(String.valueOf(child));
                    }
                }
            }
        }
        return resolved;
    }

    public static List<String> legacy(String commandLine) {
        List<String> tokens = new ArrayList<>();
        if (commandLine == null || commandLine.isBlank()) return tokens;
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean escaped = false;
        for (int i = 0; i < commandLine.length(); i++) {
            char ch = commandLine.charAt(i);
            if (escaped) {
                current.append(ch);
                escaped = false;
            } else if (ch == '\\' && quoted) {
                escaped = true;
            } else if (ch == '"') {
                quoted = !quoted;
            } else if (Character.isWhitespace(ch) && !quoted) {
                if (!current.isEmpty()) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }
        if (!current.isEmpty()) tokens.add(current.toString());
        return tokens;
    }

    public static List<String> substitute(List<String> arguments, Map<String, String> values) {
        List<String> result = new ArrayList<>(arguments.size());
        for (String argument : arguments) {
            String replaced = argument;
            for (Map.Entry<String, String> entry : values.entrySet()) {
                replaced = replaced.replace("${" + entry.getKey() + "}", entry.getValue());
            }
            result.add(replaced);
        }
        return result;
    }
}
