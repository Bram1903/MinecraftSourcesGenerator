package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.config.GeneratorConfig;
import com.deathmotion.mcsources.decompile.DecompileSettings;
import com.deathmotion.mcsources.decompile.VineflowerDecompiler;
import com.deathmotion.mcsources.decompile.VineflowerOptions;
import com.deathmotion.mcsources.decompile.cache.CachedDecompile;
import com.deathmotion.mcsources.decompile.cache.DecompileCache;
import com.deathmotion.mcsources.jar.ParameterAnnotationFixer;
import com.deathmotion.mcsources.jar.ParameterNameInjector;
import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.manifest.VersionManifest;
import com.deathmotion.mcsources.mapping.MappingResolver;
import com.deathmotion.mcsources.mapping.MappingTier;
import com.deathmotion.mcsources.mapping.ResolvedMappings;
import com.deathmotion.mcsources.mapping.parchment.ParchmentData;
import com.deathmotion.mcsources.mapping.parchment.ParchmentResolver;
import com.deathmotion.mcsources.util.Hashing;
import com.deathmotion.mcsources.util.PathUtils;
import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class SourceGenerator {
    public static final String MARKER_FILE = ".mcsg-done";
    public static final int OUTPUT_REVISION = 2;

    private static final String REVISION_KEY = "revision=";

    private final GeneratorConfig config;
    private final VersionManifest manifest;
    private final VineflowerDecompiler decompiler;
    private final DecompileCache decompileCache;

    public SourceGenerator(GeneratorConfig config, VersionManifest manifest) {
        this.config = config;
        this.manifest = manifest;
        this.decompiler = new VineflowerDecompiler(config.vineflowerHeap(), config.decompilerThreads());
        this.decompileCache = config.cacheEnabled() ? new DecompileCache(config.decompileCacheDir()) : null;
    }

    public static boolean isCurrent(Path versionDir) throws IOException {
        Path marker = versionDir.resolve(MARKER_FILE);
        if (!Files.exists(marker)) {
            return false;
        }
        return Files.readAllLines(marker).contains(REVISION_KEY + OUTPUT_REVISION);
    }

    private static String decompileBaseHash(boolean renameLocals) {
        return Hashing.sha256Hex(decompilerIdentity() + "\n" + VineflowerOptions.identity(renameLocals)
                + "\ncache-format:" + DecompileCache.FORMAT);
    }

    private static String decompilerIdentity() {
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            String name = Path.of(entry).getFileName().toString();
            if (name.contains("vineflower")) {
                return name;
            }
        }
        return "vineflower";
    }

    private static void writeMarker(Path marker, String id, ResolvedMappings mappings, String parchment, int classes,
                                    int javaFiles) throws IOException {
        String content = """
                version=%s
                %s%d
                tier=%s
                mappings=%s
                parchment=%s
                decompiler=%s
                classes=%d
                javaFiles=%d
                generatedAt=%s
                """.formatted(id, REVISION_KEY, OUTPUT_REVISION, mappings.tier(), mappings.detail(), parchment,
                decompilerIdentity(), classes, javaFiles, Instant.now());
        Files.writeString(marker, content);
    }

    private static String javadocSpec(Path parchmentJson, Path javadocTiny) {
        if (parchmentJson != null) {
            return "parchment:" + parchmentJson;
        }
        if (javadocTiny != null) {
            return "tiny:" + javadocTiny;
        }
        return "none";
    }

    private static long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private static String describe(Throwable failure) {
        StringBuilder builder = new StringBuilder(String.valueOf(failure));
        for (Throwable cause = failure.getCause(); cause != null && cause != failure; cause = cause.getCause()) {
            builder.append(" <- ").append(cause);
        }
        return builder.toString();
    }

    private static void log(String id, String message) {
        System.out.println("[" + id + "] " + message);
    }

    public GenerationResult generate(MinecraftVersion version, Consumer<Phase> onPhase) {
        long start = System.nanoTime();
        String id = version.id();
        Path outputDir = config.sourcesDir().resolve(id);
        Path marker = outputDir.resolve(MARKER_FILE);

        try {
            if (!config.force() && isCurrent(outputDir)) {
                return GenerationResult.skipped(id);
            }

            Path scratch = config.scratchDir(id);
            PathUtils.deleteRecursively(scratch);
            Files.createDirectories(scratch);

            JsonObject metadata = manifest.metadata(version);

            onPhase.accept(Phase.MAPPINGS);
            ResolvedMappings mappings = MappingResolver.resolve(metadata, id, scratch);

            Path parchmentJson = null;
            String parchmentVersion = "none";
            if (mappings.tier() == MappingTier.MOJMAP) {
                List<String> releases = manifest.versionsSinceBaseline(false).stream()
                        .map(MinecraftVersion::id)
                        .toList();
                Optional<ParchmentResolver.Resolved> parchment = ParchmentResolver.resolve(id, releases, scratch);
                if (parchment.isPresent()) {
                    parchmentJson = parchment.get().json();
                    parchmentVersion = parchment.get().describe(id);
                }
            }

            VersionJars jars = VersionJars.assemble(config, metadata, mappings, scratch, onPhase);
            List<Path> libraries = jars.libraries();
            Path merged = jars.merged();
            int classes = jars.classes();

            onPhase.accept(Phase.TRANSFORM);
            ParameterAnnotationFixer.apply(merged);

            if (mappings.tier() == MappingTier.MOJMAP) {
                ParameterNameInjector.apply(merged, parchmentJson != null ? ParchmentData.load(parchmentJson) : null);
            }

            onPhase.accept(Phase.DECOMPILE);
            boolean renameLocals = !mappings.tier().shipsLocalNames();
            DecompileSettings settings =
                    new DecompileSettings(javadocSpec(parchmentJson, mappings.javadocTiny()), renameLocals);
            Path staging = scratch.resolve("decompiled");
            Path log = scratch.resolve("vineflower.log");
            if (decompileCache != null) {
                CachedDecompile.run(decompiler, decompileCache, decompileBaseHash(renameLocals),
                        merged, staging, libraries, log, settings, scratch);
            } else {
                decompiler.decompile(merged, staging, libraries, log, settings);
            }

            onPhase.accept(Phase.WRITE);
            PathUtils.deleteRecursively(outputDir);
            Files.createDirectories(outputDir.getParent());
            Files.move(staging, outputDir);
            int javaFiles = PathUtils.countJavaFiles(outputDir);

            writeMarker(marker, id, mappings, parchmentVersion, classes, javaFiles);

            if (!config.keepWork()) {
                PathUtils.deleteRecursively(scratch);
            }

            long millis = elapsedMillis(start);
            return GenerationResult.generated(id, mappings.tier(), javaFiles, millis);
        } catch (Exception e) {
            log(id, "failed: " + describe(e));
            return GenerationResult.failed(id, describe(e), elapsedMillis(start));
        }
    }
}
