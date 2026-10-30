package io.github.ufml.launcher;

public record VersionSettings(String javaPath, int memoryMb, boolean fullscreen,
                              int windowWidth, int windowHeight,
                              String jvmArguments, String gameArguments) {
    public static final int DEFAULT_WIDTH = 854;
    public static final int DEFAULT_HEIGHT = 480;
    public static final int MIN_SIZE = 320;
    public static final int MAX_SIZE = 7680;
    public static final int MIN_MEMORY = 1024;
    public static final int MAX_MEMORY = 65536;

    public VersionSettings {
        javaPath = javaPath == null ? "" : javaPath.trim();
        memoryMb = memoryMb <= 0 ? 0 : Math.max(MIN_MEMORY, Math.min(MAX_MEMORY, memoryMb));
        windowWidth = clamp(windowWidth);
        windowHeight = clamp(windowHeight);
        jvmArguments = jvmArguments == null ? "" : jvmArguments.trim();
        gameArguments = gameArguments == null ? "" : gameArguments.trim();
    }

    public VersionSettings(String javaPath, int windowWidth, int windowHeight) {
        this(javaPath, 0, false, windowWidth, windowHeight, "", "");
    }

    public static VersionSettings defaults() {
        return new VersionSettings("", 0, false, DEFAULT_WIDTH, DEFAULT_HEIGHT, "", "");
    }

    private static int clamp(int value) {
        return Math.max(MIN_SIZE, Math.min(MAX_SIZE, value));
    }
}
