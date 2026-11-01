package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.config.GeneratorConfig;
import com.deathmotion.mcsources.config.RunOptions;
import com.deathmotion.mcsources.decompile.plan.VersionPlan;
import com.deathmotion.mcsources.decompile.store.DecompileStore;
import com.deathmotion.mcsources.io.Directories;
import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.manifest.VersionManifest;
import com.deathmotion.mcsources.pipeline.generation.Decompilation;
import com.deathmotion.mcsources.pipeline.generation.Planning;
import com.deathmotion.mcsources.pipeline.generation.PreparedVersions;
import com.deathmotion.mcsources.pipeline.generation.StageClock;
import com.deathmotion.mcsources.version.VersionPreparer;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Generation {
    private final GeneratorConfig config;
    private final RunOptions options;
    private final VersionManifest manifest;

    public Generation(GeneratorConfig config, RunOptions options, VersionManifest manifest) {
        this.config = config;
        this.options = options;
        this.manifest = manifest;
    }

    public List<GenerationResult> run(List<MinecraftVersion> requested) throws IOException {
        StageClock clock = new StageClock();
        SourceTree tree = new SourceTree(config.sourcesDir());
        List<MinecraftVersion> stale = config.force() ? requested : tree.stale(requested);
        Map<String, String> failures = new ConcurrentHashMap<>();
        if (!stale.isEmpty()) {
            Console.line("generate", "%d version(s), %d preparing at once, %d decompile run(s) of %d thread(s)",
                    stale.size(), options.prepareJobs(), options.jobs(), options.decompilerThreads());
            DecompileStore store = openStore();
            PreparedVersions prepared = new PreparedVersions(new VersionPreparer(config, manifest), stale);
            List<VersionPlan> plans = clock.time("prepare and plan",
                    () -> Planning.plan(prepared, options.prepareJobs(), failures));
            clock.time("decompile", () -> Decompilation.run(plans, prepared, store, options.jobs(),
                    options.decompilerThreads(), config.logsDir(), failures));
            clock.time("write", () -> tree.write(plans, store, failures));
        }
        List<GenerationResult> results = tree.results(requested, stale, failures);
        GenerationSummary.print(results, clock);
        return results;
    }

    private DecompileStore openStore() throws IOException {
        if (!config.cacheEnabled()) {
            return DecompileStore.inMemory();
        }
        if (config.resetCache()) {
            Directories.deleteRecursively(config.decompileCacheDir());
            Console.line("generate", "decompile cache reset");
        }
        return DecompileStore.onDisk(config.decompileCacheDir());
    }
}
