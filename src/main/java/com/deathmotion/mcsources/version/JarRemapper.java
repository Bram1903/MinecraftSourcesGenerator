package com.deathmotion.mcsources.version;

import com.deathmotion.mcsources.mapping.ResolvedMappings;
import com.deathmotion.mcsources.classfile.JarClasses;
import net.fabricmc.mappingio.tree.MemoryMappingTree;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class JarRemapper {
    private JarRemapper() {
    }

    static Map<String, byte[]> remap(Path jar, MemoryMappingTree tree, List<Path> classpath, int threads)
            throws IOException {
        if (tree == null) {
            return JarClasses.read(jar);
        }
        TinyRemapper remapper = TinyRemapper.newRemapper()
                .withMappings(TinyUtils.createMappingProvider(tree,
                        ResolvedMappings.OBFUSCATED_NAMESPACE, ResolvedMappings.NAMED_NAMESPACE))
                .renameInvalidLocals(true)
                .rebuildSourceFilenames(true)
                .ignoreConflicts(true)
                .threads(threads)
                .build();
        Map<String, byte[]> classes = new ConcurrentHashMap<>();
        try {
            if (!classpath.isEmpty()) {
                remapper.readClassPath(classpath.toArray(new Path[0]));
            }
            remapper.readInputs(jar);
            remapper.apply(classes::put);
        } finally {
            remapper.finish();
        }
        return classes;
    }
}
