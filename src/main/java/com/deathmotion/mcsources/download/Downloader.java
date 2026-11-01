package com.deathmotion.mcsources.download;

import com.deathmotion.mcsources.io.Hashing;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Downloader {
    private static final int ATTEMPTS = 4;
    private static final Map<Path, Object> LOCKS = new ConcurrentHashMap<>();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private Downloader() {
    }

    public static Path download(String url, Path destination) throws IOException {
        return download(url, destination, null);
    }

    public static Path download(String url, Path destination, String sha1) throws IOException {
        Path key = destination.toAbsolutePath().normalize();
        synchronized (LOCKS.computeIfAbsent(key, path -> new Object())) {
            if (isUsable(destination, sha1)) {
                return destination;
            }
            Files.createDirectories(destination.getParent());
            Path temp = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".part");
            try {
                fetchInto(url, temp, sha1);
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
                return destination;
            } finally {
                Files.deleteIfExists(temp);
            }
        }
    }

    public static String getString(String url) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpResponse<String> response = CLIENT.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 404) {
                    throw new NotFoundException(url);
                }
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + response.statusCode() + " for " + url);
                }
                return response.body();
            } catch (NotFoundException e) {
                throw e;
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + url, e);
            }
            backoff(attempt);
        }
        throw new IOException("Failed to GET " + url, last);
    }

    public static JsonObject getJson(String url) throws IOException {
        return JsonParser.parseString(getString(url)).getAsJsonObject();
    }

    private static void fetchInto(String url, Path temp, String sha1) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= ATTEMPTS; attempt++) {
            try {
                HttpResponse<Path> response = CLIENT.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build(),
                        HttpResponse.BodyHandlers.ofFile(temp));
                if (response.statusCode() == 404) {
                    throw new NotFoundException(url);
                }
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + response.statusCode() + " for " + url);
                }
                if (sha1 != null && !sha1.equalsIgnoreCase(sha1Of(temp))) {
                    throw new IOException("SHA-1 mismatch for " + url);
                }
                return;
            } catch (NotFoundException e) {
                throw e;
            } catch (IOException e) {
                last = e;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while downloading " + url, e);
            }
            Files.deleteIfExists(temp);
            backoff(attempt);
        }
        throw new IOException("Failed to download " + url, last);
    }

    private static boolean isUsable(Path file, String sha1) throws IOException {
        if (!Files.isRegularFile(file) || Files.size(file) == 0) {
            return false;
        }
        return sha1 == null || sha1.equalsIgnoreCase(sha1Of(file));
    }

    private static String sha1Of(Path file) throws IOException {
        MessageDigest digest = Hashing.newSha1();
        byte[] buffer = new byte[1 << 16];
        try (InputStream in = Files.newInputStream(file)) {
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
        }
        return Hashing.hex(digest.digest());
    }

    private static void backoff(int attempt) throws IOException {
        if (attempt == ATTEMPTS) {
            return;
        }
        try {
            Thread.sleep(1000L * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting to retry", e);
        }
    }

    public static final class NotFoundException extends IOException {
        public NotFoundException(String url) {
            super("HTTP 404 for " + url);
        }
    }
}
