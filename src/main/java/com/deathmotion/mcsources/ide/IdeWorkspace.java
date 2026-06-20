package com.deathmotion.mcsources.ide;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

public final class IdeWorkspace {
    private IdeWorkspace() {
    }

    public static void write(Path workspaceDir, String version, Path sourcesDir, List<Path> libraries)
            throws IOException {
        Files.createDirectories(workspaceDir);
        copyGradleWrapper(workspaceDir.getParent(), workspaceDir);
        Files.writeString(workspaceDir.resolve("settings.gradle.kts"),
                "rootProject.name = \"minecraft-" + version + "\"\n");

        boolean linked = linkSources(workspaceDir, sourcesDir);
        String sourceConfig = linked ? "" : """
                    java {
                        setSrcDirs(listOf("%s"))
                    }
                """.formatted(sourcesDir.toAbsolutePath());

        StringBuilder files = new StringBuilder();
        for (Path library : libraries) {
            files.append("        \"").append(library.toAbsolutePath()).append("\",\n");
        }

        String build = """
                plugins {
                    java
                }
                
                repositories {
                    mavenCentral()
                }
                
                sourceSets {
                    main {
                %s        resources {
                            setSrcDirs(emptyList<String>())
                        }
                    }
                }
                
                dependencies {
                    compileOnly("com.google.code.findbugs:jsr305:3.0.2")
                    compileOnly("org.jetbrains:annotations:26.1.0")
                    compileOnly(files(
                %s    ))
                }
                """.formatted(sourceConfig, files);
        Files.writeString(workspaceDir.resolve("build.gradle.kts"), build);
    }

    private static void copyGradleWrapper(Path projectRoot, Path workspaceDir) throws IOException {
        Path wrapperJar = projectRoot.resolve("gradle/wrapper/gradle-wrapper.jar");
        if (!Files.exists(wrapperJar)) {
            return;
        }
        copyFile(projectRoot.resolve("gradlew"), workspaceDir.resolve("gradlew"), true);
        copyFile(projectRoot.resolve("gradlew.bat"), workspaceDir.resolve("gradlew.bat"), false);
        copyFile(wrapperJar, workspaceDir.resolve("gradle/wrapper/gradle-wrapper.jar"), false);
        copyFile(projectRoot.resolve("gradle/wrapper/gradle-wrapper.properties"),
                workspaceDir.resolve("gradle/wrapper/gradle-wrapper.properties"), false);
    }

    private static void copyFile(Path source, Path target, boolean executable) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        Files.createDirectories(target.getParent());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        if (executable) {
            target.toFile().setExecutable(true, false);
        }
    }

    private static boolean linkSources(Path workspaceDir, Path sourcesDir) throws IOException {
        Path javaRoot = workspaceDir.resolve("src").resolve("main").resolve("java");
        Files.createDirectories(javaRoot.getParent());
        try {
            Files.deleteIfExists(javaRoot);
            Files.createSymbolicLink(javaRoot, sourcesDir.toAbsolutePath());
            return true;
        } catch (IOException | UnsupportedOperationException e) {
            return false;
        }
    }
}
