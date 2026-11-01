package com.deathmotion.mcsources.cli;

import com.deathmotion.mcsources.config.RunOptions;
import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.manifest.VersionManifest;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class VersionSelection {
    private final VersionManifest manifest;
    private final RunOptions options;

    VersionSelection(VersionManifest manifest, RunOptions options) {
        this.manifest = manifest;
        this.options = options;
    }

    List<MinecraftVersion> parse(String specs) throws IOException {
        Set<MinecraftVersion> selected = new LinkedHashSet<>();
        for (String spec : specs.split(",")) {
            if (!spec.isBlank()) {
                selected.addAll(parseOne(spec.trim()));
            }
        }
        return List.copyOf(selected);
    }

    private List<MinecraftVersion> parseOne(String spec) throws IOException {
        List<MinecraftVersion> candidates = manifest.versionsSinceBaseline(options.includeSnapshots());
        if (indexOf(candidates, spec) >= 0 || !spec.contains("-")) {
            return List.of(manifest.find(spec));
        }
        for (int dash = spec.indexOf('-'); dash > 0; dash = spec.indexOf('-', dash + 1)) {
            int low = indexOf(candidates, spec.substring(0, dash));
            int high = indexOf(candidates, spec.substring(dash + 1));
            if (low >= 0 && high >= 0) {
                return List.copyOf(candidates.subList(Math.min(low, high), Math.max(low, high) + 1));
            }
        }
        return List.of(manifest.find(spec));
    }

    List<MinecraftVersion> all() throws IOException {
        List<MinecraftVersion> versions = manifest.versionsSinceBaseline(options.includeSnapshots());
        int from = options.from() == null ? 0 : require(versions, options.from(), "-Pfrom");
        int to = options.to() == null ? versions.size() - 1 : require(versions, options.to(), "-Pto");
        if (from > to) {
            throw new IllegalArgumentException("-Pfrom=" + options.from() + " is newer than -Pto=" + options.to());
        }
        List<MinecraftVersion> selected = versions.subList(from, to + 1);
        if (options.limit() >= 0 && selected.size() > options.limit()) {
            selected = selected.subList(0, options.limit());
        }
        return List.copyOf(selected);
    }

    private static int require(List<MinecraftVersion> versions, String id, String flag) {
        int index = indexOf(versions, id);
        if (index < 0) {
            throw new IllegalArgumentException(flag + "=" + id + " is not a known version"
                    + " (pass -Psnapshots to include snapshots, run ./gradlew listVersions for the list)");
        }
        return index;
    }

    private static int indexOf(List<MinecraftVersion> versions, String id) {
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }
}
