package com.deathmotion.mcsources.download;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

public final class Downloader {
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(30))
            .build();

    private Downloader() {
    }

    public static Path download(String url, Path destination) throws IOException {
        if (Files.exists(destination) && Files.size(destination) > 0) {
            return destination;
        }
        Files.createDirectories(destination.getParent());
        Path temp = Files.createTempFile(destination.getParent(), destination.getFileName().toString(), ".part");
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<Path> response = CLIENT.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(10)).GET().build(),
                        HttpResponse.BodyHandlers.ofFile(temp));
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + response.statusCode() + " for " + url);
                }
                Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
                return destination;
            } catch (IOException | InterruptedException e) {
                last = e instanceof IOException io ? io : new IOException(e);
                backoff(attempt);
            }
        }
        Files.deleteIfExists(temp);
        throw new IOException("Failed to download " + url, last);
    }

    public static String getString(String url) throws IOException {
        IOException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                HttpResponse<String> response = CLIENT.send(
                        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build(),
                        HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    throw new IOException("HTTP " + response.statusCode() + " for " + url);
                }
                return response.body();
            } catch (IOException | InterruptedException e) {
                last = e instanceof IOException io ? io : new IOException(e);
                backoff(attempt);
            }
        }
        throw new IOException("Failed to GET " + url, last);
    }

    public static JsonObject getJson(String url) throws IOException {
        return JsonParser.parseString(getString(url)).getAsJsonObject();
    }

    private static void backoff(int attempt) {
        try {
            Thread.sleep(500L * attempt);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
