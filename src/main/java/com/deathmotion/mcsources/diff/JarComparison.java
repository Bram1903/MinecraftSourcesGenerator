package com.deathmotion.mcsources.diff;

import com.deathmotion.mcsources.diff.ClassShape.FieldShape;
import com.deathmotion.mcsources.diff.ClassShape.MethodShape;
import com.deathmotion.mcsources.diff.MemberChange.Change;
import com.deathmotion.mcsources.diff.MemberChange.Member;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.*;
import java.util.stream.Collectors;

public record JarComparison(int classesBefore,
                            int classesAfter,
                            List<String> added,
                            List<String> removed,
                            List<ClassRename> renamed,
                            List<ClassChange> changed,
                            int unchanged) {

    private static final double RENAME_OVERLAP = 0.6;
    private static final double RENAME_OVERLAP_SAME_NAME = 0.3;
    private static final int SUBSTANTIAL_METHOD = 6;
    private static final int RENAMED_METHOD_MINIMUM = 4;
    private static final int REFERENCE_LIMIT = 30;
    private static final String PACKAGE_INFO = "/package-info";

    public record ClassChange(String name, List<String> header, List<MemberChange> members) {
        boolean isEmpty() {
            return header.isEmpty() && members.isEmpty();
        }
    }

    public record ClassRename(String from, String to, double overlap, ClassChange change) {
    }

    static JarComparison compare(ClassIndex before, ClassIndex after) {
        Map<String, ClassShape> old = shapes(before);
        Map<String, ClassShape> updated = shapes(after);
        Map<String, List<Long>> oldAnonymous = anonymousByTopLevel(old);
        Map<String, List<Long>> updatedAnonymous = anonymousByTopLevel(updated);

        Set<String> oldNamed = named(old);
        Set<String> updatedNamed = named(updated);
        List<String> removed = oldNamed.stream().filter(name -> !updatedNamed.contains(name)).sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        List<String> added = updatedNamed.stream().filter(name -> !oldNamed.contains(name)).sorted()
                .collect(Collectors.toCollection(ArrayList::new));
        List<ClassRename> renamed = matchRenames(removed, added, old, updated, before, after);

        List<ClassChange> changed = new ArrayList<>();
        int unchanged = 0;
        for (String name : new TreeSet<>(oldNamed)) {
            if (!updatedNamed.contains(name)) {
                continue;
            }
            ClassChange change = compareClass(old.get(name), updated.get(name), before.node(name), after.node(name));
            if (name.indexOf('$') < 0) {
                anonymousDelta(oldAnonymous.getOrDefault(name, List.of()),
                        updatedAnonymous.getOrDefault(name, List.of())).ifPresent(change.header()::add);
            }
            if (change.isEmpty()) {
                unchanged++;
            } else {
                changed.add(change);
            }
        }
        return new JarComparison(before.size(), after.size(), added, removed, renamed, changed, unchanged);
    }

    private static Map<String, ClassShape> shapes(ClassIndex index) {
        return index.classes().values().parallelStream()
                .collect(Collectors.toConcurrentMap(node -> node.name, ClassShape::of));
    }

    private static Set<String> named(Map<String, ClassShape> shapes) {
        return shapes.keySet().stream().filter(name -> !ClassNames.isAnonymous(name)).collect(Collectors.toSet());
    }

    private static Map<String, List<Long>> anonymousByTopLevel(Map<String, ClassShape> shapes) {
        Map<String, List<Long>> grouped = new HashMap<>();
        shapes.values().stream().filter(shape -> ClassNames.isAnonymous(shape.name())).forEach(shape ->
                grouped.computeIfAbsent(ClassNames.topLevel(shape.name()), key -> new ArrayList<>())
                        .add(shape.fingerprint()));
        grouped.values().forEach(Collections::sort);
        return grouped;
    }

    private static Optional<String> anonymousDelta(List<Long> before, List<Long> after) {
        if (before.equals(after)) {
            return Optional.empty();
        }
        List<Long> unmatched = new ArrayList<>(after);
        for (Long fingerprint : before) {
            unmatched.remove(fingerprint);
        }
        return Optional.of("anonymous or local classes " + before.size() + " -> " + after.size() + ", "
                + unmatched.size() + " without an identical counterpart");
    }

    private static List<ClassRename> matchRenames(List<String> removed, List<String> added,
                                                  Map<String, ClassShape> old, Map<String, ClassShape> updated,
                                                  ClassIndex before, ClassIndex after) {
        List<ClassRename> renames = new ArrayList<>();
        for (String from : List.copyOf(removed)) {
            if (from.endsWith(PACKAGE_INFO)) {
                continue;
            }
            ClassShape source = old.get(from);
            Set<Long> sourcePrints = substantial(source);
            String best = null;
            double bestScore = 0;
            for (String to : added) {
                ClassShape target = updated.get(to);
                boolean sameName = nestedSimple(from).equals(nestedSimple(to));
                if (sourcePrints.isEmpty() && !sameName) {
                    continue;
                }
                double score = overlap(source, sourcePrints, target);
                double required = sameName ? RENAME_OVERLAP_SAME_NAME : RENAME_OVERLAP;
                if (score >= required && score > bestScore) {
                    best = to;
                    bestScore = score;
                }
            }
            if (best != null) {
                ClassChange change = compareClass(source, updated.get(best), before.node(from), after.node(best));
                renames.add(new ClassRename(from, best, bestScore, change));
                removed.remove(from);
                added.remove(best);
            }
        }
        return renames;
    }

    private static String nestedSimple(String internalName) {
        String simple = ClassNames.simple(internalName);
        return simple.substring(simple.lastIndexOf('$') + 1);
    }

    private static Set<Long> substantial(ClassShape shape) {
        return shape.methods().values().stream()
                .filter(method -> method.size() >= SUBSTANTIAL_METHOD && !method.name().startsWith("<"))
                .map(MethodShape::fingerprint)
                .collect(Collectors.toSet());
    }

    private static double overlap(ClassShape source, Set<Long> sourcePrints, ClassShape target) {
        Set<Long> targetPrints = substantial(target);
        if (sourcePrints.isEmpty() && targetPrints.isEmpty()) {
            return source.fingerprint() == target.fingerprint() ? 1.0 : 0.0;
        }
        long shared = sourcePrints.stream().filter(targetPrints::contains).count();
        return (double) shared / Math.max(sourcePrints.size(), targetPrints.size());
    }

    private static ClassChange compareClass(ClassShape before, ClassShape after, ClassNode beforeNode,
                                            ClassNode afterNode) {
        List<String> header = new ArrayList<>();
        int mask = ClassShape.HEADER_ACCESS_MASK;
        if ((before.access() & mask) != (after.access() & mask)) {
            header.add("access " + ClassNames.access(before.access() & mask) + " -> "
                    + ClassNames.access(after.access() & mask));
        }
        if (!Objects.equals(before.superName(), after.superName())) {
            header.add("extends " + ClassNames.simple(before.superName()) + " -> "
                    + ClassNames.simple(after.superName()));
        }
        setDelta("implements", before.interfaces(), after.interfaces()).ifPresent(header::add);
        setDelta("permits", before.permittedSubclasses(), after.permittedSubclasses()).ifPresent(header::add);
        if (!Objects.equals(before.signature(), after.signature()) && before.superName() != null
                && Objects.equals(before.superName(), after.superName())) {
            header.add("generic signature " + before.signature() + " -> " + after.signature());
        }

        List<MemberChange> members = new ArrayList<>();
        compareFields(before, after, members);
        compareMethods(before, after, beforeNode, afterNode, members);
        return new ClassChange(after.name(), header, members);
    }

    private static Optional<String> setDelta(String label, List<String> before, List<String> after) {
        List<String> gained = after.stream().filter(name -> !before.contains(name)).map(ClassNames::simple).toList();
        List<String> lost = before.stream().filter(name -> !after.contains(name)).map(ClassNames::simple).toList();
        if (gained.isEmpty() && lost.isEmpty()) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder(label);
        gained.forEach(name -> out.append(" +").append(name));
        lost.forEach(name -> out.append(" -").append(name));
        return Optional.of(out.toString());
    }

    private static void compareFields(ClassShape before, ClassShape after, List<MemberChange> members) {
        Set<String> names = new TreeSet<>(before.fields().keySet());
        names.addAll(after.fields().keySet());
        for (String name : names) {
            FieldShape old = before.fields().get(name);
            FieldShape updated = after.fields().get(name);
            if (old == null) {
                members.add(new MemberChange(Member.FIELD, Change.ADDED, name, updated.descriptor(),
                        ClassNames.access(updated.access()) + valueSuffix(updated.value())));
                continue;
            }
            if (updated == null) {
                members.add(new MemberChange(Member.FIELD, Change.REMOVED, name, old.descriptor(),
                        ClassNames.access(old.access())));
                continue;
            }
            if (!old.descriptor().equals(updated.descriptor())) {
                members.add(new MemberChange(Member.FIELD, Change.TYPE, name, updated.descriptor(),
                        ClassNames.typeName(old.descriptor()) + " -> " + ClassNames.typeName(updated.descriptor())));
            } else if (!Objects.equals(old.signature(), updated.signature())) {
                members.add(new MemberChange(Member.FIELD, Change.SIGNATURE, name, updated.descriptor(),
                        old.signature() + " -> " + updated.signature()));
            }
            if (old.access() != updated.access()) {
                members.add(new MemberChange(Member.FIELD, Change.ACCESS, name, updated.descriptor(),
                        ClassNames.access(old.access()) + " -> " + ClassNames.access(updated.access())));
            }
            if (!Objects.equals(old.value(), updated.value())) {
                members.add(new MemberChange(Member.FIELD, Change.VALUE, name, updated.descriptor(),
                        old.value() + " -> " + updated.value()));
            }
        }
    }

    private static String valueSuffix(Object value) {
        return value == null ? "" : " = " + value;
    }

    private static void compareMethods(ClassShape before, ClassShape after, ClassNode beforeNode,
                                       ClassNode afterNode, List<MemberChange> members) {
        InstructionText beforeText = new InstructionText(beforeNode);
        InstructionText afterText = new InstructionText(afterNode);
        Map<String, MethodNode> beforeMethods = methods(beforeNode);
        Map<String, MethodNode> afterMethods = methods(afterNode);

        Set<String> keys = new TreeSet<>(before.methods().keySet());
        keys.addAll(after.methods().keySet());
        List<MethodShape> onlyBefore = new ArrayList<>();
        List<MethodShape> onlyAfter = new ArrayList<>();
        for (String key : keys) {
            MethodShape old = before.methods().get(key);
            MethodShape updated = after.methods().get(key);
            if (old == null) {
                onlyAfter.add(updated);
                continue;
            }
            if (updated == null) {
                onlyBefore.add(old);
                continue;
            }
            if (old.fingerprint() != updated.fingerprint()) {
                members.add(new MemberChange(Member.METHOD, Change.BODY, updated.name(), updated.descriptor(),
                        bodyDetail(beforeMethods.get(key), afterMethods.get(key), beforeText, afterText)));
            }
            if (old.access() != updated.access()) {
                members.add(new MemberChange(Member.METHOD, Change.ACCESS, updated.name(), updated.descriptor(),
                        ClassNames.access(old.access()) + " -> " + ClassNames.access(updated.access())));
            }
            if (!Objects.equals(old.signature(), updated.signature())) {
                members.add(new MemberChange(Member.METHOD, Change.SIGNATURE, updated.name(), updated.descriptor(),
                        old.signature() + " -> " + updated.signature()));
            }
        }

        for (MethodShape old : List.copyOf(onlyBefore)) {
            if (old.size() < RENAMED_METHOD_MINIMUM) {
                continue;
            }
            Optional<MethodShape> twin = onlyAfter.stream()
                    .filter(candidate -> candidate.fingerprint() == old.fingerprint())
                    .findFirst();
            if (twin.isEmpty()) {
                continue;
            }
            MethodShape updated = twin.get();
            Change change = old.name().equals(updated.name()) ? Change.DESCRIPTOR : Change.RENAMED;
            members.add(new MemberChange(Member.METHOD, change, updated.name(), updated.descriptor(),
                    "from " + ClassNames.method(old.name(), old.descriptor()) + ", body unchanged"));
            onlyBefore.remove(old);
            onlyAfter.remove(updated);
        }

        for (MethodShape old : List.copyOf(onlyBefore)) {
            List<MethodShape> sameNameAfter = onlyAfter.stream().filter(m -> m.name().equals(old.name())).toList();
            long sameNameBefore = onlyBefore.stream().filter(m -> m.name().equals(old.name())).count();
            if (sameNameAfter.size() != 1 || sameNameBefore != 1) {
                continue;
            }
            MethodShape updated = sameNameAfter.getFirst();
            String detail = "from " + ClassNames.method(old.name(), old.descriptor());
            if (old.fingerprint() != updated.fingerprint()) {
                detail += ", " + bodyDetail(beforeMethods.get(old.key()), afterMethods.get(updated.key()),
                        beforeText, afterText);
            }
            members.add(new MemberChange(Member.METHOD, Change.DESCRIPTOR, updated.name(), updated.descriptor(),
                    detail));
            onlyBefore.remove(old);
            onlyAfter.remove(updated);
        }

        for (MethodShape old : onlyBefore) {
            members.add(new MemberChange(Member.METHOD, Change.REMOVED, old.name(), old.descriptor(),
                    ClassNames.access(old.access())));
        }
        for (MethodShape updated : onlyAfter) {
            members.add(new MemberChange(Member.METHOD, Change.ADDED, updated.name(), updated.descriptor(),
                    ClassNames.access(updated.access()) + ", size " + updated.size()));
        }
    }

    private static Map<String, MethodNode> methods(ClassNode node) {
        Map<String, MethodNode> methods = new HashMap<>();
        for (MethodNode method : node.methods) {
            methods.put(method.name + method.desc, method);
        }
        return methods;
    }

    private static String bodyDetail(MethodNode before, MethodNode after, InstructionText beforeText,
                                     InstructionText afterText) {
        Set<String> old = beforeText.references(before);
        Set<String> updated = afterText.references(after);
        List<String> gained = updated.stream().filter(reference -> !old.contains(reference)).toList();
        List<String> lost = old.stream().filter(reference -> !updated.contains(reference)).toList();
        StringBuilder out = new StringBuilder("size ").append(before.instructions.size()).append(" -> ")
                .append(after.instructions.size());
        appendReferences(out, " | + ", gained);
        appendReferences(out, " | - ", lost);
        return out.toString();
    }

    private static void appendReferences(StringBuilder out, String prefix, List<String> references) {
        if (references.isEmpty()) {
            return;
        }
        out.append(prefix).append(String.join(", ", references.subList(0, Math.min(REFERENCE_LIMIT, references.size()))));
        if (references.size() > REFERENCE_LIMIT) {
            out.append(", and ").append(references.size() - REFERENCE_LIMIT).append(" more");
        }
    }
}
