package com.deathmotion.mcsources.decompile;

import com.deathmotion.mcsources.mapping.parchment.ParchmentData;

import java.util.List;

final class ParchmentJavadocSource implements JavadocSource {
    private final ParchmentData parchment;

    ParchmentJavadocSource(ParchmentData parchment) {
        this.parchment = parchment;
    }

    private static String join(List<String> lines) {
        return lines == null || lines.isEmpty() ? null : String.join("\n", lines);
    }

    @Override
    public String classDoc(String owner) {
        return join(parchment.classDoc(owner));
    }

    @Override
    public String fieldDoc(String owner, String field) {
        return join(parchment.fieldDoc(owner, field));
    }

    @Override
    public String methodDoc(String owner, String name, String descriptor) {
        return join(parchment.methodDoc(owner, name, descriptor));
    }
}
