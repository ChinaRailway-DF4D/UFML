package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.VersionSettings;
import io.github.ufml.launcher.util.Json;
import io.github.ufml.launcher.util.LauncherLog;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class GameLauncher {
    public static final String LAUNCHER_NAME = "UFML";
    public static final String LAUNCHER_VERSION = "1.0.0";

    public Process launch(GameVersion version, InstalledVersion installed, JavaRuntimeManager.JavaInfo java,
                          Path gameDirectory, String username, UUID playerId, int globalMemoryMb,
                          VersionSettings settings)
            throws IOException {
        if (settings == null) settings = VersionSettings.defaults();
        List<String> command = buildCommand(version, installed, java.executable(), gameDirectory,
                username, playerId, globalMemoryMb, settings);
        LauncherLog.info("启动命令: " + redactForLog(command));
        Process process = new ProcessBuilder(command)
                .directory(gameDirectory.toFile())
                .redirectErrorStream(true)
                .start();
        LauncherLog.info("游戏进程已启动，PID " + process.pid());
        return process;
    }

    public List<String> buildCommand(GameVersion version, InstalledVersion installed, Path javaExecutable,
                                     Path gameDirectory, String username, UUID playerId, int memoryMb,
                                     int windowWidth, int windowHeight) {
        return buildCommand(version, installed, javaExecutable, gameDirectory, username, playerId,
                memoryMb, new VersionSettings("", 0, false, windowWidth, windowHeight, "", ""));
    }

    public List<String> buildCommand(GameVersion version, InstalledVersion installed, Path javaExecutable,
                                     Path gameDirectory, String username, UUID playerId, int globalMemoryMb,
                                     VersionSettings settings) {
        if (settings == null) settings = VersionSettings.defaults();
        int memoryMb = settings.memoryMb() > 0 ? settings.memoryMb() : globalMemoryMb;
        Map<String, Boolean> features = features();
        Map<String, String> values = placeholders(version, installed, gameDirectory, username, playerId,
                settings.windowWidth(), settings.windowHeight());
        List<String> jvmArguments = new ArrayList<>(
                ArgumentBuilder.resolve(version.jvmArguments(), features));
        List<String> gameArguments;
        if (!version.gameArguments().isEmpty()) {
            gameArguments = ArgumentBuilder.resolve(version.gameArguments(), features);
        } else {
            gameArguments = ArgumentBuilder.legacy(version.legacyArguments());
        }
        jvmArguments.addAll(ArgumentBuilder.legacy(settings.jvmArguments()));

        List<String> command = new ArrayList<>();
        command.add(javaExecutable.toString());
        command.add("-Xms" + Math.min(512, Math.max(256, memoryMb)) + "M");
        command.add("-Xmx" + Math.max(1024, memoryMb) + "M");
        command.add("-Dfile.encoding=UTF-8");
        command.add("-Dlog4j2.formatMsgNoLookups=true");
        command.add("-Dminecraft.launcher.brand=" + LAUNCHER_NAME);
        command.add("-Dminecraft.launcher.version=" + LAUNCHER_VERSION);

        String logArgument = Json.string(version.loggingClient(), "argument");
        if (logArgument != null && installed.loggingConfig() != null) {
            command.add(logArgument.replace("${path}", installed.loggingConfig().toString()));
        }
        if (jvmArguments.stream().noneMatch(argument -> argument.contains("-Djava.library.path"))) {
            command.add("-Djava.library.path=" + installed.nativesDirectory());
        }
        List<String> substitutedJvm = ArgumentBuilder.substitute(jvmArguments, values);
        boolean hasClasspath = substitutedJvm.stream().anyMatch(argument -> argument.equals("-cp")
                || argument.equals("-classpath") || argument.startsWith("-Djava.class.path"));
        command.addAll(substitutedJvm);
        if (!hasClasspath) {
            command.add("-cp");
            command.add(values.get("classpath"));
        }
        command.add(version.mainClass());
        List<String> finalGameArguments = new ArrayList<>(
                ArgumentBuilder.substitute(gameArguments, values));
        finalGameArguments.addAll(ArgumentBuilder.substitute(
                ArgumentBuilder.legacy(settings.gameArguments()), values));
        if (settings.fullscreen() && !finalGameArguments.contains("--fullscreen")) {
            finalGameArguments.add("--fullscreen");
        }
        command.addAll(finalGameArguments);
        return List.copyOf(command);
    }

    public static UUID offlineUuid(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8));
    }

    private static Map<String, String> placeholders(GameVersion version, InstalledVersion installed,
                                                     Path gameDirectory, String username, UUID playerId,
                                                     int windowWidth, int windowHeight) {
        Map<String, String> values = new LinkedHashMap<>();
        String uuid = playerId.toString().replace("-", "");
        String classpath = String.join(File.pathSeparator, installed.classpath().stream()
                .map(Path::toString).toList());
        values.put("auth_player_name", username);
        values.put("version_name", version.id());
        values.put("game_directory", gameDirectory.toString());
        values.put("assets_root", installed.assetsDirectory().toString());
        values.put("assets_index_name", installed.assetsIndexName());
        values.put("auth_uuid", uuid);
        values.put("auth_access_token", "0");
        values.put("clientid", "");
        values.put("auth_xuid", "");
        values.put("user_type", "legacy");
        values.put("version_type", version.type());
        values.put("natives_directory", installed.nativesDirectory().toString());
        values.put("launcher_name", LAUNCHER_NAME);
        values.put("launcher_version", LAUNCHER_VERSION);
        values.put("classpath", classpath);
        values.put("classpath_separator", File.pathSeparator);
        values.put("library_directory", gameDirectory.resolve("libraries").toString());
        values.put("game_assets", installed.assetsDirectory().resolve("virtual")
                .resolve(installed.assetsIndexName()).toString());
        values.put("auth_session", "token:0:" + uuid);
        values.put("user_properties", "{}");
        values.put("resolution_width", String.valueOf(windowWidth));
        values.put("resolution_height", String.valueOf(windowHeight));
        values.put("quickPlayPath", "");
        values.put("quickPlaySingleplayer", "");
        values.put("quickPlayMultiplayer", "");
        values.put("quickPlayRealms", "");
        return values;
    }

    private static Map<String, Boolean> features() {
        return Map.of(
                "is_demo_user", false,
                "has_custom_resolution", true,
                "has_quick_plays_support", false,
                "is_quick_play_singleplayer", false,
                "is_quick_play_multiplayer", false,
                "is_quick_play_realms", false
        );
    }

    private static String redactForLog(List<String> command) {
        StringBuilder out = new StringBuilder();
        for (String part : command) {
            if (out.length() > 0) out.append(' ');
            out.append(part.contains(" ") ? '"' + part + '"' : part);
        }
        return out.toString();
    }
}
