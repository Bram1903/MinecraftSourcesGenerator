package com.deathmotion.mcsources.decompile;

import com.deathmotion.mcsources.mapping.parchment.ParchmentData;

import java.io.IOException;
import java.nio.file.Path;

public interface JavadocSource {
    static JavadocSource fromSpec(String spec) throws IOException {
        if (spec == null) {
            return null;
        }
        if (spec.startsWith("parchment:")) {
            return new ParchmentJavadocSource(ParchmentData.load(Path.of(spec.substring("parchment:".length()))));
        }
        if (spec.startsWith("tiny:")) {
            return TinyJavadocSource.load(Path.of(spec.substring("tiny:".length())));
        }
        return null;
    }

    String classDoc(String owner);

    String fieldDoc(String owner, String field);

    String methodDoc(String owner, String name, String descriptor);
}
