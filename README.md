# Minecraft Sources Generator

Generates readable, decompiled Minecraft: Java Edition source code for every release since 1.7.2 into
`sources/<version>/`, one directory per version. There is no upper bound. When a new release ships, run
`generateAll` again and it is picked up automatically. Pure JVM, no Fabric or Loom required.

## Disclaimer

This repository contains only the generator, the tooling that produces the sources. It does not contain, and
never commits, any Minecraft code, assets, or mappings.

Mojang permits decompiling Minecraft locally for personal use. The decompiled sources and the mappings used to
produce them may not be redistributed or published. For that reason:

- The `sources/` output is git-ignored and stays on your machine.
- Downloaded mappings and jars are cached under `work/`, which is also git-ignored.
- Running the generator requires accepting the Minecraft EULA (see Usage).

Use the generated code for local, personal study and development only. See the Minecraft Wiki page
[See Minecraft's code](https://minecraft.wiki/w/Tutorial:See_Minecraft%27s_code) for background.

## What it produces

```
sources/
├── 1.7.2/
│   └── net/minecraft/...          (.java files)
├── 1.16.5/
│   ├── com/mojang/...
│   └── net/minecraft/...
├── 26.2/
│   ├── com/mojang/...
│   └── net/minecraft/...
└── ...                            (every release since 1.7.2, 83 today, about 2 GB)
    └── .mcsg-done                 (marker with tier, mapping build, file and class counts, timestamp)
```

Each version is the merged client and server (client wins on conflict), filtered to genuine Minecraft classes
so bundled third-party libraries are excluded, then decompiled with
[Vineflower](https://github.com/Vineflower/vineflower). The vanilla (26.x) output matches the file set that
Fabric Loom produces.

## Mappings

Minecraft was obfuscated for most of its history, so readable names come from different sources per era. The
tier is chosen automatically per version, with no hard-coded version lists.

| Versions          | Naming source                        | Detection |
|-------------------|--------------------------------------|-----------|
| 1.7.2 to 1.14.3   | Ornithe Feather (`mergedv2`)         | published on maven.ornithemc.net (covers every patch release) |
| 1.14.4 to 1.21.x  | Mojang official mappings (Mojmap)    | version metadata contains `client_mappings` |
| 26.1 and later    | none, shipped unobfuscated           | no mappings published anywhere, the jars already use real names |

Since Minecraft 26.1 the game is distributed unobfuscated, so those versions are decompiled straight from the
jar with no remapping step. Run `./gradlew listVersions` to print the resolved list and the tier chosen for
each version.

On the Mojang-mapped versions, parameter names and javadocs are layered in from
[Parchment](https://parchmentmc.org/) wherever a release exists for that version (currently 1.16.5 through the
latest 1.21.x). Mojang's official mappings carry no parameter names or documentation, so this is a large
readability gain. Parameter names are written into the jar before decompilation, and javadocs are injected by
the decompiler. Mojang-mapped versions without Parchment fall back to type-based parameter names.

On the Ornithe Feather versions (1.7.2 to 1.14.3), parameter names come from the Feather mappings directly, and
the javadocs that Feather ships are injected the same way. So every era gets the best names and documentation
its mappings can provide.

## Usage

```bash
# List every version and the mapping source chosen for each (no EULA needed)
./gradlew listVersions

# Generate a single version
./gradlew generateVersion -Pmc=1.16.5 -Peula=true

# Generate an inclusive range of versions
./gradlew generateVersion -Pmc=1.16.5-1.18 -Peula=true

# Generate all releases in parallel (resumable, skips finished versions)
./gradlew generateAll -Peula=true

# Build an IDE workspace for one version (generates it first if needed)
./gradlew ide -Pmc=1.21.8 -Peula=true
```

`generateVersion -Pmc=<from>-<to>` generates every version between the two endpoints, inclusive, in parallel
(for example `1.16.5-1.18` covers 1.16.5, 1.17, 1.17.1 and 1.18). Endpoints are matched against the version list,
so they must be plain release ids unless `-Psnapshots` is set.

`generateAll` and ranges print a progress line after each version finishes, for example
`[progress] 42/83 done (50%), 250s elapsed, ~245s remaining`, so a long run shows how far along it is.

### Browsing a version in an IDE

Every version uses the same `net.minecraft` packages, so they cannot all live in one project. `./gradlew ide
-Pmc=<version>` writes a small standalone Gradle project to `workspace/` whose `src/main/java` is linked to that
version's sources, with that version's libraries on the classpath. It also adds the compile-only annotation
libraries Mojang references but does not ship at runtime (JSR-305 and JetBrains annotations), so imports like
`javax.annotation.Nullable` resolve.

**Open the `workspace/` folder on its own, not the generator repo.** Opening the generator repo shows every
version under `sources/` with no resolution. In IntelliJ use File > Open, select the `workspace/` folder, and
choose Open in New Window. You then see only that one version, with full symbol resolution, go-to-definition,
find-usages and references.

The workspace includes its own Gradle wrapper copied from the root project, so it uses the same Gradle version.
Running `./gradlew ide` again for a different version repoints the same `workspace/`. `workspace/` is git-ignored.
Decompiled code is not guaranteed to compile, so some inspections may flag issues, but navigation and search work.

### Accepting the Minecraft EULA

Generation is gated behind the Minecraft EULA, the same way a Minecraft server is. Accept it in one of two ways:

- Pass `-Peula=true`, which writes or updates `eula.txt`.
- Edit `eula.txt` (created on first run) and set `eula=true`.

`eula.txt` is git-ignored, so acceptance is per machine. `listVersions` does not require it.

### Flags

| Flag             | Task          | Meaning |
|------------------|---------------|---------|
| `-Peula=true`    | generate      | Accept the Minecraft EULA, required to generate |
| `-Pforce`        | generate      | Rebuild even if `sources/<v>/.mcsg-done` exists |
| `-Pjobs=N`       | generateAll   | Number of versions decompiled in parallel (default is about cores/4, capped at 8) |
| `-PvfThreads=N`  | generate      | Vineflower threads per version (default is about cores/jobs) |
| `-PvfHeap=4g`    | generate      | Heap for each Vineflower subprocess (default 3g) |
| `-Pfrom=1.16`    | generateAll   | Start at this version, inclusive |
| `-Pto=1.20.1`    | generateAll   | Stop at this version, inclusive |
| `-Plimit=5`      | generateAll   | Only the first N versions of the selection |
| `-Psnapshots`    | all tasks     | Include snapshots, pre-releases, and release candidates (default is releases only) |
| `-PkeepWork`     | generate      | Keep the per-version scratch dir under `work/tmp/<v>/` |
| `-PnoCache`      | generate      | Turn the decompile cache off for a fully canonical, reproducible decompile (cache is on by default, see note below) |
| `-PresetCache`   | generate      | Delete the decompile cache before running |

The decompile cache stores decompiled classes keyed by their content (bytecode, javadocs, and super-class
hierarchy) and reuses them across versions and across runs, so an unchanged class is decompiled once and then
shared everywhere it appears. It is on by default: a full `generateAll` decompiles more and more from cache as it
proceeds, and adding a later release is fast because almost every class is already cached. The one trade off is
that the output is not byte for byte identical to a cache free decompile. A changed class that references an
unchanged class's nested type renders that reference with an import and a simple name rather than the qualified
form (for example `Mutable` rather than `BlockPos.Mutable`), because reused classes sit on the classpath rather
than in the decompile set. The code stays valid, equivalent, and navigable, and a given class is identical
everywhere it appears regardless of run order. Pass `-PnoCache` for a fully canonical decompile, and
`-PresetCache` to discard the cache first.

Parallelism note: `generateAll` runs `jobs` versions at once, each Vineflower using `vfThreads`. The defaults
aim for `jobs * vfThreads` near the CPU core count. Peak memory is about `jobs * vfHeap` plus the build JVM, so
lower `-Pjobs` or `-PvfHeap` on memory-constrained machines.

## How it works

For each version the generator:

1. Reads the Mojang piston version manifest and per-version metadata.
2. Picks the mapping tier and downloads the mapping artifact when needed.
3. Downloads `client.jar` and `server.jar`, unwrapping the 1.18 and later server bundler to reach the real
   server jar.
4. Remaps client and server independently to named names with
   [tiny-remapper](https://github.com/FabricMC/tiny-remapper) and
   [mapping-io](https://github.com/FabricMC/mapping-io). Mojang ProGuard files are converted on the fly, and
   Ornithe uses its `official` to `named` `mergedv2`. For the unobfuscated 26.1 and later versions this step is
   a verbatim copy.
5. Merges named client and server, keeping only real Minecraft classes.
6. Fixes parameter annotations on enum and inner-class constructors with ASM, so an annotation like
   `@Nullable` lands on the parameter it belongs to. Older compilers counted the synthetic leading parameters
   (an enum's name and ordinal, an inner class's enclosing instance), which shifted every following annotation
   by one slot. This runs on every version and is a no-op where the bytecode is already correct.
7. On Mojang-mapped versions, writes parameter names into the merged jar with ASM, using Parchment names where
   they exist and type-derived names (matching the decompiler's local-variable style) everywhere else, so no
   parameter is left unnamed. Feather versions already carry their parameter names through the remap step.
8. Decompiles with Vineflower in a separate JVM, with the version libraries supplied as context and javadocs
   injected through the decompiler (Parchment on Mojmap, Feather on the legacy versions).
9. Writes the `.java` tree to `sources/<version>/` and a `.mcsg-done` marker.

Downloaded jars, libraries, and mappings are cached under `work/` and shared across versions, so re-runs and
resumes are fast.

## Requirements

- A JDK. The Gradle toolchain targets Java 25.
- Network access to Mojang, Maven Central, maven.fabricmc.net (build dependencies), maven.ornithemc.net, and
  maven.parchmentmc.org.
- A few GB of free disk for `sources/` and `work/`.

## Project layout

```
build.gradle.kts                 plugins, dependencies (from the version catalog), tasks
gradle/libs.versions.toml        dependency versions
src/main/java/com/deathmotion/mcsources/
├── cli/             Main                                CLI entry for list, one, all
├── config/          GeneratorConfig, RunOptions, Eula   configuration and EULA gate
├── manifest/        VersionManifest, MinecraftVersion   Mojang manifest and metadata
├── mapping/         MappingResolver, MappingTier,       tier resolution and loading
│   │                ResolvedMappings, MappingPlan, MappingArtifact
│   └── parchment/   ParchmentResolver, ParchmentData    Parchment names and javadocs
├── download/        Downloader, LibraryDownloader       HTTP and library fetching
├── remap/           JarRemapper                         tiny-remapper wrapper
├── jar/             ServerBundle, MinecraftClassFilter, JarAssembler, ParameterNameInjector, ParameterAnnotationFixer
├── decompile/       VineflowerDecompiler, VineflowerWorker, ParchmentJavadocProvider, TinyCommentJavadocProvider
├── pipeline/        SourceGenerator, GenerationResult, GenerationOutcome, GenerationSummary
├── ide/             IdeWorkspace                        generates the per-version IDE project
└── util/            PathUtils
```
