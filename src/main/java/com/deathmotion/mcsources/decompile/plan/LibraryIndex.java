package com.deathmotion.mcsources.decompile.plan;

import com.deathmotion.mcsources.classfile.JarClasses;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class LibraryIndex implements AutoCloseable {
    private final Map<Path, Library> libraries = new ConcurrentHashMap<>();

    Map<String, Library> owners(List<Path> jars) {
        Map<String, Library> owners = new HashMap<>();
        for (Path jar : jars) {
            Library library = libraries.computeIfAbsent(jar, Library::open);
            for (String name : library.classes()) {
                owners.putIfAbsent(name, library);
            }
        }
        return owners;
    }

    @Override
    public void close() throws IOException {
        for (Library library : libraries.values()) {
            library.zip.close();
        }
        libraries.clear();
    }

    static final class Library {
        private final ZipFile zip;
        private final Set<String> classes;
        private final Map<String, ClassStructure> structures = new ConcurrentHashMap<>();

        private Library(ZipFile zip, Set<String> classes) {
            this.zip = zip;
            this.classes = classes;
        }

        private static Library open(Path jar) {
            try {
                ZipFile zip = new ZipFile(jar.toFile());
                Set<String> classes = zip.stream()
                        .map(JarClasses::classNameOf)
                        .filter(Objects::nonNull)
                        .collect(Collectors.toUnmodifiableSet());
                return new Library(zip, classes);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read library " + jar, e);
            }
        }

        Set<String> classes() {
            return classes;
        }

        ClassStructure structure(String name) {
            return structures.computeIfAbsent(name, this::read);
        }

        private ClassStructure read(String name) {
            ZipEntry entry = zip.getEntry(JarClasses.entryName(name));
            if (entry == null) {
                return ClassStructure.ABSENT;
            }
            try (InputStream in = zip.getInputStream(entry)) {
                return ClassStructure.of(in.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
