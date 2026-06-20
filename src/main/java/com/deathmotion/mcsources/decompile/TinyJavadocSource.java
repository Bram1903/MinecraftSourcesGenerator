package com.deathmotion.mcsources.decompile;

import net.fabricmc.mappingio.MappingReader;
import net.fabricmc.mappingio.format.MappingFormat;
import net.fabricmc.mappingio.tree.MappingTree;
import net.fabricmc.mappingio.tree.MemoryMappingTree;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class TinyJavadocSource implements JavadocSource {
    private static final String NAMED = "named";

    private final Map<String, MappingTree.ClassMapping> classes = new HashMap<>();
    private final Map<String, MappingTree.MethodMapping> methods = new HashMap<>();
    private final Map<String, MappingTree.FieldMapping> fields = new HashMap<>();
    private final int namedNamespace;

    private TinyJavadocSource(MemoryMappingTree tree) {
        namedNamespace = tree.getNamespaceId(NAMED);
        for (MappingTree.ClassMapping clazz : tree.getClasses()) {
            String className = clazz.getName(namedNamespace);
            if (className == null) {
                continue;
            }
            classes.put(className, clazz);
            for (MappingTree.MethodMapping method : clazz.getMethods()) {
                String name = method.getName(namedNamespace);
                String descriptor = method.getDesc(namedNamespace);
                if (name != null && descriptor != null) {
                    methods.put(className + "#" + name + descriptor, method);
                }
            }
            for (MappingTree.FieldMapping field : clazz.getFields()) {
                String name = field.getName(namedNamespace);
                if (name != null) {
                    fields.put(className + "#" + name, field);
                }
            }
        }
    }

    static TinyJavadocSource load(Path tiny) throws IOException {
        MemoryMappingTree tree = new MemoryMappingTree();
        MappingReader.read(tiny, MappingFormat.TINY_2_FILE, tree);
        return new TinyJavadocSource(tree);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    @Override
    public String classDoc(String owner) {
        MappingTree.ClassMapping mapping = classes.get(owner);
        return mapping == null ? null : blankToNull(mapping.getComment());
    }

    @Override
    public String fieldDoc(String owner, String field) {
        MappingTree.FieldMapping mapping = fields.get(owner + "#" + field);
        return mapping == null ? null : blankToNull(mapping.getComment());
    }

    @Override
    public String methodDoc(String owner, String name, String descriptor) {
        MappingTree.MethodMapping mapping = methods.get(owner + "#" + name + descriptor);
        if (mapping == null) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        if (mapping.getComment() != null && !mapping.getComment().isBlank()) {
            lines.add(mapping.getComment());
        }
        for (MappingTree.MethodArgMapping argument : mapping.getArgs()) {
            String name1 = argument.getName(namedNamespace);
            String comment = argument.getComment();
            if (name1 != null && comment != null && !comment.isBlank()) {
                lines.add("@param " + name1 + " " + comment);
            }
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }
}
