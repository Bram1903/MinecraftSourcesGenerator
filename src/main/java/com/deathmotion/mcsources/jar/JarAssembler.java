package com.deathmotion.mcsources.jar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class JarAssembler {
    private JarAssembler() {
    }

    public static int assemble(Path clientNamed, Path serverNamed, Set<String> namedClasses, Path output)
            throws IOException {
        Files.createDirectories(output.getParent());
        MinecraftClassFilter filter = new MinecraftClassFilter(namedClasses);
        Set<String> written = new HashSet<>();
        int count;
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(output))) {
            count = copyMatching(clientNamed, filter, written, out);
            count += copyMatching(serverNamed, filter, written, out);
        }
        return count;
    }

    private static int copyMatching(Path jar, MinecraftClassFilter filter, Set<String> written,
                                    ZipOutputStream out) throws IOException {
        if (jar == null || !Files.exists(jar)) {
            return 0;
        }
        int count = 0;
        byte[] buffer = new byte[8192];
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(jar))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if (entry.isDirectory() || written.contains(name) || !filter.accept(name)) {
                    continue;
                }
                written.add(name);
                out.putNextEntry(new ZipEntry(name));
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                out.closeEntry();
                count++;
            }
        }
        return count;
    }
}
