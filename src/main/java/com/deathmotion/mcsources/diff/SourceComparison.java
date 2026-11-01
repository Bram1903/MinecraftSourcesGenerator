package com.deathmotion.mcsources.diff;

import com.deathmotion.mcsources.classfile.InternalNames;
import com.deathmotion.mcsources.diff.bytecode.JarComparison;
import com.deathmotion.mcsources.diff.bytecode.JarComparison.ClassRename;

import com.deathmotion.mcsources.pipeline.SourceTree;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record SourceComparison(boolean available, Map<String, List<String>> diffs, List<String> textOnly) {

    static SourceComparison compare(SourceTree tree, String older, String newer, JarComparison classes)
            throws IOException {
        if (!tree.isCurrent(older) || !tree.isCurrent(newer)) {
            return new SourceComparison(false, Map.of(), List.of());
        }
        Path beforeRoot = tree.directory(older);
        Path afterRoot = tree.directory(newer);
        Map<String, String> pairs = new TreeMap<>();
        classes.changed().forEach(change -> {
            String topLevel = InternalNames.topLevel(change.name());
            pairs.put(topLevel, topLevel);
        });
        for (ClassRename rename : classes.renamed()) {
            pairs.putIfAbsent(InternalNames.topLevel(rename.to()), InternalNames.topLevel(rename.from()));
        }

        Map<String, List<String>> diffs = new ConcurrentSkipListMap<>();
        pairs.entrySet().parallelStream().forEach(pair -> {
            Path before = beforeRoot.resolve(pair.getValue() + ".java");
            Path after = afterRoot.resolve(pair.getKey() + ".java");
            if (Files.exists(before) && Files.exists(after)) {
                List<String> diff = TextDelta.unified(pair.getValue() + ".java", pair.getKey() + ".java",
                        read(before), read(after));
                if (!diff.isEmpty()) {
                    diffs.put(pair.getKey(), diff);
                }
            }
        });

        Set<String> bytecodeChanged = new HashSet<>(pairs.keySet());
        List<String> textOnly;
        try (Stream<Path> walk = Files.walk(afterRoot)) {
            textOnly = walk.parallel()
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(path -> afterRoot.relativize(path).toString().replace('\\', '/'))
                    .map(relative -> relative.substring(0, relative.length() - ".java".length()))
                    .filter(name -> !bytecodeChanged.contains(name))
                    .filter(name -> {
                        Path before = beforeRoot.resolve(name + ".java");
                        return Files.exists(before) && !read(before).equals(read(afterRoot.resolve(name + ".java")));
                    })
                    .sorted()
                    .collect(Collectors.toList());
        }
        return new SourceComparison(true, diffs, textOnly);
    }

    private static List<String> read(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
