package com.deathmotion.mcsources.decompile.cache;

import org.objectweb.asm.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Stream;

public final class JarIndex {
    private static final int ASM_API = Opcodes.ASM9;

    private JarIndex() {
    }

    public static Result index(Path root) throws IOException {
        Map<String, Group> groups = new TreeMap<>();

        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : (Iterable<Path>) walk.filter(JarIndex::isClassFile)::iterator) {
                ClassReader reader = new ClassReader(Files.readAllBytes(path));
                Collector collector = new Collector();
                reader.accept(collector, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                String internalName = collector.name;
                String topLevel = topLevelOf(internalName);
                Group group = groups.computeIfAbsent(topLevel, Group::new);
                if (!internalName.equals(topLevel)) {
                    group.inners.add(internalName);
                }
                if (collector.superName != null && !collector.superName.startsWith("java/")) {
                    group.supers.add(collector.superName);
                }
                for (String itf : collector.interfaces) {
                    if (!itf.startsWith("java/")) {
                        group.supers.add(itf);
                    }
                }
                collector.methods.sort(Comparator.comparing(Method::name).thenComparing(Method::descriptor));
                collector.fields.sort(Comparator.naturalOrder());
                group.members.add(new OwnerMembers(internalName, collector.fields, collector.methods));
            }
        }

        List<ClassEntry> entries = new ArrayList<>(groups.size());
        Map<String, List<OwnerMembers>> membersByTopLevel = new TreeMap<>();
        for (Group group : groups.values()) {
            group.supers.remove(group.name);
            group.inners.forEach(group.supers::remove);
            entries.add(new ClassEntry(group.name, new ArrayList<>(group.inners), new ArrayList<>(group.supers)));
            group.members.sort(Comparator.comparing(OwnerMembers::owner));
            membersByTopLevel.put(group.name, group.members);
        }
        return new Result(entries, membersByTopLevel);
    }

    private static boolean isClassFile(Path path) {
        return Files.isRegularFile(path) && path.toString().endsWith(".class");
    }

    private static String topLevelOf(String internalName) {
        int dollar = internalName.indexOf('$');
        return dollar >= 0 ? internalName.substring(0, dollar) : internalName;
    }

    public record Method(String name, String descriptor) {
    }

    public record OwnerMembers(String owner, List<String> fields, List<Method> methods) {
    }

    public record Result(List<ClassEntry> entries, Map<String, List<OwnerMembers>> membersByTopLevel) {
    }

    private static final class Group {
        private final String name;
        private final TreeSet<String> inners = new TreeSet<>();
        private final TreeSet<String> supers = new TreeSet<>();
        private final List<OwnerMembers> members = new ArrayList<>();

        private Group(String name) {
            this.name = name;
        }
    }

    private static final class Collector extends ClassVisitor {
        private final List<String> fields = new ArrayList<>();
        private final List<Method> methods = new ArrayList<>();
        private String name;
        private String superName;
        private String[] interfaces = new String[0];

        private Collector() {
            super(ASM_API);
        }

        @Override
        public void visit(int version, int access, String name, String signature, String superName,
                          String[] interfaces) {
            this.name = name;
            this.superName = superName;
            this.interfaces = interfaces != null ? interfaces : new String[0];
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
            fields.add(name);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                                         String[] exceptions) {
            methods.add(new Method(name, descriptor));
            return null;
        }
    }
}
