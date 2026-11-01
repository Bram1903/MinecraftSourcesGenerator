package com.deathmotion.mcsources.diff.data;

import com.deathmotion.mcsources.diff.TextDelta;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

final class ReportTables {
    static final String PACKETS = "packets.json";
    static final String BLOCKS = "blocks.json";
    static final String REGISTRIES = "registries.json";
    static final Set<String> SPECIAL = Set.of(PACKETS, BLOCKS, REGISTRIES);

    private ReportTables() {
    }

    static List<String> of(String path, byte[] before, byte[] after) {
        JsonObject old = TextDelta.parse(before).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .orElseGet(JsonObject::new);
        JsonObject updated = TextDelta.parse(after).filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject)
                .orElseGet(JsonObject::new);
        return switch (path) {
            case PACKETS -> packets(old, updated);
            case BLOCKS -> blocks(old, updated);
            case REGISTRIES -> registries(old, updated);
            default -> List.of();
        };
    }

    private static List<String> packets(JsonObject before, JsonObject after) {
        List<String> out = new ArrayList<>();
        for (String phase : union(before, after)) {
            JsonObject oldPhase = object(before, phase);
            JsonObject newPhase = object(after, phase);
            for (String direction : union(oldPhase, newPhase)) {
                Map<String, Integer> old = ids(object(oldPhase, direction), "protocol_id");
                Map<String, Integer> updated = ids(object(newPhase, direction), "protocol_id");
                out.add("## " + phase + " " + direction);
                out.add("");
                summarise(out, old, updated);
                out.add("");
                out.add("| before | after | packet |");
                out.add("|---|---|---|");
                List<String> names = new ArrayList<>(union(old.keySet(), updated.keySet()));
                names.sort(Comparator.comparing((String name) -> updated.getOrDefault(name, Integer.MAX_VALUE))
                        .thenComparing(name -> old.getOrDefault(name, Integer.MAX_VALUE)));
                for (String name : names) {
                    out.add("| " + text(old.get(name)) + " | " + text(updated.get(name)) + " | " + marker(old, updated, name)
                            + name + " |");
                }
                out.add("");
            }
        }
        return out;
    }

    private static List<String> registries(JsonObject before, JsonObject after) {
        List<String> out = new ArrayList<>();
        for (String registry : union(before, after)) {
            JsonObject old = object(before, registry);
            JsonObject updated = object(after, registry);
            if (!before.has(registry)) {
                out.add("+ registry " + registry + " with " + object(updated, "entries").size() + " entries");
            } else if (!after.has(registry)) {
                out.add("- registry " + registry);
                continue;
            }
            Map<String, Integer> oldEntries = ids(object(old, "entries"), "protocol_id");
            Map<String, Integer> newEntries = ids(object(updated, "entries"), "protocol_id");
            if (oldEntries.equals(newEntries) && Objects.equals(old.get("default"), updated.get("default"))) {
                continue;
            }
            out.add("## " + registry);
            summarise(out, oldEntries, newEntries);
            if (!Objects.equals(old.get("default"), updated.get("default"))) {
                out.add("default " + old.get("default") + " -> " + updated.get("default"));
            }
            out.add("");
        }
        return out;
    }

    private static List<String> blocks(JsonObject before, JsonObject after) {
        List<String> out = new ArrayList<>();
        int moved = 0;
        String firstMoved = null;
        for (String block : union(before, after)) {
            JsonObject old = object(before, block);
            JsonObject updated = object(after, block);
            if (!before.has(block)) {
                out.add("+ " + block + ", " + states(updated) + " states, first id " + firstId(updated)
                        + ", properties " + updated.get("properties"));
                TextDelta.flatten(object(updated, "definition")).forEach(line -> out.add("    definition " + line));
                continue;
            }
            if (!after.has(block)) {
                out.add("- " + block + ", " + states(old) + " states");
                continue;
            }
            if (firstId(old) != firstId(updated)) {
                moved++;
                if (firstMoved == null) {
                    firstMoved = block + " " + firstId(old) + " -> " + firstId(updated);
                }
            }
            if (!Objects.equals(old.get("properties"), updated.get("properties"))) {
                out.add("~ " + block + " properties " + old.get("properties") + " -> " + updated.get("properties"));
            } else if (states(old) != states(updated)) {
                out.add("~ " + block + " states " + states(old) + " -> " + states(updated));
            }
            if (!Objects.equals(defaultState(old), defaultState(updated))) {
                out.add("~ " + block + " default state " + defaultState(old) + " -> " + defaultState(updated));
            }
            List<String> definition = TextDelta.lineDelta(TextDelta.flatten(object(old, "definition")),
                    TextDelta.flatten(object(updated, "definition")));
            if (!definition.isEmpty()) {
                out.add("~ " + block + " definition");
                definition.forEach(line -> out.add("    " + line));
            }
        }
        out.addFirst("");
        out.addFirst("state ids moved for " + moved + " blocks, the first being " + firstMoved);
        return out;
    }

    private static void summarise(List<String> out, Map<String, Integer> before, Map<String, Integer> after) {
        int renumbered = 0;
        for (String name : union(before.keySet(), after.keySet())) {
            if (!before.containsKey(name)) {
                out.add("+ " + name + " at " + after.get(name));
            } else if (!after.containsKey(name)) {
                out.add("- " + name + " was " + before.get(name));
            } else if (!before.get(name).equals(after.get(name))) {
                renumbered++;
            }
        }
        out.add(renumbered + " renumbered, " + before.size() + " -> " + after.size() + " entries");
    }

    private static String marker(Map<String, Integer> before, Map<String, Integer> after, String name) {
        if (!before.containsKey(name)) {
            return "**added** ";
        }
        if (!after.containsKey(name)) {
            return "**removed** ";
        }
        return "";
    }

    private static String text(Integer id) {
        return id == null ? "" : "0x" + Integer.toHexString(id) + " (" + id + ")";
    }

    private static Map<String, Integer> ids(JsonObject object, String key) {
        Map<String, Integer> ids = new LinkedHashMap<>();
        object.entrySet().forEach(entry -> {
            if (entry.getValue().isJsonObject() && entry.getValue().getAsJsonObject().has(key)) {
                ids.put(entry.getKey(), entry.getValue().getAsJsonObject().get(key).getAsInt());
            }
        });
        return ids;
    }

    private static int states(JsonObject block) {
        return block.has("states") ? block.getAsJsonArray("states").size() : 0;
    }

    private static int firstId(JsonObject block) {
        return states(block) == 0 ? -1 : block.getAsJsonArray("states").get(0).getAsJsonObject().get("id").getAsInt();
    }

    private static JsonElement defaultState(JsonObject block) {
        if (!block.has("states")) {
            return null;
        }
        for (JsonElement state : block.getAsJsonArray("states")) {
            JsonObject object = state.getAsJsonObject();
            if (object.has("default") && object.get("default").getAsBoolean()) {
                return object.get("properties");
            }
        }
        return null;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : new JsonObject();
    }

    private static Set<String> union(JsonObject before, JsonObject after) {
        return union(before.keySet(), after.keySet());
    }

    private static Set<String> union(Set<String> before, Set<String> after) {
        Set<String> names = new TreeSet<>(before);
        names.addAll(after);
        return names;
    }
}
