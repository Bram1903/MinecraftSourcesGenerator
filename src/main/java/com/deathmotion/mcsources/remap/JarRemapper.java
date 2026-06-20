package com.deathmotion.mcsources.remap;

import com.deathmotion.mcsources.mapping.ResolvedMappings;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class JarRemapper {
    private JarRemapper() {
    }

    public static void remap(Path input, Path output, MemoryMappingTree tree, List<Path> classpath, int threads)
            throws IOException {
        Files.createDirectories(output.getParent());
        if (tree == null) {
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
            return;
        }

        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createMappingProvider(tree,
                        ResolvedMappings.OBFUSCATED_NAMESPACE, ResolvedMappings.NAMED_NAMESPACE))
                .renameInvalidLocals(true)
                .rebuildSourceFilenames(true)
                .ignoreConflicts(true)
                .threads(threads)
                .build();

        try (OutputConsumerPath consumer = new OutputConsumerPath.Builder(output).assumeArchive(true).build()) {
            consumer.addNonClassFiles(input, NonClassCopyMode.FIX_META_INF, remapper);
            if (!classpath.isEmpty()) {
                remapper.readClassPath(classpath.toArray(new Path[0]));
            }
            remapper.readInputs(input);
            remapper.apply(consumer);
        } finally {
            remapper.finish();
        }
    }
}
