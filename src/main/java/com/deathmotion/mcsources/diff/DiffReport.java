package com.deathmotion.mcsources.diff;

import com.deathmotion.mcsources.diff.JarComparison.ClassChange;
import com.deathmotion.mcsources.diff.JarComparison.ClassRename;
import com.deathmotion.mcsources.diff.MemberChange.Change;
import com.deathmotion.mcsources.diff.ResourceIndex.Resource;
import com.deathmotion.mcsources.diff.VersionDiff.Reports;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

final class DiffReport {
    private static final String VERSION_FILE = "version.json";
    private static final int TEXT_ONLY_SAMPLE = 25;

    private final Path output;
    private final String older;
    private final String newer;
    private final ResourceIndex beforeResources;
    private final ResourceIndex afterResources;

    DiffReport(Path output, String older, String newer, ResourceIndex beforeResources, ResourceIndex afterResources) {
        this.output = output;
        this.older = older;
        this.newer = newer;
        this.beforeResources = beforeResources;
        this.afterResources = afterResources;
    }

    void write(JarComparison classes, ResourceComparison resources, Optional<Reports> reports,
               SourceComparison sources) throws IOException {
        Files.createDirectories(output);
        writePackages(classes);
        writeMembers(classes);
        writeResources(resources, output.resolve("resources"));
        if (reports.isPresent()) {
            writeReports(reports.get());
        }
        for (Map.Entry<String, List<String>> diff : sources.diffs().entrySet()) {
            write(output.resolve("sources").resolve(diff.getKey() + ".java.diff"), diff.getValue());
        }
        writeSummary(classes, resources, reports, sources);
    }

    private void writeSummary(JarComparison classes, ResourceComparison resources, Optional<Reports> reports,
                              SourceComparison sources) throws IOException {
        List<String> out = new ArrayList<>();
        out.add("# " + older + " -> " + newer);
        out.add("");
        out.add("## Versions");
        out.add("");
        out.add("| | " + older + " | " + newer + " |");
        out.add("|---|---|---|");
        JsonObject oldVersion = version(beforeResources);
        JsonObject newVersion = version(afterResources);
        for (String key : new TreeSet<>(union(oldVersion.keySet(), newVersion.keySet()))) {
            out.add("| " + key + " | " + oldVersion.get(key) + " | " + newVersion.get(key) + " |");
        }
        out.add("");

        out.add("## Classes");
        out.add("");
        out.add("Compared as bytecode. Lambda bodies are folded into the method that creates them and anonymous"
                + " classes are matched by content, so renumbering alone is not a change.");
        out.add("");
        out.add("| | count |");
        out.add("|---|---|");
        out.add("| before | " + classes.classesBefore() + " |");
        out.add("| after | " + classes.classesAfter() + " |");
        out.add("| added | " + classes.added().size() + " |");
        out.add("| removed | " + classes.removed().size() + " |");
        out.add("| renamed or moved | " + classes.renamed().size() + " |");
        out.add("| changed | " + classes.changed().size() + " |");
        out.add("| unchanged | " + classes.unchanged() + " |");
        out.add("");

        Map<Change, Long> changes = allMembers(classes).stream()
                .collect(Collectors.groupingBy(MemberChange::change, () -> new EnumMap<>(Change.class),
                        Collectors.counting()));
        out.add("| member change | count |");
        out.add("|---|---|");
        changes.forEach((change, count) -> out.add("| " + change.name().toLowerCase(Locale.ROOT) + " | " + count + " |"));
        out.add("");

        out.add("## Packages");
        out.add("");
        out.add("Detail per package is in `packages/`, one line per member in `members.tsv`.");
        out.add("");
        out.add("| package | added | removed | renamed | changed |");
        out.add("|---|---|---|---|---|");
        Map<String, int[]> packages = new TreeMap<>();
        classes.added().forEach(name -> packageRow(packages, name)[0]++);
        classes.removed().forEach(name -> packageRow(packages, name)[1]++);
        classes.renamed().forEach(rename -> packageRow(packages, rename.to())[2]++);
        classes.changed().forEach(change -> packageRow(packages, change.name())[3]++);
        packages.forEach((name, row) -> out.add("| [" + name + "](packages/" + name + ".md) | " + row[0] + " | "
                + row[1] + " | " + row[2] + " | " + row[3] + " |"));
        out.add("");

        out.add("## Resources");
        out.add("");
        out.add("| group | added | removed | changed |");
        out.add("|---|---|---|---|");
        Map<String, int[]> groups = new TreeMap<>();
        resources.added().forEach(path -> groups.computeIfAbsent(ResourceComparison.group(path), k -> new int[3])[0]++);
        resources.removed().forEach(path -> groups.computeIfAbsent(ResourceComparison.group(path), k -> new int[3])[1]++);
        resources.changed().forEach(path -> groups.computeIfAbsent(ResourceComparison.group(path), k -> new int[3])[2]++);
        groups.forEach((group, row) -> out.add("| [" + group + "](resources/" + fileName(group) + ".md) | " + row[0]
                + " | " + row[1] + " | " + row[2] + " |"));
        out.add("");

        out.add("## Server reports");
        out.add("");
        if (reports.isEmpty()) {
            out.add("Not generated.");
        } else {
            reports.get().tables().keySet().forEach(name -> out.add("- [" + name + "](reports/" + name + ".md)"));
            out.add("- the rest: " + reports.get().rest().added().size() + " added, "
                    + reports.get().rest().removed().size() + " removed, " + reports.get().rest().changed().size()
                    + " changed, in [reports/other](reports/other/)");
        }
        out.add("");

        out.add("## Decompiled sources");
        out.add("");
        if (!sources.available()) {
            out.add("Not compared, generate both versions first.");
        } else {
            out.add(sources.diffs().size() + " unified diffs in `sources/` for the top-level classes whose bytecode"
                    + " changed.");
            out.add("");
            out.add(sources.textOnly().size() + " source files differ while their bytecode does not. That is"
                    + " decompiler noise or a change the bytecode comparison normalises away:");
            out.add("");
            sources.textOnly().stream().limit(TEXT_ONLY_SAMPLE).forEach(name -> out.add("- `" + name + "`"));
        }
        write(output.resolve("summary.md"), out);
    }

