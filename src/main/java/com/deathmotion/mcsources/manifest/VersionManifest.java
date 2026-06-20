package com.deathmotion.mcsources.manifest;

import com.deathmotion.mcsources.download.Downloader;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class VersionManifest {
    public static final String BASELINE_VERSION = "1.7.2";
    private static final String MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private final Path cacheDir;
    private JsonObject manifest;

    public VersionManifest(Path cacheDir) {
        this.cacheDir = cacheDir;
    }

    private static MinecraftVersion byId(List<MinecraftVersion> versions, String id) {
        return versions.stream()
                .filter(version -> version.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Version " + id + " not found in manifest"));
    }

    public List<MinecraftVersion> versionsSinceBaseline(boolean includeSnapshots) throws IOException {
        List<MinecraftVersion> all = all();
        Instant baseline = byId(all, BASELINE_VERSION).releaseTime();
        List<MinecraftVersion> result = new ArrayList<>();
        for (MinecraftVersion version : all) {
            boolean atOrAfterBaseline = !version.releaseTime().isBefore(baseline);
            if (atOrAfterBaseline && (includeSnapshots || version.isRelease())) {
                result.add(version);
            }
        }
        result.sort(Comparator.comparing(MinecraftVersion::releaseTime));
        return result;
    }

    public MinecraftVersion find(String id) throws IOException {
        return byId(all(), id);
    }

    public JsonObject metadata(MinecraftVersion version) throws IOException {
        Path cache = cacheDir.resolve("meta").resolve(version.id() + ".json");
        if (Files.exists(cache) && Files.size(cache) > 0) {
            return JsonParser.parseString(Files.readString(cache)).getAsJsonObject();
        }
        JsonObject metadata = Downloader.getJson(version.url());
        Files.createDirectories(cache.getParent());
        Files.writeString(cache, metadata.toString());
        return metadata;
    }

    private synchronized JsonObject manifest() throws IOException {
        if (manifest == null) {
            manifest = Downloader.getJson(MANIFEST_URL);
        }
        return manifest;
    }

    private List<MinecraftVersion> all() throws IOException {
        List<MinecraftVersion> versions = new ArrayList<>();
        for (var element : manifest().getAsJsonArray("versions")) {
            JsonObject object = element.getAsJsonObject();
            versions.add(new MinecraftVersion(
                    object.get("id").getAsString(),
                    object.get("type").getAsString(),
                    object.get("url").getAsString(),
                    Instant.parse(object.get("releaseTime").getAsString())));
        }
        return versions;
    }
}
