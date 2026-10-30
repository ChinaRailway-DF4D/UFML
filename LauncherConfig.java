package io.github.ufml.launcher;

import io.github.ufml.launcher.util.AppPaths;

import java.nio.file.Path;

public final class LauncherConfig {
    private String username = "Steve";
    private String versionId = "";
    private String javaPath = "";
    private int memoryMb = 4096;
    private boolean showSnapshots = false;

    public String username() { return username; }
    public void username(String username) { this.username = username; }
    public String versionId() { return versionId; }
    public void versionId(String versionId) { this.versionId = versionId; }
    public String gameDirectory() { return AppPaths.defaultGameDirectory().toString(); }
    public Path gameDirectoryPath() { return AppPaths.defaultGameDirectory(); }
    public String javaPath() { return javaPath; }
    public void javaPath(String javaPath) { this.javaPath = javaPath; }
    public int memoryMb() { return memoryMb; }
    public void memoryMb(int memoryMb) { this.memoryMb = Math.max(1024, Math.min(65536, memoryMb)); }
    public boolean showSnapshots() { return showSnapshots; }
    public void showSnapshots(boolean showSnapshots) { this.showSnapshots = showSnapshots; }
}
