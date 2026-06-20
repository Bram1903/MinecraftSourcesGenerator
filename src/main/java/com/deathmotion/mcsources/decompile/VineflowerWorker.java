package com.deathmotion.mcsources.decompile;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler;
import org.jetbrains.java.decompiler.main.decompiler.PrintStreamLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger;
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class VineflowerWorker {
    private VineflowerWorker() {
    }

    static void main(String[] args) throws Exception {
        File outputDir = new File(args[0]);
        File input = new File(args[1]);
        String threads = args[2];
        String javadocSpec = args[3];

        Map<String, Object> options = new HashMap<>(IFernflowerPreferences.getDefaults());
        options.putAll(VineflowerOptions.output());
        options.put(IFernflowerPreferences.LOG_LEVEL, "warn");
        options.put(IFernflowerPreferences.THREADS, threads);

        IFabricJavadocProvider javadoc = javadocProvider(javadocSpec);
        if (javadoc != null) {
            options.put(IFabricJavadocProvider.PROPERTY_NAME, javadoc);
        }

        IFernflowerLogger logger = new PrintStreamLogger(System.out);
        try (FolderDecompiler decompiler = new FolderDecompiler(outputDir, options, logger)) {
            decompiler.addSource(input);
            for (int i = 4; i < args.length; i++) {
                decompiler.addLibrary(new File(args[i]));
            }
            decompiler.decompileContext();
        }
    }

    private static IFabricJavadocProvider javadocProvider(String spec) throws Exception {
        JavadocSource source = JavadocSource.fromSpec(spec);
        return source == null ? null : new JavadocSourceProvider(source);
    }

    private static final class FolderDecompiler extends ConsoleDecompiler {
        private FolderDecompiler(File destination, Map<String, Object> options, IFernflowerLogger logger) {
            super(destination, options, logger, SaveType.FOLDER);
        }
    }
}
