package com.deathmotion.mcsources.classfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class JarClasses {
    private static final String CLASS_SUFFIX = ".class";
    private static final String META_INF = "META-INF/";

    private JarClasses() {
    }

    public static Map<String, byte[]> read(Path jar) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String className = classNameOf(entry);
                if (className == null) {
                    continue;
                }
                try (InputStream in = zip.getInputStream(entry)) {
                    classes.put(className, in.readAllBytes());
                }
            }
        }
        return classes;
    }

    public static String classNameOf(ZipEntry entry) {
        String name = entry.getName();
        if (entry.isDirectory() || !name.endsWith(CLASS_SUFFIX) || name.startsWith(META_INF)) {
            return null;
        }
        return name.substring(0, name.length() - CLASS_SUFFIX.length());
    }

    public static String entryName(String className) {
        return className + CLASS_SUFFIX;
    }
}
