package com.deathmotion.mcsources.diff;

import com.deathmotion.mcsources.config.GeneratorConfig;
import com.deathmotion.mcsources.diff.ResourceIndex.Resource;
import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.manifest.VersionManifest;
import com.deathmotion.mcsources.io.Directories;
import com.deathmotion.mcsources.pipeline.SourceTree;
import com.deathmotion.mcsources.version.AssembledVersion;
import com.deathmotion.mcsources.version.GameJars;
import com.deathmotion.mcsources.version.VersionPreparer;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class VersionDiff {
    private final GeneratorConfig config;
    private final VersionManifest manifest;

    public VersionDiff(GeneratorConfig config, VersionManifest manifest) {
        this.config = config;
        this.manifest = manifest;
    }

    private record Snapshot(String id, ClassIndex classes, ResourceIndex resources, ResourceIndex reports) {
    }

    public record Reports(Map<String, List<String>> tables, ResourceComparison rest) {
    }

    public Path run(MinecraftVersion older, MinecraftVersion newer, Consumer<String> log) throws Exception {
        Snapshot before = snapshot(older, log);
        Snapshot after = snapshot(newer, log);

        log.accept("comparing " + before.classes().size() + " and " + after.classes().size() + " classes");
        JarComparison classes = JarComparison.compare(before.classes(), after.classes());

        log.accept("comparing resources");
        ResourceComparison resources = ResourceComparison.compare(before.resources(), after.resources(), Set.of());

        Optional<Reports> reports = Optional.empty();
        if (before.reports() != null && after.reports() != null) {
            log.accept("comparing server reports");
            reports = Optional.of(compareReports(before.reports(), after.reports()));
        }

        log.accept("comparing decompiled sources");
        SourceComparison sources = SourceComparison.compare(new SourceTree(config.sourcesDir()), older.id(),
                newer.id(), classes);

        Path output = config.diffsDir().resolve(older.id() + ".." + newer.id());
        Directories.deleteRecursively(output);
        log.accept("writing " + output);
        new DiffReport(output, older.id(), newer.id(), before.resources(), after.resources())
                .write(classes, resources, reports, sources);
        return output;
    }

    private Snapshot snapshot(MinecraftVersion version, Consumer<String> log) throws Exception {
        String id = version.id();
        log.accept("[" + id + "] assembling classes");
        AssembledVersion assembled = new VersionPreparer(config, manifest).assemble(version);
        GameJars jars = assembled.jars();

        log.accept("[" + id + "] indexing classes and resources");
        ClassIndex classes = ClassIndex.of(assembled.classes());
        ResourceIndex resources = ResourceIndex.fromJars(
                Stream.of(jars.client(), jars.server()).filter(Objects::nonNull).toList());

        ResourceIndex reports = null;
        if (config.serverReports() && jars.serverBundle() != null) {
            log.accept("[" + id + "] running the server data generator");
            try {
                reports = ResourceIndex.fromDirectory(ServerReports.generate(jars.serverBundle(),
                        config.reportsDir(id)));
            } catch (Exception e) {
                log.accept("[" + id + "] server reports skipped: " + e.getMessage());
            }
        }
        return new Snapshot(id, classes, resources, reports);
    }

    private static Reports compareReports(ResourceIndex before, ResourceIndex after) {
        Map<String, List<String>> tables = new TreeMap<>();
        for (String name : ReportTables.SPECIAL) {
            Optional<Resource> old = before.find(name);
            Optional<Resource> updated = after.find(name);
            if (old.isPresent() && updated.isPresent() && old.get().text() != null && updated.get().text() != null) {
                tables.put(name, ReportTables.of(name, old.get().text(), updated.get().text()));
            }
        }
        return new Reports(tables, ResourceComparison.compare(before, after, ReportTables.SPECIAL));
    }
}
