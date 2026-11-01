package com.deathmotion.mcsources.decompile.plan;

import com.deathmotion.mcsources.classfile.InternalNames;
import com.deathmotion.mcsources.decompile.store.DecompileStore;
import com.deathmotion.mcsources.decompile.vineflower.VineflowerOptions;
import com.deathmotion.mcsources.io.Hashing;
import com.deathmotion.mcsources.version.PreparedVersion;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

public final class DecompileKeys {
    private final LibraryIndex libraries;
    private final String decompiler;

    public DecompileKeys(LibraryIndex libraries, String decompiler) {
        this.libraries = libraries;
        this.decompiler = decompiler;
    }

    public VersionPlan plan(PreparedVersion version) {
        Context context = new Context(version, libraries.owners(version.libraries()));
        String base = Hashing.sha256Hex(String.join("\n", DecompileStore.FORMAT, decompiler, Runtime.version().toString(),
                VineflowerOptions.identity(version.renameLocals()), context.packageNames("").toString()));
        List<ClassGroup> groups = ClassGroup.of(version.classes());
        Map<String, ClassGroup> groupsByName = new HashMap<>();
        groups.forEach(group -> groupsByName.put(group.name(), group));
        String[] keys = new String[groups.size()];
        List<Set<String>> companions = new ArrayList<>(Collections.nCopies(groups.size(), Set.of()));
        IntStream.range(0, groups.size()).parallel().forEach(i -> {
            ClassGroup group = groups.get(i);
            Set<String> companionNames = companions(group, context);
            companions.set(i, companionNames);
            List<ClassGroup> companionGroups = companionNames.stream().map(groupsByName::get).toList();
            keys[i] = Hashing.sha256Hex(base + "\n" + describe(group, companionGroups, context));
        });
        Map<ClassGroup, String> ordered = new LinkedHashMap<>();
        Map<String, Set<String>> companionsByGroup = new HashMap<>();
        for (int i = 0; i < keys.length; i++) {
            ordered.put(groups.get(i), keys[i]);
            if (!companions.get(i).isEmpty()) {
                companionsByGroup.put(groups.get(i).name(), companions.get(i));
            }
        }
        return new VersionPlan(version.id(), version.tier(), version.mappings(), version.parchment(),
                version.classes().size(), ordered, companionsByGroup);
    }

    private static String describe(ClassGroup group, List<ClassGroup> companions, Context context) {
        StringBuilder text = new StringBuilder();
        Set<String> references = new TreeSet<>();
        Set<String> covered = new HashSet<>(group.members());
        appendBytes(text, "B", group, context, references);
        for (ClassGroup companion : companions) {
            appendBytes(text, "C", companion, context, references);
            covered.addAll(companion.members());
        }
        text.append("J ").append(JavadocDigest.of(context.version.javadocs(), group.members(),
                context.version.classes()::get)).append('\n');
        for (String reference : closure(references, context)) {
            if (!covered.contains(reference)) {
                text.append("R ").append(reference).append(' ').append(context.describe(reference)).append('\n');
            }
        }
        text.append("P ").append(context.packageNames(group.packagePath()));
        return text.toString();
    }

    private static void appendBytes(StringBuilder text, String kind, ClassGroup group, Context context,
                                    Set<String> references) {
        for (String member : group.members()) {
            byte[] bytes = context.version.classes().get(member);
            text.append(kind).append(' ').append(member).append(' ').append(Hashing.sha256Hex(bytes)).append('\n');
            references.addAll(context.references(member).types());
        }
    }

    private static Set<String> companions(ClassGroup group, Context context) {
        Set<String> companions = new TreeSet<>();
        for (String member : group.members()) {
            References references = context.references(member);
            for (String type : references.types()) {
                if (context.isOwn(type) && context.structure(type).synthetic()) {
                    companions.add(InternalNames.topLevel(type));
                }
            }
            for (References.Member used : references.members()) {
                if (context.isOwn(used.owner())
                        && context.structure(used.owner()).declaresSynthetic(used.name(), used.descriptor())) {
                    companions.add(InternalNames.topLevel(used.owner()));
                }
            }
        }
        companions.remove(group.name());
        return companions;
    }

    private static Set<String> closure(Set<String> references, Context context) {
        Set<String> closure = new TreeSet<>();
        Deque<String> pending = new ArrayDeque<>(references);
        while (!pending.isEmpty()) {
            String name = pending.poll();
            if (closure.add(name)) {
                pending.addAll(context.structure(name).supertypesAndOuter());
            }
        }
        return closure;
    }

    private static final class Context {
        private final PreparedVersion version;
        private final Map<String, LibraryIndex.Library> libraryOwners;
        private final Map<String, ClassStructure> ownStructures = new ConcurrentHashMap<>();
        private final Map<String, References> ownReferences = new ConcurrentHashMap<>();
        private final Map<String, SortedSet<String>> packages = new TreeMap<>();

        private Context(PreparedVersion version, Map<String, LibraryIndex.Library> libraryOwners) {
            this.version = version;
            this.libraryOwners = libraryOwners;
            index(version.classes().keySet());
            index(libraryOwners.keySet());
        }

        private void index(Set<String> names) {
            for (String name : names) {
                packages.computeIfAbsent(InternalNames.packageOf(name), key -> new TreeSet<>()).add(name);
            }
        }

        private SortedSet<String> packageNames(String packagePath) {
            return packages.getOrDefault(packagePath, new TreeSet<>());
        }

        private ClassStructure structure(String name) {
            byte[] own = version.classes().get(name);
            if (own != null) {
                return ownStructures.computeIfAbsent(name, key -> ClassStructure.of(own));
            }
            LibraryIndex.Library library = libraryOwners.get(name);
            return library == null ? ClassStructure.ABSENT : library.structure(name);
        }

        private References references(String name) {
            return ownReferences.computeIfAbsent(name, key -> References.of(version.classes().get(key)));
        }

        private boolean isOwn(String name) {
            return version.classes().containsKey(name);
        }

        private String describe(String name) {
            String origin = version.classes().containsKey(name) ? "own" : libraryOwners.containsKey(name) ? "lib" : "jdk";
            return origin + ' ' + structure(name).hash();
        }
    }
}
