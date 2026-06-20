package com.deathmotion.mcsources.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Eula {
    public static final String EULA_URL = "https://aka.ms/MinecraftEULA";

    private static final String FILE_NAME = "eula.txt";
    private static final String HEADER = """
            # Minecraft Sources Generator
            # By setting eula=true you confirm that you agree to the Minecraft End User License Agreement
            # (%s). Minecraft may only be decompiled for PERSONAL, LOCAL use. The generated sources and the
            # mappings used to produce them must NOT be redistributed or published. See README.md.
            """.formatted(EULA_URL);

    private final Path eulaFile;
    private final boolean acceptedViaFlag;

    public Eula(Path projectDir, boolean acceptedViaFlag) {
        this.eulaFile = projectDir.resolve(FILE_NAME);
        this.acceptedViaFlag = acceptedViaFlag;
    }

    public static Eula fromSystemProperties() {
        Path projectDir = Path.of(System.getProperty("mcsg.projectDir", "."));
        boolean acceptedViaFlag = Boolean.parseBoolean(System.getProperty("mcsg.eula", "false"));
        return new Eula(projectDir, acceptedViaFlag);
    }

    public boolean ensureAccepted() throws IOException {
        if (acceptedViaFlag) {
            write(true);
            System.out.println("Minecraft EULA accepted via -Peula=true (recorded in " + eulaFile + ").");
            return true;
        }
        if (isAcceptedInFile()) {
            return true;
        }
        if (!Files.exists(eulaFile)) {
            write(false);
        }
        printRejectionNotice();
        return false;
    }

    private boolean isAcceptedInFile() throws IOException {
        if (!Files.exists(eulaFile)) {
            return false;
        }
        for (String line : Files.readAllLines(eulaFile)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.replace(" ", "").equalsIgnoreCase("eula=true")) {
                return true;
            }
        }
        return false;
    }

    private void write(boolean accepted) throws IOException {
        Files.writeString(eulaFile, HEADER + "eula=" + accepted + System.lineSeparator());
    }

    private void printRejectionNotice() {
        System.out.println();
        System.out.println("You must accept the Minecraft EULA before generating sources.");
        System.out.println("Minecraft may only be decompiled locally for personal use. The output must not be shared.");
        System.out.println("EULA: " + EULA_URL);
        System.out.println();
        System.out.println("Accept by either:");
        System.out.println("  - editing " + eulaFile + " and setting eula=true, or");
        System.out.println("  - re-running the task with -Peula=true");
        System.out.println();
    }
}
