package com.deathmotion.mcsources.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class LibraryDownloader {
    private static final String MOJANG_LIBRARIES = "https://libraries.minecraft.net/";

    private LibraryDownloader() {
    }

    public static List<Path> resolve(JsonObject metadata, Path librariesDir) {
        List<Path> resolved = new ArrayList<>();
        if (!metadata.has("libraries")) {
            return resolved;
        }
        JsonArray libraries = metadata.getAsJsonArray("libraries");
        for (var element : libraries) {
            JsonObject library = element.getAsJsonObject();
            String name = library.has("name") ? library.get("name").getAsString() : "";
            if (name.contains("natives")) {
                continue;
            }

            String relativePath = null;
            String url = null;
            if (library.has("downloads")) {
                JsonObject downloads = library.getAsJsonObject("downloads");
                if (downloads.has("artifact")) {
                    JsonObject artifact = downloads.getAsJsonObject("artifact");
                    relativePath = artifact.has("path") ? artifact.get("path").getAsString() : null;
                    url = artifact.has("url") ? artifact.get("url").getAsString() : null;
                }
            }
            if ((relativePath == null || url == null || url.isEmpty()) && !name.isEmpty()) {
                relativePath = toMavenPath(name);
                if (relativePath != null) {
                    url = MOJANG_LIBRARIES + relativePath;
                }
            }
            if (relativePath == null || url == null || url.isEmpty() || relativePath.contains("natives")) {
                continue;
            }

            try {
                resolved.add(Downloader.download(url, librariesDir.resolve(relativePath)));
            } catch (IOException ignored) {
            }
        }
        return resolved;
    }

    private static String toMavenPath(String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length < 3) {
            return null;
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }
}
