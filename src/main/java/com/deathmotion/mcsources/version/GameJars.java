package com.deathmotion.mcsources.version;

import com.deathmotion.mcsources.download.Downloader;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;

public record GameJars(Path client, Path server, Path serverBundle) {

    static GameJars download(JsonObject downloads, Path directory) throws IOException {
        Path client = download(downloads, "client", directory.resolve("client.jar"));
        if (!downloads.has("server")) {
            return new GameJars(client, null, null);
        }
        Path bundle = download(downloads, "server", directory.resolve("server.jar"));
        Path server = ServerBundle.extractServerJar(bundle, directory.resolve("server-extracted.jar"));
        return new GameJars(client, server, bundle);
    }

    private static Path download(JsonObject downloads, String key, Path destination) throws IOException {
        JsonObject artifact = downloads.getAsJsonObject(key);
        String sha1 = artifact.has("sha1") ? artifact.get("sha1").getAsString() : null;
        return Downloader.download(artifact.get("url").getAsString(), destination, sha1);
    }
}
