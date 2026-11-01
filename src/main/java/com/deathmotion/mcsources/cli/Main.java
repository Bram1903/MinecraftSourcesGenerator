package com.deathmotion.mcsources.cli;

import com.deathmotion.mcsources.config.Eula;
import com.deathmotion.mcsources.config.GeneratorConfig;
import com.deathmotion.mcsources.config.RunOptions;
import com.deathmotion.mcsources.diff.VersionDiff;
import com.deathmotion.mcsources.download.LibraryDownloader;
import com.deathmotion.mcsources.ide.IdeWorkspace;
import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.manifest.VersionManifest;
import com.deathmotion.mcsources.mapping.MappingPlan;
import com.deathmotion.mcsources.mapping.MappingResolver;
import com.deathmotion.mcsources.mapping.MappingTier;
import com.deathmotion.mcsources.pipeline.Generation;
import com.deathmotion.mcsources.pipeline.GenerationOutcome;
import com.deathmotion.mcsources.pipeline.GenerationResult;
import com.deathmotion.mcsources.pipeline.SourceTree;
import com.deathmotion.mcsources.pipeline.Verification;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class Main {
    private static final String UNSET = "<unset>";

    private Main() {
    }

    static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println("Usage: <list | one <version> | all | ide <version> | diff <old> <new> | verify <version>>");
            System.exit(2);
            return;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(Main::stopChildProcesses, "mcsg-shutdown"));
        try {
            RunOptions options = RunOptions.fromSystemProperties();
            GeneratorConfig config = GeneratorConfig.fromSystemProperties();
            VersionManifest manifest = new VersionManifest(config.cacheDir());
            VersionSelection selection = new VersionSelection(manifest, options);
            int exitCode = switch (args[0]) {
                case "list" -> list(manifest, options);
                case "one" -> generate(config, options, manifest, selection.parse(argument(args, 1,
                        "./gradlew generateVersion -Pmc=<version|from-to>   (e.g. -Pmc=1.16.5 or -Pmc=1.16.5-1.18)")));
                case "all" -> generate(config, options, manifest, selection.all());
                case "ide" -> ide(config, options, manifest, argument(args, 1,
                        "./gradlew ide -Pmc=<version>   (e.g. -Pmc=1.21.8)"));
                case "diff" -> diff(config, manifest, argument(args, 1, diffUsage()), argument(args, 2, diffUsage()));
                case "verify" -> verify(config, options, manifest, selection.parse(argument(args, 1,
                        "./gradlew verifySources -Pmc=<version|from-to>")));
                default -> throw new IllegalArgumentException("Unknown mode: " + args[0]);
            };
            System.exit(exitCode);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
        }
    }

    private static String argument(String[] args, int index, String usage) {
        if (args.length <= index || args[index].isBlank() || args[index].equals(UNSET)) {
            throw new IllegalArgumentException("Usage: " + usage);
        }
        return args[index];
    }

    private static String diffUsage() {
        return "./gradlew diffVersions -Pold=<version> -Pnew=<version>   (e.g. -Pold=26.2 -Pnew=26.3)";
    }

    private static void stopChildProcesses() {
        ProcessHandle.current().descendants().forEach(ProcessHandle::destroyForcibly);
    }

    private static int list(VersionManifest manifest, RunOptions options) throws Exception {
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
        return 0;
    }

    private static int generate(GeneratorConfig config, RunOptions options, VersionManifest manifest,
                                List<MinecraftVersion> versions) throws Exception {
        if (!Eula.fromSystemProperties().ensureAccepted()) {
            return 3;
        }
        List<GenerationResult> results = new Generation(config, options.sizedFor(versions.size()), manifest)
                .run(versions);
        return results.stream().anyMatch(result -> result.outcome() == GenerationOutcome.FAILED) ? 1 : 0;
    }

    private static int ide(GeneratorConfig config, RunOptions options, VersionManifest manifest, String id)
            throws Exception {
        MinecraftVersion version = manifest.find(id);
        SourceTree tree = new SourceTree(config.sourcesDir());
        Path sources = tree.directory(id);
        if (!tree.isCurrent(id)) {
            int generated = generate(config, options, manifest, List.of(version));
            if (generated != 0) {
                return generated;
            }
        }
        List<Path> libraries = LibraryDownloader.resolve(manifest.metadata(version), config.librariesDir());
        Path workspace = config.workspaceDir().toAbsolutePath();
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
        return 0;
    }

    private static int diff(GeneratorConfig config, VersionManifest manifest, String older, String newer)
            throws Exception {
        if (!Eula.fromSystemProperties().ensureAccepted()) {
            return 3;
        }
        Path output = new VersionDiff(config, manifest)
                .run(manifest.find(older), manifest.find(newer), System.out::println);
        System.out.println();
        System.out.println("Diff written to " + output);
        System.out.println("Start with " + output.resolve("summary.md"));
        return 0;
    }

    private static int verify(GeneratorConfig config, RunOptions options, VersionManifest manifest,
                              List<MinecraftVersion> versions) throws Exception {
        if (!Eula.fromSystemProperties().ensureAccepted()) {
            return 3;
        }
        return new Verification(config, options.sizedFor(1), manifest).verify(versions) ? 0 : 1;
    }
}