    private static int[] packageRow(Map<String, int[]> packages, String internalName) {
        return packages.computeIfAbsent(ClassNames.packageOf(internalName), key -> new int[4]);
    }

    private void writePackages(JarComparison classes) throws IOException {
        Map<String, List<String>> pages = new TreeMap<>();
        Function<String, List<String>> page = name -> pages.computeIfAbsent(ClassNames.packageOf(name),
                key -> new ArrayList<>(List.of("# " + key, "")));

        for (String name : classes.added()) {
            page.apply(name).add("## + " + ClassNames.simple(name));
            page.apply(name).add("");
        }
        for (String name : classes.removed()) {
            page.apply(name).add("## - " + ClassNames.simple(name));
            page.apply(name).add("");
        }
        for (ClassRename rename : classes.renamed()) {
            List<String> lines = page.apply(rename.to());
            lines.add("## > " + ClassNames.simple(rename.to()) + " from `" + rename.from().replace('/', '.')
                    + "`, " + Math.round(rename.overlap() * 100) + "% of methods identical");
            lines.add("");
            appendChange(lines, rename.change());
        }
        for (ClassChange change : classes.changed()) {
            List<String> lines = page.apply(change.name());
            lines.add("## ~ " + ClassNames.simple(change.name()));
            lines.add("");
            appendChange(lines, change);
        }
        for (Map.Entry<String, List<String>> entry : pages.entrySet()) {
            write(output.resolve("packages").resolve(entry.getKey() + ".md"), entry.getValue());
        }
    }

