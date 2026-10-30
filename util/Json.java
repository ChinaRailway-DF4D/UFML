package io.github.ufml.launcher.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Minimal, dependency-free JSON parser/writer used for Mojang metadata and launcher config. */
public final class Json {
    private Json() {
    }

    public static Object parse(String text) {
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw parser.error("Unexpected content after JSON value");
        }
        return value;
    }

    public static Map<String, Object> object(Object value) {
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        throw new IllegalArgumentException("Expected a JSON object, got " + typeName(value));
    }

    public static Map<String, Object> object(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            return Map.of();
        }
        return object(value);
    }

    public static List<Object> array(Object value) {
        if (value instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> typed = (List<Object>) list;
            return typed;
        }
        throw new IllegalArgumentException("Expected a JSON array, got " + typeName(value));
    }

    public static List<Object> array(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        if (value == null) {
            return List.of();
        }
        return array(value);
    }

    public static String string(Map<String, Object> parent, String key) {
        Object value = parent.get(key);
        return value == null ? null : String.valueOf(value);
    }

    public static String string(Map<String, Object> parent, String key, String fallback) {
        String value = string(parent, key);
        return value == null ? fallback : value;
    }

    public static long number(Map<String, Object> parent, String key, long fallback) {
        Object value = parent.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return fallback;
    }

    public static int integer(Map<String, Object> parent, String key, int fallback) {
        return (int) number(parent, key, fallback);
    }

    public static boolean bool(Map<String, Object> parent, String key, boolean fallback) {
        Object value = parent.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    public static String stringify(Object value) {
        StringBuilder out = new StringBuilder(256);
        write(value, out, false, 0);
        return out.toString();
    }

    public static String stringifyPretty(Object value) {
        StringBuilder out = new StringBuilder(256);
        write(value, out, true, 0);
        return out.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object value, StringBuilder out, boolean pretty, int depth) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String text) {
            writeString(text, out);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map<?, ?> map) {
            out.append('{');
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index++ > 0) {
                    out.append(',');
                }
                newline(out, pretty, depth + 1);
                writeString(String.valueOf(entry.getKey()), out);
                out.append(pretty ? ": " : ":");
                write(entry.getValue(), out, pretty, depth + 1);
            }
            if (!map.isEmpty()) {
                newline(out, pretty, depth);
            }
            out.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            out.append('[');
            int index = 0;
            for (Object item : iterable) {
                if (index++ > 0) {
                    out.append(',');
                }
                newline(out, pretty, depth + 1);
                write(item, out, pretty, depth + 1);
            }
            if (index > 0) {
                newline(out, pretty, depth);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
        }
    }

    private static void newline(StringBuilder out, boolean pretty, int depth) {
        if (!pretty) {
            return;
        }
        out.append('\n');
        out.append("  ".repeat(Math.max(0, depth)));
    }

    private static void writeString(String text, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (ch < 0x20) {
                        out.append(String.format("\\u%04x", (int) ch));
                    } else {
                        out.append(ch);
                    }
                }
            }
        }
        out.append('"');
    }

    private static String typeName(Object value) {
        return value == null ? "null" : value.getClass().getSimpleName();
    }

    private static final class Parser {
        private final String text;
        private int pos;

        private Parser(String text) {
            this.text = text == null ? "" : text;
        }

        private boolean atEnd() {
            return pos >= text.length();
        }

        private void skipWhitespace() {
            while (!atEnd()) {
                char ch = text.charAt(pos);
                if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        private Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw error("Unexpected end of JSON");
            }
            return switch (text.charAt(pos)) {
                case '{' -> readObject();
                case '[' -> readArray();
                case '"' -> readString();
                case 't' -> readLiteral("true", Boolean.TRUE);
                case 'f' -> readLiteral("false", Boolean.FALSE);
                case 'n' -> readLiteral("null", null);
                default -> readNumber();
            };
        }

        private Map<String, Object> readObject() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                pos++;
                return result;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw error("Expected an object key");
                }
                String key = readString();
                skipWhitespace();
                expect(':');
                result.put(key, readValue());
                skipWhitespace();
                if (peek('}')) {
                    pos++;
                    return result;
                }
                expect(',');
            }
        }

        private List<Object> readArray() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                pos++;
                return result;
            }
            while (true) {
                result.add(readValue());
                skipWhitespace();
                if (peek(']')) {
                    pos++;
                    return result;
                }
                expect(',');
            }
        }

        private String readString() {
            expect('"');
            StringBuilder out = new StringBuilder();
            while (!atEnd()) {
                char ch = text.charAt(pos++);
                if (ch == '"') {
                    return out.toString();
                }
                if (ch != '\\') {
                    out.append(ch);
                    continue;
                }
                if (atEnd()) {
                    throw error("Unterminated escape sequence");
                }
                char escaped = text.charAt(pos++);
                switch (escaped) {
                    case '"' -> out.append('"');
                    case '\\' -> out.append('\\');
                    case '/' -> out.append('/');
                    case 'b' -> out.append('\b');
                    case 'f' -> out.append('\f');
                    case 'n' -> out.append('\n');
                    case 'r' -> out.append('\r');
                    case 't' -> out.append('\t');
                    case 'u' -> out.append(readUnicodeEscape());
                    default -> throw error("Invalid escape sequence \\" + escaped);
                }
            }
            throw error("Unterminated string");
        }

        private char readUnicodeEscape() {
            if (pos + 4 > text.length()) {
                throw error("Incomplete unicode escape");
            }
            String hex = text.substring(pos, pos + 4);
            pos += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException ex) {
                throw error("Invalid unicode escape \\u" + hex);
            }
        }

        private Object readNumber() {
            int start = pos;
            if (peek('-')) {
                pos++;
            }
            while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                pos++;
            }
            boolean decimal = false;
            if (peek('.')) {
                decimal = true;
                pos++;
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (peek('e') || peek('E')) {
                decimal = true;
                pos++;
                if (peek('+') || peek('-')) {
                    pos++;
                }
                while (!atEnd() && Character.isDigit(text.charAt(pos))) {
                    pos++;
                }
            }
            if (start == pos) {
                throw error("Expected a JSON value");
            }
            String token = text.substring(start, pos);
            try {
                return decimal ? Double.parseDouble(token) : Long.parseLong(token);
            } catch (NumberFormatException ex) {
                throw error("Invalid number " + token);
            }
        }

        private Object readLiteral(String literal, Object value) {
            if (!text.startsWith(literal, pos)) {
                throw error("Invalid literal");
            }
            pos += literal.length();
            return value;
        }

        private boolean peek(char expected) {
            return !atEnd() && text.charAt(pos) == expected;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (!peek(expected)) {
                throw error("Expected '" + expected + "'");
            }
            pos++;
        }

        private IllegalArgumentException error(String message) {
            int line = 1;
            int column = 1;
            for (int i = 0; i < Math.min(pos, text.length()); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                    column = 1;
                } else {
                    column++;
                }
            }
            return new IllegalArgumentException(message + " at line " + line + ", column " + column);
        }
    }
}
