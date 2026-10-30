package io.github.ufml.launcher.minecraft;

import io.github.ufml.launcher.util.Hashing;
import io.github.ufml.launcher.util.LauncherLog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;

public final class Downloader {
    private static final String USER_AGENT = "UFML-Launcher/1.0 (Java " + System.getProperty("java.version") + ")";
    private static final int MAX_ATTEMPTS = 3;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    public byte[] fetchBytes(String url) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(2))
                        .header("User-Agent", USER_AGENT)
                        .GET().build();
                HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    return response.body();
                }
                last = new IOException("HTTP " + response.statusCode() + " - " + url);
                if (response.statusCode() >= 400 && response.statusCode() < 500) break;
            } catch (IOException ex) {
                last = ex;
            }
            sleep(attempt);
        }
        throw last == null ? new IOException("Download failed: " + url) : last;
    }

    public String fetchString(String url) throws IOException, InterruptedException {
        return new String(fetchBytes(url), StandardCharsets.UTF_8);
    }

    public boolean ensureFile(String url, Path destination, String expectedSha1, long expectedSize)
            throws IOException, InterruptedException {
        if (Files.isRegularFile(destination)) {
            boolean sizeMatches = expectedSize <= 0 || Files.size(destination) == expectedSize;
            boolean hashKnown = expectedSha1 != null && !expectedSha1.isBlank();
            if (sizeMatches && hashKnown) {
                if (Hashing.sha1(destination).equalsIgnoreCase(expectedSha1)) return false;
            } else if (sizeMatches) {
                return false;
            }
        }
        download(url, destination, expectedSha1, expectedSize);
        return true;
    }

    public void download(String url, Path destination, String expectedSha1, long expectedSize)
            throws IOException, InterruptedException {
        Files.createDirectories(destination.getParent());
        Path temporary = destination.resolveSibling(destination.getFileName() + ".part");
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofMinutes(10))
                        .header("User-Agent", USER_AGENT)
                        .GET().build();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    response.body().close();
                    last = new IOException("HTTP " + status + " - " + url);
                    if (status >= 400 && status < 500) break;
                    sleep(attempt);
                    continue;
                }

                MessageDigest digest = Hashing.digest("SHA-1");
                try (InputStream input = response.body();
                     OutputStream file = Files.newOutputStream(temporary);
                     DigestOutputStream output = new DigestOutputStream(file, digest)) {
                    input.transferTo(output);
                }

                long actualSize = Files.size(temporary);
                if (expectedSize > 0 && actualSize != expectedSize) {
                    throw new IOException("文件大小不符: 期望 " + expectedSize + "，实际 " + actualSize);
                }
                String actualSha1 = HexFormat.of().formatHex(digest.digest());
                if (expectedSha1 != null && !expectedSha1.isBlank()
                        && !actualSha1.equalsIgnoreCase(expectedSha1)) {
                    throw new IOException("SHA-1 校验失败: " + destination.getFileName());
                }
                try {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING,
                            StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException moveFailure) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            } catch (IOException ex) {
                last = ex;
                try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
                if (attempt < MAX_ATTEMPTS) {
                    LauncherLog.warn("下载重试 " + attempt + "/" + (MAX_ATTEMPTS - 1) + ": " + shortUrl(url));
                }
                sleep(attempt);
            }
        }
        throw last == null ? new IOException("下载失败: " + url) : last;
    }

    private static String shortUrl(String url) {
        return url.length() <= 100 ? url : url.substring(0, 70) + "..." + url.substring(url.length() - 24);
    }

    private static void sleep(int attempt) {
        try {
            Thread.sleep(650L * Math.max(1, attempt));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
