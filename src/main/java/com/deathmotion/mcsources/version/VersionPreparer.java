package com.deathmotion.mcsources.version;

import com.deathmotion.mcsources.config.GeneratorConfig;
import com.deathmotion.mcsources.decompile.javadoc.JavadocSource;
import com.deathmotion.mcsources.download.LibraryDownloader;
import com.deathmotion.mcsources.manifest.MinecraftVersion;
import com.deathmotion.mcsources.manifest.VersionManifest;
import com.deathmotion.mcsources.mapping.MappingResolver;
import com.deathmotion.mcsources.mapping.MappingTier;
import com.deathmotion.mcsources.mapping.ResolvedMappings;
import com.deathmotion.mcsources.mapping.parchment.ParchmentData;
import com.deathmotion.mcsources.mapping.parchment.ParchmentResolver;
import com.deathmotion.mcsources.version.transform.ClassTransforms;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class VersionPreparer {
    private static final int REMAP_THREADS = 4;

    private final GeneratorConfig config;
    private final VersionManifest manifest;

    public VersionPreparer(GeneratorConfig config, VersionManifest manifest) {
        this.config = config;
        this.manifest = manifest;
    }

    public AssembledVersion assemble(MinecraftVersion version) throws IOException {
        Path downloads = config.downloadsDir(version.id());
        Files.createDirectories(downloads);
        JsonObject metadata = manifest.metadata(version);
        ResolvedMappings mappings = MappingResolver.resolve(metadata, version.id(), downloads);
        List<Path> libraries = LibraryDownloader.resolve(metadata, config.librariesDir());
        GameJars jars = GameJars.download(metadata.getAsJsonObject("downloads"), downloads);

        Map<String, byte[]> client = JarRemapper.remap(jars.client(), mappings.clientTree(), libraries, REMAP_THREADS);
        Map<String, byte[]> server = jars.server() == null
                ? Map.of()
                : JarRemapper.remap(jars.server(), mappings.serverTree(), libraries, REMAP_THREADS);
        return new AssembledVersion(version, mappings, jars,
                MergedClasses.of(client, server, mappings.namedClasses()), libraries);
    }

    public PreparedVersion prepare(MinecraftVersion version) throws IOException {
        AssembledVersion assembled = assemble(version);
        ResolvedMappings mappings = assembled.mappings();
        Optional<ParchmentResolver.Resolved> parchment = mappings.tier() == MappingTier.MOJMAP
                ? ParchmentResolver.resolve(version.id(), releaseIds(), config.downloadsDir(version.id()))
                : Optional.empty();
        ParchmentData parchmentData = parchment.isPresent() ? ParchmentData.load(parchment.get().json()) : null;
        return new PreparedVersion(version.id(), mappings.tier(), mappings.detail(),
                parchment.map(resolved -> resolved.describe(version.id())).orElse("none"),
                ClassTransforms.apply(assembled.classes(), mappings.tier(), parchmentData),
                assembled.libraries(),
                javadocs(parchmentData, mappings.javadocTiny()));
    }

    private List<String> releaseIds() throws IOException {
        return manifest.versionsSinceBaseline(false).stream().map(MinecraftVersion::id).toList();
    }

    private static JavadocSource javadocs(ParchmentData parchment, Path tiny) throws IOException {
        if (parchment != null) {
            return JavadocSource.parchment(parchment);
        }
        return tiny == null ? null : JavadocSource.tiny(tiny);
    }
}
