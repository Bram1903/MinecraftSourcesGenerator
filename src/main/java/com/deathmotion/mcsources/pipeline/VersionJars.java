package com.deathmotion.mcsources.pipeline;

import com.deathmotion.mcsources.config.GeneratorConfig;
import com.deathmotion.mcsources.download.Downloader;
import com.deathmotion.mcsources.download.LibraryDownloader;
import com.deathmotion.mcsources.jar.JarAssembler;
import com.deathmotion.mcsources.jar.ServerBundle;
import com.deathmotion.mcsources.mapping.ResolvedMappings;
import com.deathmotion.mcsources.remap.JarRemapper;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public record VersionJars(Path client, Path serverBundle, Path server, Path merged, int classes,
                          List<Path> libraries) {

    public static VersionJars assemble(GeneratorConfig config, JsonObject metadata, ResolvedMappings mappings,
                                       Path scratch, Consumer<Phase> onPhase) throws IOException {
        onPhase.accept(Phase.DOWNLOAD);
        List<Path> libraries = LibraryDownloader.resolve(metadata, config.librariesDir());
        JsonObject downloads = metadata.getAsJsonObject("downloads");

        Path clientJar = Downloader.download(url(downloads, "client"), scratch.resolve("client.jar"));
        Path serverBundle = null;
        Path serverJar = null;
        if (downloads.has("server")) {
            serverBundle = Downloader.download(url(downloads, "server"), scratch.resolve("server.jar"));
            serverJar = ServerBundle.extractServerJar(serverBundle, scratch.resolve("server-extracted.jar"));
        }

        onPhase.accept(Phase.REMAP);
        int remapThreads = config.decompilerThreads();
        Path clientNamed = scratch.resolve("client-named.jar");
        JarRemapper.remap(clientJar, clientNamed, mappings.clientTree(), libraries, remapThreads);
        Path serverNamed = null;
        if (serverJar != null) {
            serverNamed = scratch.resolve("server-named.jar");
            JarRemapper.remap(serverJar, serverNamed, mappings.serverTree(), libraries, remapThreads);
        }

        onPhase.accept(Phase.MERGE);
        Path merged = scratch.resolve("minecraft.jar");
        int classes = JarAssembler.assemble(clientNamed, serverNamed, mappings.namedClasses(), merged);
        return new VersionJars(clientNamed, serverBundle, serverNamed, merged, classes, libraries);
    }

    private static String url(JsonObject downloads, String key) {
        return downloads.getAsJsonObject(key).get("url").getAsString();
    }
}
