package com.deathmotion.mcsources.diff.data;

import com.deathmotion.mcsources.diff.TextDelta;
import com.deathmotion.mcsources.diff.data.ResourceIndex.Resource;

import java.util.*;

public record ResourceComparison(List<String> added, List<String> removed, List<String> changed,
                                 Map<String, List<String>> deltas) {

    private static final int ADDED_CONTENT_LIMIT = 400;

    public static ResourceComparison compare(ResourceIndex before, ResourceIndex after, Set<String> excluded) {
        Set<String> paths = new TreeSet<>(before.resources().keySet());
        paths.addAll(after.resources().keySet());
        paths.removeAll(excluded);

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> changed = new ArrayList<>();
        Map<String, List<String>> deltas = new TreeMap<>();
        for (String path : paths) {
            Resource old = before.resources().get(path);
            Resource updated = after.resources().get(path);
            if (old == null) {
                added.add(path);
                addedContent(updated).ifPresent(content -> deltas.put(path, content));
            } else if (updated == null) {
                removed.add(path);
            } else if (old.crc() != updated.crc() || old.size() != updated.size()) {
                changed.add(path);
                if (old.text() != null && updated.text() != null) {
                    deltas.put(path, TextDelta.of(path, old.text(), updated.text()));
                }
            }
        }
        return new ResourceComparison(added, removed, changed, deltas);
    }

    private static Optional<List<String>> addedContent(Resource resource) {
        if (resource.text() == null || !TextDelta.isJson(resource.path())) {
            return Optional.empty();
        }
        return TextDelta.parse(resource.text())
                .map(TextDelta::flatten)
                .filter(lines -> lines.size() <= ADDED_CONTENT_LIMIT)
                .map(lines -> lines.stream().map(line -> "+ " + line).toList());
    }

    public static String group(String path) {
        String[] parts = path.split("/");
        int depth = parts[0].equals("data") || parts[0].equals("assets") ? 4 : 1;
        int length = Math.min(depth, parts.length - 1);
        return length <= 0 ? "(root)" : String.join("/", Arrays.copyOf(parts, length));
    }
}
