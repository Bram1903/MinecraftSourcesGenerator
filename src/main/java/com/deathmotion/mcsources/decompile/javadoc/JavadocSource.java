package com.deathmotion.mcsources.decompile.javadoc;

import com.deathmotion.mcsources.mapping.parchment.ParchmentData;

import java.io.IOException;
import java.nio.file.Path;

public interface JavadocSource {
    static JavadocSource parchment(ParchmentData parchment) {
        return new ParchmentJavadocSource(parchment);
    }

    static JavadocSource tiny(Path tiny) throws IOException {
        return TinyJavadocSource.load(tiny);
    }

    String classDoc(String owner);

    String fieldDoc(String owner, String field);

    String methodDoc(String owner, String name, String descriptor);
}
