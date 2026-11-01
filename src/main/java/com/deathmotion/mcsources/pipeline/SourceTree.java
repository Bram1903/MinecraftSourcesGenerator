package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.classfile.InternalNames;
import com.deathmotion.mcsources.decompile.plan.ClassGroup;
import com.deathmotion.mcsources.decompile.plan.VersionPlan;
import com.deathmotion.mcsources.decompile.store.DecompileStore;
import com.deathmotion.mcsources.decompile.vineflower.VineflowerRun;
import com.deathmotion.mcsources.io.Directories;
import com.deathmotion.mcsources.manifest.MinecraftVersion;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class SourceTree {
    public static final int OUTPUT_REVISION = 3;

    private static final String MARKER_FILE = ".mcsg-done";
    private static final String REVISION_KEY = "revision=";

    private final Path root;
    private final Map<String, Integer> written = new ConcurrentHashMap<>();
    private final Map<String, VersionPlan> plans = new ConcurrentHashMap<>();

    public SourceTree(Path root) {
        this.root = root;
    }

    public Path directory(String versionId) {
        return root.resolve(versionId);
    }

    public boolean isCurrent(String versionId) {
        Path marker = directory(versionId).resolve(MARKER_FILE);
        try {
            return Files.exists(marker) && Files.readAllLines(marker).contains(REVISION_KEY + OUTPUT_REVISION);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static String relativePath(String group, String file) {
        String packagePath = InternalNames.packageOf(group);
        return packagePath.isEmpty() ? file : packagePath + "/" + file;
    }

    List<MinecraftVersion> stale(List<MinecraftVersion> versions) {
        return versions.stream().filter(version -> !isCurrent(version.id())).toList();
    }

    void write(List<VersionPlan> versionPlans, DecompileStore store, Map<String, String> failures) {
        versionPlans.parallelStream()
                .filter(plan -> !failures.containsKey(plan.id()))
                .forEach(plan -> {
                    try {
                        written.put(plan.id(), write(plan, store));
                        plans.put(plan.id(), plan);
                    } catch (IOException | RuntimeException e) {
                        failures.put(plan.id(), Console.describe(e));
                    }
                });
    }

    List<GenerationResult> results(List<MinecraftVersion> requested, List<MinecraftVersion> stale,
                                   Map<String, String> failures) {
        List<GenerationResult> results = new ArrayList<>();
        for (MinecraftVersion version : requested) {
            String id = version.id();
            if (failures.containsKey(id)) {
                results.add(GenerationResult.failed(id, failures.get(id)));
            } else if (written.containsKey(id)) {
                results.add(GenerationResult.generated(id, plans.get(id).tier(), written.get(id)));
            } else if (!stale.contains(version)) {
                results.add(GenerationResult.skipped(id));
            } else {
                results.add(GenerationResult.failed(id, "was neither prepared nor written"));
            }
        }
        return results;
    }

    private int write(VersionPlan plan, DecompileStore store) throws IOException {
        Path target = directory(plan.id());
        Path staging = root.resolve("." + plan.id() + ".partial");
        Directories.deleteRecursively(staging);
        int files = 0;
        for (Map.Entry<ClassGroup, String> entry : plan.keys().entrySet()) {
            Map<String, String> bundle = store.get(entry.getValue());
            if (bundle == null) {
                throw new IOException("No decompiled source for " + entry.getKey().name()
                        + ", every version that could decompile it failed");
            }
            for (Map.Entry<String, String> file : bundle.entrySet()) {
                Path path = staging.resolve(relativePath(entry.getKey().name(), file.getKey()));
                Files.createDirectories(path.getParent());
                Files.writeString(path, file.getValue(), StandardCharsets.UTF_8);
                files++;
            }
        }
        Directories.deleteRecursively(target);
        Files.move(staging, target);
        writeMarker(target.resolve(MARKER_FILE), plan, files);
        return files;
    }

    private static void writeMarker(Path marker, VersionPlan plan, int javaFiles) throws IOException {
        Files.writeString(marker, """
                version=%s
                %s%d
                tier=%s
                mappings=%s
                parchment=%s
                decompiler=%s
                classes=%d
                javaFiles=%d
                generatedAt=%s
                """.formatted(plan.id(), REVISION_KEY, OUTPUT_REVISION, plan.tier(), plan.mappings(),
                plan.parchment(), VineflowerRun.decompiler(), plan.classes(), javaFiles, Instant.now()));
    }
}
