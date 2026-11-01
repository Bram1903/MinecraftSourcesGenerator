package com.deathmotion.mcsources.decompile.vineflower;

import com.deathmotion.mcsources.classfile.InternalNames;
import com.deathmotion.mcsources.decompile.javadoc.JavadocSource;
import com.deathmotion.mcsources.io.Hashing;
import com.deathmotion.mcsources.version.PreparedVersion;
import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import org.jetbrains.java.decompiler.main.Fernflower;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;
import org.jetbrains.java.decompiler.main.extern.IResultSaver;
import org.jetbrains.java.decompiler.struct.StructClass;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.jar.Manifest;

public final class VineflowerRun {
    private static final String IDENTITY = identify();

    private VineflowerRun() {
    }

    public static String decompiler() {
        return IDENTITY;
    }

    public static Map<String, Map<String, String>> decompile(PreparedVersion version, Predicate<String> groups,
                                                             int threads, Path log) throws IOException {
        Files.createDirectories(log.getParent());
        Map<String, Map<String, String>> output = new ConcurrentHashMap<>();
        List<MemoryClassSource> passes = passes(version, output);
        try (OutputStream out = Files.newOutputStream(log);
             PrintStream logStream = new PrintStream(out, true, StandardCharsets.UTF_8)) {
            FailureAwareLogger logger = new FailureAwareLogger(logStream);
            SelectiveFernflower engine = new SelectiveFernflower(
                    options(version.javadocs(), version.renameLocals(), threads), logger, groups);
            try {
                passes.forEach(engine::addSource);
                for (Path library : version.libraries()) {
                    engine.addLibrary(library.toFile());
                }
                engine.decompileContext();
            } finally {
                engine.clearContext();
            }
            logger.rethrowResourceFailure(version.id());
        }
        return output;
    }

    private static List<MemoryClassSource> passes(PreparedVersion version, Map<String, Map<String, String>> output) {
        Set<String> tables = SwitchTables.standalone(version.classes());
        SortedMap<String, byte[]> first = new TreeMap<>();
        SortedMap<String, byte[]> second = new TreeMap<>();
        version.classes().forEach((name, bytes) ->
                (tables.contains(InternalNames.topLevel(name)) ? first : second).put(name, bytes));
        List<MemoryClassSource> passes = new ArrayList<>();
        if (!first.isEmpty()) {
            passes.add(new MemoryClassSource(version.id() + " switch tables", first, output));
        }
        passes.add(new MemoryClassSource(version.id(), second, output));
        return passes;
    }

    private static String identify() {
        try {
            Path jar = Path.of(Fernflower.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String name = jar.getFileName().toString();
            return Files.isRegularFile(jar) ? name + "@" + Hashing.sha256Hex(Files.readAllBytes(jar)) : name;
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("Cannot identify the Vineflower build", e);
        }
    }

    private static Map<String, Object> options(JavadocSource javadocs, boolean renameLocals, int threads) {
        Map<String, Object> options = new HashMap<>(IFernflowerPreferences.getDefaults());
        options.putAll(VineflowerOptions.output(renameLocals));
        options.put(IFernflowerPreferences.LOG_LEVEL, "warn");
        options.put(IFernflowerPreferences.THREADS, String.valueOf(threads));
        if (javadocs != null) {
            options.put(IFabricJavadocProvider.PROPERTY_NAME, new JavadocSourceProvider(javadocs));
        }
        return options;
    }

    private static final class SelectiveFernflower extends Fernflower {
        private final Predicate<String> groups;

        private SelectiveFernflower(Map<String, Object> options, IFernflowerLogger logger, Predicate<String> groups) {
            super(new DiscardingSaver(), options, logger);
            this.groups = groups;
        }

        @Override
        public String getClassEntryName(StructClass structClass, String entryName) {
            if (!groups.test(InternalNames.topLevel(structClass.qualifiedName))) {
                return null;
            }
            return super.getClassEntryName(structClass, entryName);
        }
    }

    private static final class FailureAwareLogger extends PrintStreamLogger {
        private volatile OutOfMemoryError resourceFailure;

        private FailureAwareLogger(PrintStream stream) {
            super(stream);
        }

        @Override
        public void writeMessage(String message, Severity severity, Throwable throwable) {
            for (Throwable cause = throwable; cause != null; cause = cause.getCause()) {
                if (cause instanceof OutOfMemoryError error) {
                    resourceFailure = error;
                }
            }
            super.writeMessage(message, severity, throwable);
        }

        private void rethrowResourceFailure(String version) throws IOException {
            if (resourceFailure != null) {
                throw new IOException("Vineflower ran out of memory while decompiling " + version
                        + ", nothing from this run was kept", resourceFailure);
            }
        }
    }

    private static final class DiscardingSaver implements IResultSaver {
        @Override
        public void saveFolder(String path) {
        }

        @Override
        public void copyFile(String source, String path, String entryName) {
        }

        @Override
        public void saveClassFile(String path, String qualifiedName, String entryName, String content,
                                  int[] mapping) {
        }

        @Override
        public void createArchive(String path, String archiveName, Manifest manifest) {
        }

        @Override
        public void saveDirEntry(String path, String archiveName, String entryName) {
        }

        @Override
        public void copyEntry(String source, String path, String archiveName, String entry) {
        }

        @Override
        public void saveClassEntry(String path, String archiveName, String qualifiedName, String entryName,
                                   String content) {
        }

        @Override
        public void closeArchive(String path, String archiveName) {
        }
    }
}
