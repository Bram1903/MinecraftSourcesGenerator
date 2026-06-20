package com.deathmotion.mcsources.mapping.parchment;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ParchmentData {
    private final Map<String, List<String>> classJavadoc = new HashMap<>();
    private final Map<String, List<String>> fieldJavadoc = new HashMap<>();
    private final Map<String, ParchmentMethod> methods = new HashMap<>();

    private ParchmentData(Root root) {
        if (root == null || root.classes == null) {
            return;
        }
        for (ClassEntry clazz : root.classes) {
            if (clazz.name == null) {
                continue;
            }
            if (hasContent(clazz.javadoc)) {
                classJavadoc.put(clazz.name, clazz.javadoc);
            }
            if (clazz.fields != null) {
                for (FieldEntry field : clazz.fields) {
                    if (hasContent(field.javadoc)) {
                        fieldJavadoc.put(fieldKey(clazz.name, field.name), field.javadoc);
                    }
                }
            }
            if (clazz.methods != null) {
                for (MethodEntry method : clazz.methods) {
                    methods.put(methodKey(clazz.name, method.name, method.descriptor), new ParchmentMethod(method));
                }
            }
        }
    }

    public static ParchmentData load(Path json) throws IOException {
        try (Reader reader = Files.newBufferedReader(json)) {
            return new ParchmentData(new Gson().fromJson(reader, Root.class));
        }
    }

    private static boolean hasContent(List<String> javadoc) {
        return javadoc != null && !javadoc.isEmpty();
    }

    private static String fieldKey(String owner, String field) {
        return owner + "#" + field;
    }

    private static String methodKey(String owner, String name, String descriptor) {
        return owner + "#" + name + descriptor;
    }

    public Map<Integer, String> parameterNames(String owner, String name, String descriptor) {
        ParchmentMethod method = methods.get(methodKey(owner, name, descriptor));
        return method == null ? Map.of() : method.parameterNames;
    }

    public List<String> classDoc(String owner) {
        return classJavadoc.get(owner);
    }

    public List<String> fieldDoc(String owner, String field) {
        return fieldJavadoc.get(fieldKey(owner, field));
    }

    public List<String> methodDoc(String owner, String name, String descriptor) {
        ParchmentMethod method = methods.get(methodKey(owner, name, descriptor));
        if (method == null) {
            return null;
        }
        List<String> lines = new ArrayList<>(method.javadoc);
        if (!method.parameterDocs.isEmpty()) {
            if (!lines.isEmpty()) {
                lines.add("");
            }
            lines.addAll(method.parameterDocs);
        }
        return lines.isEmpty() ? null : lines;
    }

    private static final class ParchmentMethod {
        private final List<String> javadoc;
        private final Map<Integer, String> parameterNames = new HashMap<>();
        private final List<String> parameterDocs = new ArrayList<>();

        private ParchmentMethod(MethodEntry entry) {
            this.javadoc = entry.javadoc != null ? entry.javadoc : List.of();
            if (entry.parameters != null) {
                for (ParameterEntry parameter : entry.parameters) {
                    if (parameter.name != null) {
                        parameterNames.put(parameter.index, parameter.name);
                        if (parameter.javadoc != null && !parameter.javadoc.isBlank()) {
                            parameterDocs.add("@param " + parameter.name + " " + parameter.javadoc);
                        }
                    }
                }
            }
        }
    }

    private static final class Root {
        List<ClassEntry> classes;
    }

    private static final class ClassEntry {
        String name;
        List<String> javadoc;
        List<FieldEntry> fields;
        List<MethodEntry> methods;
    }

    private static final class FieldEntry {
        String name;
        List<String> javadoc;
    }

    private static final class MethodEntry {
        String name;
        String descriptor;
        List<String> javadoc;
        List<ParameterEntry> parameters;
    }

    private static final class ParameterEntry {
        int index;
        String name;
        String javadoc;
    }
}
