package com.deathmotion.mcsources.cli;

import com.deathmotion.mcsources.config.Eula;
import com.deathmotion.mcsources.config.GeneratorConfig;
import com.deathmotion.mcsources.config.RunOptions;
import com.deathmotion.mcsources.download.LibraryDownloader;
import com.deathmotion.mcsources.ide.IdeWorkspace;
import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.manifest.VersionManifest;
import com.deathmotion.mcsources.mapping.MappingPlan;
import com.deathmotion.mcsources.mapping.MappingResolver;
import com.deathmotion.mcsources.mapping.MappingTier;
import com.deathmotion.mcsources.pipeline.GenerationOutcome;
import com.deathmotion.mcsources.pipeline.GenerationResult;
import com.deathmotion.mcsources.pipeline.GenerationSummary;
import com.deathmotion.mcsources.pipeline.ProgressTracker;
import com.deathmotion.mcsources.pipeline.SourceGenerator;
import com.deathmotion.mcsources.util.PathUtils;
import com.google.gson.JsonObject;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class Main {
    private static final long HEARTBEAT_SECONDS = 15;

    private Main() {
    }

    static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: <list | one <version> | all>");
            System.exit(2);
            return;
        }

        RunOptions options = RunOptions.fromSystemProperties();
        GeneratorConfig config = GeneratorConfig.fromSystemProperties(options.decompilerThreads());
        VersionManifest manifest = new VersionManifest(config.cacheDir());

        switch (args[0]) {
            case "list" -> listVersions(manifest, options);
            case "one" -> generate(config, manifest, options, args);
            case "all" -> generateAll(config, manifest, options);
            case "ide" -> ide(config, manifest, args);
            default -> {
                System.err.println("Unknown mode: " + args[0]);
                System.exit(2);
            }
        }
    }

    private static void listVersions(VersionManifest manifest, RunOptions options) throws Exception {
        List<MinecraftVersion> versions = manifest.versionsSinceBaseline(options.includeSnapshots());
        System.out.printf("%-12s %-12s %-10s %s%n", "VERSION", "TYPE", "DATE", "MAPPINGS");
        Map<MappingTier, Integer> counts = new EnumMap<>(MappingTier.class);
        for (MinecraftVersion version : versions) {
            MappingPlan plan = MappingResolver.plan(manifest.metadata(version), version.id());
            counts.merge(plan.tier(), 1, Integer::sum);
            System.out.printf("%-12s %-12s %-10s %s%n",
                    version.id(), version.type(), version.releaseTime().toString().substring(0, 10), plan.detail());
        }
        System.out.println();
        System.out.println("Total: " + versions.size() + " versions");
        counts.forEach((tier, count) -> System.out.println("  " + tier + ": " + count));
    }

    private static void generate(GeneratorConfig config, VersionManifest manifest, RunOptions options, String[] args)
            throws Exception {
        if (args.length < 2 || args[1].isBlank() || args[1].equals("<unset>")) {
            System.err.println("Usage: ./gradlew generateVersion -Pmc=<version|from-to>"
                    + "   (e.g. -Pmc=1.16.5 or -Pmc=1.16.5-1.18)");
            System.exit(2);
            return;
        }
        if (!requireEula()) {
            System.exit(3);
            return;
        }
        List<MinecraftVersion> versions = largestFirst(resolveSelection(args[1], manifest, options), manifest);
        List<GenerationResult> results = runGeneration(config, manifest, options, versions);
        if (results.stream().anyMatch(result -> result.outcome() == GenerationOutcome.FAILED)) {
            System.exit(1);
        }
    }

    private static List<MinecraftVersion> resolveSelection(String spec, VersionManifest manifest, RunOptions options)
            throws Exception {
        int dash = spec.indexOf('-');
        if (dash > 0 && dash < spec.length() - 1) {
            List<MinecraftVersion> inRange = manifest.versionsSinceBaseline(options.includeSnapshots());
            int low = indexOfId(inRange, spec.substring(0, dash));
            int high = indexOfId(inRange, spec.substring(dash + 1));
            if (low >= 0 && high >= 0) {
                return new ArrayList<>(inRange.subList(Math.min(low, high), Math.max(low, high) + 1));
            }
        }
        return List.of(manifest.find(spec));
    }

    private static int indexOfId(List<MinecraftVersion> versions, String id) {
        for (int i = 0; i < versions.size(); i++) {
            if (versions.get(i).id().equals(id)) {
                return i;
            }
        }
        return -1;
    }

    private static void ide(GeneratorConfig config, VersionManifest manifest, String[] args) throws Exception {
        if (args.length < 2 || args[1].isBlank() || args[1].equals("<unset>")) {
            System.err.println("Usage: ./gradlew ide -Pmc=<version>   (e.g. -Pmc=1.21.8)");
            System.exit(2);
            return;
        }
        String id = args[1];
        MinecraftVersion version = manifest.find(id);
        Path sources = config.sourcesDir().resolve(id);

        if (!Files.exists(sources.resolve(".mcsg-done"))) {
            if (!requireEula()) {
                System.exit(3);
                return;
            }
            GenerationResult result = new SourceGenerator(config, manifest).generate(version,
                    phase -> System.out.println("[" + id + "] " + phase.label()));
            if (result.outcome() == GenerationOutcome.FAILED) {
                System.exit(1);
                return;
            }
        }

        List<Path> libraries = LibraryDownloader.resolve(manifest.metadata(version), config.librariesDir());
        Path workspace = config.sourcesDir().resolveSibling("workspace").toAbsolutePath();
        IdeWorkspace.write(workspace, id, sources, libraries);

        System.out.println();
        System.out.println("IDE workspace ready for Minecraft " + id + ".");
        System.out.println();
        System.out.println("Open ONLY this folder as its own project (not the generator repo):");
        System.out.println("  " + workspace);
        System.out.println();
        System.out.println("IntelliJ: File > Open, select that folder, choose 'Open in New Window'.");
        System.out.println("It shows just Minecraft " + id + " with go-to-definition, find-usages and references.");
        System.out.println("Re-run './gradlew ide -Pmc=<version>' to switch the workspace to another version.");
    }

    private static void generateAll(GeneratorConfig config, VersionManifest manifest, RunOptions options)
            throws Exception {
        if (!requireEula()) {
            System.exit(3);
            return;
        }
        List<MinecraftVersion> versions =
                largestFirst(select(manifest.versionsSinceBaseline(options.includeSnapshots()), options), manifest);
        runGeneration(config, manifest, options, versions);
    }

    private static List<GenerationResult> runGeneration(GeneratorConfig config, VersionManifest manifest,
                                                        RunOptions options, List<MinecraftVersion> versions)
            throws Exception {
        if (System.getProperty("mcsg.resetCache") != null && config.cacheEnabled()) {
            PathUtils.deleteRecursively(config.decompileCacheDir());
            System.out.println("Decompile cache reset.");
        }
        System.out.println("Generating " + versions.size() + " version(s) with " + options.jobs()
                + " parallel job(s), " + options.decompilerThreads() + " decompiler thread(s) each.");
        System.out.println();

        SourceGenerator generator = new SourceGenerator(config, manifest);
        ProgressTracker tracker = new ProgressTracker();
        long start = System.nanoTime();
        int total = versions.size();
        AtomicInteger completed = new AtomicInteger();
        AtomicInteger inProgress = new AtomicInteger();
        List<GenerationResult> results = new ArrayList<>();

        ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mcsg-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        AtomicInteger lastBeatDone = new AtomicInteger(0);
        heartbeat.scheduleAtFixedRate(() -> {
            int done = completed.get();
            if (done >= total || lastBeatDone.getAndSet(done) != done) {
                return;
            }
            long elapsed = (System.nanoTime() - start) / 1_000_000_000L;
            String summary = tracker.summary();
            String detail = summary.isEmpty() ? "" : " | " + summary;
            System.out.printf("[status] %ds elapsed, %d/%d done, %d running%s%n",
                    elapsed, done, total, inProgress.get(), detail);
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);

        ExecutorService pool = Executors.newFixedThreadPool(options.jobs());
        try {
            List<Future<GenerationResult>> futures = new ArrayList<>();
            for (MinecraftVersion version : versions) {
                Callable<GenerationResult> task = () -> {
                    inProgress.incrementAndGet();
                    try {
                        GenerationResult result =
                                generator.generate(version, phase -> tracker.enter(version.id(), phase));
                        reportProgress(completed.incrementAndGet(), total, start, result);
                        return result;
                    } finally {
                        tracker.finish(version.id());
                        inProgress.decrementAndGet();
                    }
                };
                futures.add(pool.submit(task));
            }
            for (Future<GenerationResult> future : futures) {
                results.add(future.get());
            }
        } finally {
            pool.shutdown();
            heartbeat.shutdownNow();
        }

        GenerationSummary.print(results, (System.nanoTime() - start) / 1_000_000);
        return results;
    }

    private static List<MinecraftVersion> select(List<MinecraftVersion> versions, RunOptions options) {
        List<MinecraftVersion> selected = new ArrayList<>();
        boolean active = options.from() == null;
        for (MinecraftVersion version : versions) {
            if (!active && version.id().equals(options.from())) {
                active = true;
            }
            if (active) {
                selected.add(version);
            }
            if (options.to() != null && version.id().equals(options.to())) {
                break;
            }
        }
        if (options.limit() >= 0 && selected.size() > options.limit()) {
            return selected.subList(0, options.limit());
        }
        return selected;
    }

    private static void reportProgress(int done, int total, long startNanos, GenerationResult result) {
        long elapsedSeconds = (System.nanoTime() - startNanos) / 1_000_000_000L;
        long remainingSeconds = done == 0 ? 0 : elapsedSeconds / done * (total - done);
        System.out.printf("[progress] %d/%d (%d%%) %s, %ds elapsed, ~%ds remaining%n",
                done, total, done * 100 / total, outcomeLabel(result), elapsedSeconds, remainingSeconds);
    }

    private static String outcomeLabel(GenerationResult result) {
        return switch (result.outcome()) {
            case GENERATED -> result.versionId() + " done in " + (result.millis() / 1000) + "s";
            case SKIPPED -> result.versionId() + " skipped";
            case FAILED -> result.versionId() + " FAILED";
        };
    }

    private static List<MinecraftVersion> largestFirst(List<MinecraftVersion> versions, VersionManifest manifest) {
        Map<String, Long> sizes = new HashMap<>();
        for (MinecraftVersion version : versions) {
            long size = 0;
            try {
                JsonObject downloads = manifest.metadata(version).getAsJsonObject("downloads");
                size = jarSize(downloads, "client") + jarSize(downloads, "server");
            } catch (Exception ignored) {
            }
            sizes.put(version.id(), size);
        }
        List<MinecraftVersion> ordered = new ArrayList<>(versions);
        ordered.sort(Comparator.comparingLong((MinecraftVersion v) -> sizes.getOrDefault(v.id(), 0L)).reversed());
        return ordered;
    }

    private static long jarSize(JsonObject downloads, String key) {
        return downloads.has(key) ? downloads.getAsJsonObject(key).get("size").getAsLong() : 0L;
    }

    private static boolean requireEula() throws Exception {
        return Eula.fromSystemProperties().ensureAccepted();
    }
}
