package com.deathmotion.mcsources.version;

import com.deathmotion.mcsources.classfile.InternalNames;

import java.util.Set;

final class MinecraftClassFilter {
    private final Set<String> namedClasses;

    MinecraftClassFilter(Set<String> namedClasses) {
        this.namedClasses = namedClasses;
    }

    boolean accept(String internalName) {
        if (namedClasses != null) {
            return namedClasses.contains(InternalNames.topLevel(internalName));
        }
        return internalName.startsWith("net/minecraft/") || internalName.startsWith("com/mojang/");
    }
}