    private static void appendChange(List<String> lines, ClassChange change) {
        change.header().forEach(line -> lines.add("- class: " + line));
        for (MemberChange member : change.members()) {
            lines.add("- " + member.change().name().toLowerCase(Locale.ROOT) + " " + member.member().name()
                    .toLowerCase(Locale.ROOT) + " `" + member.display() + "`"
                    + (member.detail().isEmpty() ? "" : ": " + member.detail()));
        }
        lines.add("");
    }

    private void writeMembers(JarComparison classes) throws IOException {
        List<String> out = new ArrayList<>();
        out.add("package\tclass\tmember kind\tchange\tmember\tdescriptor\tdetail");
        classes.added().forEach(name -> out.add(row(name, "class", "added", "", "", "")));
        classes.removed().forEach(name -> out.add(row(name, "class", "removed", "", "", "")));
        for (ClassRename rename : classes.renamed()) {
            out.add(row(rename.to(), "class", "renamed", "", "", "from " + rename.from().replace('/', '.')));
            rename.change().header().forEach(line -> out.add(row(rename.to(), "class", "header", "", "", line)));
            rename.change().members().forEach(member -> out.add(memberRow(rename.to(), member)));
        }
        for (ClassChange change : classes.changed()) {
            change.header().forEach(line -> out.add(row(change.name(), "class", "header", "", "", line)));
            change.members().forEach(member -> out.add(memberRow(change.name(), member)));
        }
        write(output.resolve("members.tsv"), out);
    }

    private static String memberRow(String owner, MemberChange member) {
        return row(owner, member.member().name().toLowerCase(Locale.ROOT),
                member.change().name().toLowerCase(Locale.ROOT), member.display(), member.descriptor(),
                member.detail());
    }

    private static String row(String owner, String kind, String change, String member, String descriptor,
                              String detail) {
        return String.join("\t", ClassNames.packageOf(owner), ClassNames.simple(owner), kind, change, member,
                descriptor, detail.replace('\t', ' '));
    }

    private void writeResources(ResourceComparison resources, Path directory) throws IOException {
        Map<String, List<String>> pages = new TreeMap<>();
        Function<String, List<String>> page = path -> pages.computeIfAbsent(ResourceComparison.group(path),
                group -> new ArrayList<>(List.of("# " + group, "")));
        resources.added().forEach(path -> page.apply(path).add("+ `" + path + "`"));
        resources.removed().forEach(path -> page.apply(path).add("- `" + path + "`"));
        resources.changed().forEach(path -> page.apply(path).add("~ `" + path + "`"));
        for (Map.Entry<String, List<String>> delta : resources.deltas().entrySet()) {
            List<String> lines = page.apply(delta.getKey());
            lines.add("");
            lines.add("## " + delta.getKey());
            lines.add("");
            lines.add("```");
            lines.addAll(delta.getValue());
            lines.add("```");
        }
        for (Map.Entry<String, List<String>> entry : pages.entrySet()) {
            write(directory.resolve(fileName(entry.getKey()) + ".md"), entry.getValue());
        }
    }

    private void writeReports(Reports reports) throws IOException {
        for (Map.Entry<String, List<String>> table : reports.tables().entrySet()) {
            List<String> lines = new ArrayList<>(List.of("# " + table.getKey(), ""));
            lines.addAll(table.getValue());
            write(output.resolve("reports").resolve(table.getKey() + ".md"), lines);
        }
        writeResources(reports.rest(), output.resolve("reports").resolve("other"));
    }

    private static JsonObject version(ResourceIndex resources) {
        return resources.find(VERSION_FILE).map(Resource::text).flatMap(TextDelta::parse)
                .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElseGet(JsonObject::new);
    }

    private static List<MemberChange> allMembers(JarComparison classes) {
        List<MemberChange> members = new ArrayList<>();
        classes.changed().forEach(change -> members.addAll(change.members()));
        classes.renamed().forEach(rename -> members.addAll(rename.change().members()));
        return members;
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> union = new HashSet<>(first);
        union.addAll(second);
        return union;
    }

    private static String fileName(String group) {
        return group.replace('/', '.').replace("(", "").replace(")", "");
    }

    private static void write(Path file, List<String> lines) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, lines);
    }
}
