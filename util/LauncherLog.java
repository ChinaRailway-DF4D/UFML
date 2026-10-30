package io.github.ufml.launcher.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class LauncherLog {
    private static final int MAX_CHARS = 300_000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final StringBuilder TEXT = new StringBuilder();
    private static final List<Consumer<String>> LISTENERS = new CopyOnWriteArrayList<>();
    private static final Path FILE = AppPaths.logs().resolve("launcher.log");

    private LauncherLog() { }

    public static void addListener(Consumer<String> listener) {
        LISTENERS.add(listener);
        listener.accept(text());
    }

    public static void info(String message) { write("INFO", message); }
    public static void warn(String message) { write("WARN", message); }
    public static void error(String message) { write("ERROR", message); }

    public static void error(String message, Throwable throwable) {
        error(message + ": " + throwable.getMessage());
        if (throwable.getCause() != null) {
            error("原因: " + throwable.getCause().getMessage());
        }
    }

    public static String text() {
        synchronized (TEXT) { return TEXT.toString(); }
    }

    public static Path file() { return FILE; }

    private static void write(String level, String message) {
        String line = "[" + LocalTime.now().format(TIME) + "] [" + level + "] " + message + System.lineSeparator();
        synchronized (TEXT) {
            TEXT.append(line);
            if (TEXT.length() > MAX_CHARS) {
                TEXT.delete(0, TEXT.length() - MAX_CHARS);
            }
        }
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(FILE, line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) { }
        for (Consumer<String> listener : LISTENERS) {
            listener.accept(line);
        }
    }
}
