package com.deathmotion.mcsources.decompile.cache;

import com.deathmotion.mcsources.decompile.JavadocSource;
import com.deathmotion.mcsources.decompile.VineflowerDecompiler;
import com.deathmotion.mcsources.util.Hashing;
import com.deathmotion.mcsources.util.PathUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class CachedDecompile {
    private CachedDecompile() {
    }

    public static Stats run(VineflowerDecompiler decompiler, DecompileCache cache, String baseHash, Path mergedJar,
                            Path stagingDir, List<Path> libraries, Path logFile, String javadocSpec, Path scratch)
            throws IOException, InterruptedException {
        JavadocSource javadocSource = JavadocSource.fromSpec(javadocSpec);

        List<ClassEntry> missEntries = new ArrayList<>();
        List<Hit> hits = new ArrayList<>();
        Map<String, String> missNameToHash = new HashMap<>();
        Map<String, String> allNameToHash = new HashMap<>();

        boolean partial;
        Path incompleteJar = scratch.resolve("cache-incomplete.jar");
        Path existingJar = scratch.resolve("cache-existing.jar");

        try (FileSystem mergedFs = FileSystems.newFileSystem(mergedJar, Map.<String, String>of())) {
            Path root = mergedFs.getPath("/");
            JarIndex.Result index = JarIndex.index(root);

            Map<String, ClassEntry> entryByTopLevel = new HashMap<>();
            Map<String, String> rawByTopLevel = new HashMap<>();
            for (ClassEntry entry : index.entries()) {
                rawByTopLevel.put(entry.name(), entry.rawHash(root));
                entryByTopLevel.put(entry.name(), entry);
            }

            for (ClassEntry entry : index.entries()) {
                String javadoc = JavadocDigest.of(javadocSource, index.membersByTopLevel().get(entry.name()));
                String hierarchy = hierarchyHashes(entry, entryByTopLevel, rawByTopLevel);
                String content = rawByTopLevel.get(entry.name()) + "|" + javadoc + "|" + hierarchy;
                String fullHash = Hashing.sha256Hex(baseHash + ":" + content);
                allNameToHash.put(entry.sourcesFileName(), fullHash);

                Map<String, String> bundle = cache.get(fullHash);
                if (bundle != null) {
                    hits.add(new Hit(entry, bundle));
                } else {
                    missEntries.add(entry);
                    missNameToHash.put(entry.sourcesFileName(), fullHash);
                }
            }

            PathUtils.deleteRecursively(stagingDir);
            Files.createDirectories(stagingDir);

            if (missEntries.isEmpty()) {
                restoreHits(hits, stagingDir);
                return new Stats(hits.size(), 0);
            }

            if (hits.isEmpty()) {
                partial = false;
            } else {
                Files.deleteIfExists(incompleteJar);
                Files.deleteIfExists(existingJar);
                writeClassJar(incompleteJar, missEntries, root);
                writeClassJar(existingJar, hits.stream().map(Hit::entry).toList(), root);
                partial = true;
            }
        }

        if (!partial) {
            decompiler.decompile(mergedJar, stagingDir, libraries, logFile, javadocSpec);
            cacheBundles(stagingDir, allNameToHash, cache, null);
            return new Stats(0, missEntries.size());
        }

        Path freshDir = scratch.resolve("decompiled-fresh");
        List<Path> context = new ArrayList<>(libraries);
        context.add(existingJar);
        decompiler.decompile(incompleteJar, freshDir, context, logFile, javadocSpec);
        cacheBundles(freshDir, missNameToHash, cache, stagingDir);
        restoreHits(hits, stagingDir);

        Files.deleteIfExists(incompleteJar);
        Files.deleteIfExists(existingJar);
        PathUtils.deleteRecursively(freshDir);
        return new Stats(hits.size(), missEntries.size());
    }

    private static void cacheBundles(Path producedDir, Map<String, String> nameToHash, DecompileCache cache,
                                     Path copyToStaging) throws IOException {
        Map<String, Map<String, String>> bundles = new HashMap<>();
        try (Stream<Path> walk = Files.walk(producedDir)) {
            for (Path produced : (Iterable<Path>) walk.filter(CachedDecompile::isJava)::iterator) {
                String relative = producedDir.relativize(produced).toString().replace('\\', '/');
                String sources = Files.readString(produced);
                if (copyToStaging != null) {
                    Path destination = copyToStaging.resolve(relative);
                    Files.createDirectories(destination.getParent());
                    Files.writeString(destination, sources);
                }
                String hash = nameToHash.get(topLevelSourceName(relative));
                if (hash != null) {
                    bundles.computeIfAbsent(hash, key -> new LinkedHashMap<>()).put(fileName(relative), sources);
                }
            }
        }
        for (Map.Entry<String, Map<String, String>> bundle : bundles.entrySet()) {
            cache.put(bundle.getKey(), bundle.getValue());
        }
    }

    private static void restoreHits(List<Hit> hits, Path stagingDir) throws IOException {
        for (Hit hit : hits) {
            String packageDir = packageDir(hit.entry().sourcesFileName());
            for (Map.Entry<String, String> file : hit.bundle().entrySet()) {
                Path destination = packageDir.isEmpty()
                        ? stagingDir.resolve(file.getKey())
                        : stagingDir.resolve(packageDir).resolve(file.getKey());
                Files.createDirectories(destination.getParent());
                Files.writeString(destination, file.getValue());
            }
        }
    }

    private static String hierarchyHashes(ClassEntry entry, Map<String, ClassEntry> entryByTopLevel,
                                          Map<String, String> rawByTopLevel) {
        TreeSet<String> hashes = new TreeSet<>();
        Set<String> visited = new HashSet<>();
        ArrayDeque<String> queue = new ArrayDeque<>();
        for (String superClass : entry.superClasses()) {
            queue.add(topLevelOf(superClass));
        }
        while (!queue.isEmpty()) {
            String topLevel = queue.poll();
            if (!visited.add(topLevel)) {
                continue;
            }
            String raw = rawByTopLevel.get(topLevel);
            if (raw == null) {
                continue;
            }
            hashes.add(raw);
            ClassEntry ancestor = entryByTopLevel.get(topLevel);
            if (ancestor != null) {
                for (String superClass : ancestor.superClasses()) {
                    queue.add(topLevelOf(superClass));
                }
            }
        }
        return String.join(",", hashes);
    }

    private static void writeClassJar(Path jarPath, List<ClassEntry> entries, Path sourceRoot) throws IOException {
        try (FileSystem fs = FileSystems.newFileSystem(jarPath, Map.of("create", "true"))) {
            Path target = fs.getPath("/");
            for (ClassEntry entry : entries) {
                entry.copyTo(sourceRoot, target);
            }
        }
    }

    private static boolean isJava(Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".java");
    }

    private static String topLevelSourceName(String relative) {
        String packageDir = packageDir(relative);
        String file = fileName(relative);
        String base = file.substring(0, file.length() - ".java".length());
        int dollar = base.indexOf('$');
        if (dollar >= 0) {
            base = base.substring(0, dollar);
        }
        return packageDir.isEmpty() ? base + ".java" : packageDir + "/" + base + ".java";
    }

    private static String packageDir(String relative) {
        int slash = relative.lastIndexOf('/');
        return slash >= 0 ? relative.substring(0, slash) : "";
    }

    private static String fileName(String relative) {
        int slash = relative.lastIndexOf('/');
        return slash >= 0 ? relative.substring(slash + 1) : relative;
    }

    private static String topLevelOf(String internalName) {
        int dollar = internalName.indexOf('$');
        return dollar >= 0 ? internalName.substring(0, dollar) : internalName;
    }

    public record Stats(int hits, int misses) {
    }

    private record Hit(ClassEntry entry, Map<String, String> bundle) {
    }
}
