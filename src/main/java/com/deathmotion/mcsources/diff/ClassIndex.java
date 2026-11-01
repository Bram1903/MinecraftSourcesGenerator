package com.deathmotion.mcsources.diff;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClassIndex {
    private final Map<String, ClassNode> classes;

    private ClassIndex(Map<String, ClassNode> classes) {
        this.classes = classes;
    }

    public static ClassIndex of(Map<String, byte[]> bytes) {
        Map<String, ClassNode> classes = new ConcurrentHashMap<>();
        bytes.values().parallelStream().forEach(content -> {
            ClassNode node = new ClassNode();
            new ClassReader(content).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            classes.put(node.name, node);
        });
        return new ClassIndex(new HashMap<>(classes));
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
