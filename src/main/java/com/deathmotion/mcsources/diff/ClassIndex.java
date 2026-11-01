package com.deathmotion.mcsources.diff;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ClassIndex {
    private final Map<String, ClassNode> classes;

    private ClassIndex(Map<String, ClassNode> classes) {
        this.classes = classes;
    }

    public static ClassIndex read(Path jar) throws IOException {
        Map<String, ClassNode> classes = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory() || !entry.getName().endsWith(".class")) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    ClassNode node = new ClassNode();
                    new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                    classes.put(node.name, node);
                }
            }
        }
        return new ClassIndex(classes);
    }

    public Map<String, ClassNode> classes() {
        return classes;
    }

    public ClassNode node(String internalName) {
        return classes.get(internalName);
    }

    public int size() {
        return classes.size();
    }
}
