package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.config.GeneratorConfig;
import com.deathmotion.mcsources.decompile.VineflowerDecompiler;
import com.deathmotion.mcsources.decompile.VineflowerOptions;
import com.deathmotion.mcsources.decompile.cache.CachedDecompile;
import com.deathmotion.mcsources.decompile.cache.DecompileCache;
import com.deathmotion.mcsources.download.Downloader;
import com.deathmotion.mcsources.download.LibraryDownloader;
import com.deathmotion.mcsources.jar.JarAssembler;
import com.deathmotion.mcsources.jar.ParameterAnnotationFixer;
import com.deathmotion.mcsources.jar.ParameterNameInjector;
import com.deathmotion.mcsources.jar.ServerBundle;
import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.manifest.VersionManifest;
import com.deathmotion.mcsources.mapping.MappingResolver;
import com.deathmotion.mcsources.mapping.MappingTier;
import com.deathmotion.mcsources.mapping.ResolvedMappings;
import com.deathmotion.mcsources.mapping.parchment.ParchmentData;
import com.deathmotion.mcsources.mapping.parchment.ParchmentResolver;
import com.deathmotion.mcsources.remap.JarRemapper;
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

public final class SourceGenerator {
    private static final String MARKER_FILE = ".mcsg-done";

    private final GeneratorConfig config;
    private final VersionManifest manifest;
    private final VineflowerDecompiler decompiler;
    private final DecompileCache decompileCache;
    private final String decompileBaseHash;

    public SourceGenerator(GeneratorConfig config, VersionManifest manifest) {
        this.config = config;
        this.manifest = manifest;
        this.decompiler = new VineflowerDecompiler(config.vineflowerHeap(), config.decompilerThreads());
        this.decompileCache = config.cacheEnabled() ? new DecompileCache(config.decompileCacheDir()) : null;
        this.decompileBaseHash = Hashing.sha256Hex(decompilerIdentity() + "\n" + VineflowerOptions.identity()
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

    private static void writeMarker(Path marker, String id, ResolvedMappings mappings, int classes, int javaFiles)
            throws IOException {
        String content = """
                version=%s
                tier=%s
                mappings=%s
                classes=%d
                javaFiles=%d
                generatedAt=%s
                """.formatted(id, mappings.tier(), mappings.detail(), classes, javaFiles, Instant.now());
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

    private static void log(String id, String message) {
        System.out.println("[" + id + "] " + message);
    }

    public GenerationResult generate(MinecraftVersion version) {
        long start = System.nanoTime();
        String id = version.id();
        Path outputDir = config.sourcesDir().resolve(id);
        Path marker = outputDir.resolve(MARKER_FILE);

        try {
            if (!config.force() && Files.exists(marker)) {
                return GenerationResult.skipped(id);
            }

            Path scratch = config.scratchDir(id);
            PathUtils.deleteRecursively(scratch);
            Files.createDirectories(scratch);

            JsonObject metadata = manifest.metadata(version);
            JsonObject downloads = metadata.getAsJsonObject("downloads");

            ResolvedMappings mappings = MappingResolver.resolve(metadata, id, scratch);

            Path parchmentJson = null;
            if (mappings.tier() == MappingTier.MOJMAP) {
                Optional<ParchmentResolver.Resolved> parchment = ParchmentResolver.resolve(id, scratch);
                if (parchment.isPresent()) {
                    parchmentJson = parchment.get().json();
                }
            }

            List<Path> libraries = LibraryDownloader.resolve(metadata, config.librariesDir());

            Path clientJar = Downloader.download(
                    downloads.getAsJsonObject("client").get("url").getAsString(), scratch.resolve("client.jar"));
            Path serverJar = null;
            if (downloads.has("server")) {
                Path rawServer = Downloader.download(
                        downloads.getAsJsonObject("server").get("url").getAsString(), scratch.resolve("server.jar"));
                serverJar = ServerBundle.extractServerJar(rawServer, scratch.resolve("server-extracted.jar"));
            }

            int remapThreads = config.decompilerThreads();
            Path clientNamed = scratch.resolve("client-named.jar");
            JarRemapper.remap(clientJar, clientNamed, mappings.clientTree(), libraries, remapThreads);
            Path serverNamed = null;
            if (serverJar != null) {
                serverNamed = scratch.resolve("server-named.jar");
                JarRemapper.remap(serverJar, serverNamed, mappings.serverTree(), libraries, remapThreads);
            }

            Path merged = scratch.resolve("minecraft.jar");
            int classes = JarAssembler.assemble(clientNamed, serverNamed, mappings.namedClasses(), merged);

            ParameterAnnotationFixer.apply(merged);

            if (mappings.tier() == MappingTier.MOJMAP) {
                ParameterNameInjector.apply(merged, parchmentJson != null ? ParchmentData.load(parchmentJson) : null);
            }

            String javadocSpec = javadocSpec(parchmentJson, mappings.javadocTiny());
            Path staging = scratch.resolve("decompiled");
            Path log = scratch.resolve("vineflower.log");
            if (decompileCache != null) {
                CachedDecompile.run(decompiler, decompileCache, decompileBaseHash,
                        merged, staging, libraries, log, javadocSpec, scratch);
            } else {
                decompiler.decompile(merged, staging, libraries, log, javadocSpec);
            }

            PathUtils.deleteRecursively(outputDir);
            Files.createDirectories(outputDir.getParent());
            Files.move(staging, outputDir);
            int javaFiles = PathUtils.countJavaFiles(outputDir);

            writeMarker(marker, id, mappings, classes, javaFiles);

            if (!config.keepWork()) {
                PathUtils.deleteRecursively(scratch);
            }

            long millis = elapsedMillis(start);
            return GenerationResult.generated(id, mappings.tier(), javaFiles, millis);
        } catch (Exception e) {
            log(id, "failed: " + e);
            return GenerationResult.failed(id, String.valueOf(e), elapsedMillis(start));
        }
    }
}
