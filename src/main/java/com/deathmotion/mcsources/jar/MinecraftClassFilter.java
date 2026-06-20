package com.deathmotion.mcsources.jar;

import java.util.Set;

public final class MinecraftClassFilter {
    private static final String CLASS_SUFFIX = ".class";

    private final Set<String> namedClasses;

    public MinecraftClassFilter(Set<String> namedClasses) {
        this.namedClasses = namedClasses;
    }

    public boolean accept(String entryName) {
        if (!entryName.endsWith(CLASS_SUFFIX)) {
            return false;
        }
        String internalName = entryName.substring(0, entryName.length() - CLASS_SUFFIX.length());
        if (namedClasses != null) {
            int dollar = internalName.indexOf('$');
            String topLevel = dollar >= 0 ? internalName.substring(0, dollar) : internalName;
            return namedClasses.contains(topLevel);
        }
        return internalName.startsWith("net/minecraft/") || internalName.startsWith("com/mojang/");
    }
}
