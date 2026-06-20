package com.deathmotion.mcsources.decompile;

import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.util.Map;
import java.util.TreeMap;

public final class VineflowerOptions {
    private VineflowerOptions() {
    }

    public static Map<String, String> output() {
        Map<String, String> options = new TreeMap<>();
        options.put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1");
        options.put(IFernflowerPreferences.USE_METHOD_PARAMETERS, "1");
        options.put(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "1");
        options.put(IFernflowerPreferences.OVERRIDE_ANNOTATION, "1");
        options.put(IFernflowerPreferences.TERNARY_CONSTANT_SIMPLIFICATION, "1");
        options.put(IFernflowerPreferences.INDENT_STRING, " ".repeat(4));
        options.put("variable-renaming", "jad");
        return options;
    }

    public static String identity() {
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : output().entrySet()) {
            builder.append(entry.getKey()).append('=').append(entry.getValue()).append('\n');
        }
        return builder.toString();
    }
}
