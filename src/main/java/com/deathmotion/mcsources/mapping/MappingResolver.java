package com.deathmotion.mcsources.mapping;

import com.deathmotion.mcsources.download.Downloader;
import com.google.gson.JsonObject;
import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.adapter.MappingSourceNsSwitch;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.format.proguard.ProGuardFileReader;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class MappingResolver {
    private static final String ORNITHE_REPOSITORY = "https://maven.ornithemc.net/releases";
    private static final Pattern VERSION_ELEMENT = Pattern.compile("<version>([^<]+)</version>");
    private static final Map<String, String> METADATA_CACHE = new ConcurrentHashMap<>();

    private MappingResolver() {
    }

    public static MappingPlan plan(JsonObject metadata, String versionId) throws IOException {
        if (metadata.getAsJsonObject("downloads").has("client_mappings")) {
            return new MappingPlan(MappingTier.MOJMAP, "mojang official", null);
        }
        MappingArtifact feather = locateMergedV2(
                ORNITHE_REPOSITORY, "net/ornithemc/feather", "feather", versionId, MappingTier.ORNITHE_FEATHER);
        if (feather != null) {
            return new MappingPlan(MappingTier.ORNITHE_FEATHER, "ornithe feather " + feather.version(), feather);
        }
        return new MappingPlan(MappingTier.VANILLA, "unobfuscated vanilla (no mappings needed, MC 26.1+)", null);
    }

    public static ResolvedMappings resolve(JsonObject metadata, String versionId, Path workDir) throws IOException {
        MappingPlan plan = plan(metadata, versionId);
        return switch (plan.tier()) {
            case MOJMAP -> resolveMojang(metadata, plan, workDir);
            case ORNITHE_FEATHER -> resolveMergedV2(plan, workDir);
            case VANILLA -> new ResolvedMappings(MappingTier.VANILLA, plan.detail(), null, null, null, null);
        };
    }

    private static ResolvedMappings resolveMojang(JsonObject metadata, MappingPlan plan, Path workDir)
            throws IOException {
        JsonObject downloads = metadata.getAsJsonObject("downloads");
        Path clientProguard = Downloader.download(
                downloads.getAsJsonObject("client_mappings").get("url").getAsString(),
                workDir.resolve("client_mappings.txt"));
        Path serverProguard = Downloader.download(
                downloads.getAsJsonObject("server_mappings").get("url").getAsString(),
                workDir.resolve("server_mappings.txt"));

        MemoryMappingTree clientTree = readProguardAsObfuscatedSource(clientProguard);
        MemoryMappingTree serverTree = readProguardAsObfuscatedSource(serverProguard);

        Set<String> namedClasses = new HashSet<>();
        collectNamedClasses(clientTree, namedClasses);
        collectNamedClasses(serverTree, namedClasses);

        return new ResolvedMappings(MappingTier.MOJMAP, plan.detail(), clientTree, serverTree, namedClasses, null);
    }

    private static ResolvedMappings resolveMergedV2(MappingPlan plan, Path workDir) throws IOException {
        MappingArtifact artifact = plan.artifact();
        Path jar = Downloader.download(artifact.url(), workDir.resolve(artifact.fileName()));
        Path tiny = extractEntry(jar, "mappings/mappings.tiny", workDir.resolve("mappings.tiny"));

        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(tiny, MappingFormat.TINY_2_FILE, tree);
        if (!ResolvedMappings.OBFUSCATED_NAMESPACE.equals(tree.getSrcNamespace())) {
            MemoryMappingTree switched = new MemoryMappingTree();
            tree.accept(new MappingSourceNsSwitch(switched, ResolvedMappings.OBFUSCATED_NAMESPACE));
            tree = switched;
        }

        Set<String> namedClasses = new HashSet<>();
        collectNamedClasses(tree, namedClasses);

        return new ResolvedMappings(plan.tier(), plan.detail(), tree, tree, namedClasses, tiny);
    }

    private static MemoryMappingTree readProguardAsObfuscatedSource(Path proguard) throws IOException {
        MemoryMappingTree namedSource = new MemoryMappingTree();
        try (Reader reader = Files.newBufferedReader(proguard)) {
            ProGuardFileReader.read(reader,
                    ResolvedMappings.NAMED_NAMESPACE, ResolvedMappings.OBFUSCATED_NAMESPACE, namedSource);
        }
        MemoryMappingTree obfuscatedSource = new MemoryMappingTree();
        namedSource.accept(new MappingSourceNsSwitch(obfuscatedSource, ResolvedMappings.OBFUSCATED_NAMESPACE));
        return obfuscatedSource;
    }

    private static void collectNamedClasses(MemoryMappingTree tree, Set<String> out) {
        for (MappingTree.ClassMapping mapping : tree.getClasses()) {
            String named = mapping.getName(ResolvedMappings.NAMED_NAMESPACE);
            if (named != null && !named.isEmpty()) {
                out.add(named);
            }
        }
    }

    private static MappingArtifact locateMergedV2(String repository, String groupPath, String artifact,
                                                  String versionId, MappingTier tier) throws IOException {
        String metadataUrl = repository + "/" + groupPath + "/maven-metadata.xml";
        String xml = METADATA_CACHE.computeIfAbsent(metadataUrl, url -> {
            try {
                return Downloader.getString(url);
            } catch (IOException e) {
                return "";
            }
        });

        String prefix = versionId + "+build.";
        String best = null;
        int bestBuild = -1;
        Matcher matcher = VERSION_ELEMENT.matcher(xml);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (candidate.startsWith(prefix)) {
                try {
                    int build = Integer.parseInt(candidate.substring(prefix.length()));
                    if (build > bestBuild) {
                        bestBuild = build;
                        best = candidate;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (best == null) {
            return null;
        }
        String url = repository + "/" + groupPath + "/" + best + "/" + artifact + "-" + best + "-mergedv2.jar";
        return new MappingArtifact(tier, best, url);
    }

    private static Path extractEntry(Path jar, String entryName, Path destination) throws IOException {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry(entryName);
            if (entry == null) {
                throw new IOException("Entry " + entryName + " not found in " + jar);
            }
            Files.createDirectories(destination.getParent());
            try (InputStream in = zip.getInputStream(entry)) {
                Files.copy(in, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        return destination;
    }
}
