package com.deathmotion.mcsources.classfile;

public final class InternalNames {
    private InternalNames() {
    }

    public static String topLevel(String internalName) {
        int dollar = internalName.indexOf('$');
        return dollar >= 0 ? internalName.substring(0, dollar) : internalName;
    }

    public static String packageOf(String internalName) {
        int slash = internalName.lastIndexOf('/');
        return slash >= 0 ? internalName.substring(0, slash) : "";
    }
}
