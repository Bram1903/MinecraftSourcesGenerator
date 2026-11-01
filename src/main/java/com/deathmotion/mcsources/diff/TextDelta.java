package com.deathmotion.mcsources.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.UnifiedDiffUtils;
import com.github.difflib.patch.Patch;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.*;

final class TextDelta {
    private static final int CONTEXT_LINES = 3;

    private TextDelta() {
    }

    static boolean isJson(String path) {
        return path.endsWith(".json") || path.endsWith(".mcmeta");
    }

    static Optional<JsonElement> parse(byte[] bytes) {
        try {
            return Optional.of(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)));
        } catch (JsonParseException | IllegalStateException e) {
            return Optional.empty();
        }
    }

    static List<String> of(String path, byte[] before, byte[] after) {
        if (isJson(path)) {
            Optional<JsonElement> old = parse(before);
            Optional<JsonElement> updated = parse(after);
            if (old.isPresent() && updated.isPresent()) {
                return lineDelta(flatten(old.get()), flatten(updated.get()));
            }
        }
        return unified(path, path, lines(before), lines(after));
    }

    static List<String> unified(String beforeName, String afterName, List<String> before, List<String> after) {
        Patch<String> patch = DiffUtils.diff(before, after);
        return UnifiedDiffUtils.generateUnifiedDiff("a/" + beforeName, "b/" + afterName, before, patch, CONTEXT_LINES);
    }

    static List<String> lines(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8).lines().toList();
    }

    static List<String> flatten(JsonElement element) {
        List<String> lines = new ArrayList<>();
        walk("", element, lines);
        return lines;
    }

    private static void walk(String path, JsonElement element, List<String> out) {
        if (element.isJsonObject()) {
            if (element.getAsJsonObject().isEmpty()) {
                out.add(path + " = {}");
            }
            element.getAsJsonObject().entrySet().forEach(entry ->
                    walk(path.isEmpty() ? entry.getKey() : path + "." + entry.getKey(), entry.getValue(), out));
            return;
        }
        if (element.isJsonArray()) {
            var array = element.getAsJsonArray();
            if (array.isEmpty()) {
                out.add(path + " = []");
                return;
            }
            boolean primitives = true;
            for (JsonElement item : array) {
                primitives &= item.isJsonPrimitive() || item.isJsonNull();
            }
            for (int i = 0; i < array.size(); i++) {
                if (primitives) {
                    out.add(path + "[] = " + array.get(i));
                } else {
                    walk(path + "[" + i + "]", array.get(i), out);
                }
            }
            return;
        }
        out.add(path + " = " + element);
    }

    static List<String> lineDelta(List<String> before, List<String> after) {
        Map<String, Integer> counts = new HashMap<>();
        before.forEach(line -> counts.merge(line, 1, Integer::sum));
        after.forEach(line -> counts.merge(line, -1, Integer::sum));
        List<String> out = new ArrayList<>();
        counts.forEach((line, count) -> {
            for (int i = 0; i < Math.abs(count); i++) {
                out.add((count > 0 ? "- " : "+ ") + line);
            }
        });
        out.sort(Comparator.comparing((String line) -> line.substring(2)).thenComparing(line -> line.charAt(0) == '-' ? 0 : 1));
        return out;
    }
}
