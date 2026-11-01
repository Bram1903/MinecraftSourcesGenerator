# Minecraft Sources Generator

Generates readable, decompiled Minecraft: Java Edition source code for every release since 1.7.2, one directory
per version under `sources/<version>/`. There is no upper bound: when Mojang ships a release, run `generateAll`
again and it is picked up. Pure JVM, no Fabric, Loom or Forge toolchain needed.

```bash
./gradlew generateVersion -Pmc=1.21.8 -Peula=true    # one version
./gradlew generateAll -Peula=true                    # every release, resumable
./gradlew ide -Pmc=1.21.8 -Peula=true                # open it in IntelliJ with full navigation
./gradlew diffVersions -Pold=26.2 -Pnew=26.3 -Peula=true
```

On Windows PowerShell, quote the version arguments: `.\gradlew generateVersion "-Pmc=1.21.8" -Peula=true`.
See [Windows](#windows).

## Disclaimer

This repository contains only the generator. It does not contain, and never commits, any Minecraft code, assets
or mappings.

Mojang permits decompiling Minecraft locally for personal use. The decompiled sources and the mappings used to
produce them may not be redistributed or published. For that reason:

- `sources/`, `diffs/` and `workspace/` are git-ignored and stay on your machine.
- Downloads and caches live under `work/`, also git-ignored.
- Generating requires accepting the Minecraft EULA (see [Accepting the EULA](#accepting-the-minecraft-eula)).

Use the generated code for local, personal study and development only. The Minecraft Wiki page
[See Minecraft's code](https://minecraft.wiki/w/Tutorial:See_Minecraft%27s_code) has background.

## What it produces

```
sources/
├── 1.7.2/
│   ├── net/minecraft/...          .java files
│   └── .mcsg-done                 what the version was built from (see below)
├── 1.21.8/
│   ├── com/mojang/...
│   └── net/minecraft/...
├── 26.3/
└── ...                            every release since 1.7.2, about 2.2 GB for all of them
```

Each version is the merged client and server (client wins where both ship a class), filtered to Minecraft's own
classes so bundled third-party libraries are left out, then decompiled with
[Vineflower](https://github.com/Vineflower/vineflower). Only `.java` files are written. Assets and data files
stay in the jars (`diffVersions` compares them).

`.mcsg-done` records the mapping tier, the mappings and Parchment build, the decompiler, the generator's output
revision, the class and file counts and the time:

```
version=1.20
revision=2
tier=MOJMAP
mappings=mojang official
parchment=2023.09.03 (borrowed from 1.20.1)
decompiler=vineflower-1.12.0.jar
classes=7436
javaFiles=4786
generatedAt=2026-11-01T16:24:09.701412998Z
```

## Names: mappings, parameters and locals

Minecraft was obfuscated for most of its history, so readable names come from a different source per era. The
tier is chosen automatically from the version's metadata, with no hard-coded version lists.

| Versions          | Class, method and field names      | How it is detected |
|-------------------|------------------------------------|--------------------|
| 1.7.2 to 1.14.3   | Ornithe Feather (`mergedv2`), Yarn style | published on maven.ornithemc.net for that version |
| 1.14.4 to 1.21.x  | Mojang official mappings (Mojmap)  | the version metadata has `client_mappings` |
| 26.1 and later    | Mojang's own, shipped unobfuscated | neither of the above, the jar already has real names |

Parameter names, local variable names and javadocs come from the best source each era has:

| Era | Parameters | Locals | Javadocs |
|-----|-----------|--------|----------|
| 26.1 and later | Mojang's own | Mojang's own | none (Mojang ships none) |
| Mojmap with a Parchment release (1.16.5 to the latest 1.21.x) | [Parchment](https://parchmentmc.org/) | decompiler assigned (`blockpos`, `i`) | Parchment |
| Mojmap without one (1.18, 1.19, 1.19.1, 1.20, 1.21.2) | Parchment of the nearest release in the same line | decompiler assigned | the same Parchment |
| Mojmap before Parchment (1.14.4 to 1.16.4) | derived from the type (`blockpos`, `level`, `clazz`) | decompiler assigned | none |
| Ornithe Feather | Feather | decompiler assigned | Feather |

Notes on fidelity:

- **26.1 and later keep Mojang's real local variable names.** The jars carry a local variable table, and the
  decompiler's renaming is switched off for them, so you read `ItemStack helmet` rather than `ItemStack itemstack`.
- **Borrowed Parchment** is matched by exact owner, name and descriptor, so a method that changed between the
  two patch releases gets no borrowed name. The marker says `borrowed from <version>` when it applies. 1.20
  borrows from 1.20.1, a hotfix of it, for example.
- **Type derived names** follow the decompiler's own lowercase style so parameters and locals read alike, and
  never produce a Java keyword (`Class<?> clazz`, `Boolean obool`).
- Parameter annotations on enum and inner-class constructors are realigned before decompiling, so `@Nullable`
  sits on the parameter it belongs to. Older compilers counted the synthetic leading parameters and shifted it.

Run `./gradlew listVersions` to print every version and the tier chosen for it.

## Usage

```bash
# List every version and the mapping source for each (no EULA needed)
./gradlew listVersions
./gradlew listVersions -Psnapshots

# One version, or an inclusive range generated in parallel
./gradlew generateVersion -Pmc=1.16.5 -Peula=true
./gradlew generateVersion -Pmc=1.16.5-1.18 -Peula=true

# Every release, in parallel, skipping versions that are already up to date
./gradlew generateAll -Peula=true
./gradlew generateAll -Pfrom=1.20 -Pto=1.21.8 -Peula=true

# An IDE workspace for one version (generates it first if needed)
./gradlew ide -Pmc=1.21.8 -Peula=true

# Compare two versions class by class, member by member, with resources and server reports
./gradlew diffVersions -Pold=26.2 -Pnew=26.3 -Peula=true
```

A range (`-Pmc=1.16.5-1.18`) covers every release between the two endpoints, inclusive: here 1.16.5, 1.17,
1.17.1 and 1.18. With `-Psnapshots`, snapshot and pre-release ids work as endpoints too, dashes included
(`-Pmc=1.21-pre1-1.21`).

`generateAll` and ranges print a line as each version finishes, and a status line when 15 seconds pass without
one finishing:

```
[progress] 42/88 (47%) 1.19.4 done in 61s, 250s elapsed, ~273s remaining
[status] 265s elapsed, 42/88 done, 6 running | remapping x1, decompiling x5
```

The run exits with a non-zero code when any version failed, and the summary lists each failure with its cause.

### Staying up to date

A version counts as finished when its `.mcsg-done` carries the generator's current output revision. When an
update to the generator changes what the sources look like (a decompiler option, a naming fix), the revision is
raised, and the next `generateAll` rebuilds the versions made by the older revision. The decompile cache keeps
that fast for every class the change did not touch. `-Pforce` rebuilds regardless.

### Browsing a version in an IDE

Every version uses the same `net.minecraft` packages, so they cannot share one project. `./gradlew ide
-Pmc=<version>` writes a small standalone Gradle project to `workspace/` whose `src/main/java` is that version's
sources, with that version's libraries on the classpath. It also adds the compile-only annotation libraries
Mojang references but does not ship (JSR-305 and JetBrains annotations), so `javax.annotation.Nullable` resolves.

**Open the `workspace/` folder on its own, not the generator repository.** In IntelliJ use File > Open, pick
`workspace/`, and choose Open in New Window. You then see just that version, with go-to-definition, find-usages
and references.

`src/main/java` is a symbolic link to `sources/<version>/`. Where links are not allowed (Windows without
Developer Mode), the build script points its source directory at `sources/<version>/` instead, which works the
same. The workspace gets a copy of the root Gradle wrapper. Running `./gradlew ide` for another version repoints
the same `workspace/`. Decompiled code is not guaranteed to compile, so some inspections may complain, but
navigation and search work.

### Comparing two versions

`./gradlew diffVersions -Pold=<a> -Pnew=<b>` answers "what moved" between two releases and writes it to
`diffs/<a>..<b>/`. It compares the jars rather than decompiled text, so decompiler formatting never shows up as a
change.

- **Classes are compared as bytecode.** Every method body is normalised before it is fingerprinted: line numbers,
  frames and constant pool order are ignored, labels are renumbered, a synthetic lambda body is folded into the
  method that creates it, and anonymous classes are matched by content. Renumbering a lambda or an anonymous
  class is not a change, and editing a lambda shows up on the method that owns it.
- **Members are classified** as added, removed, a body change, a descriptor change, a rename (identical body
  under a new name), or a type, value, access or generic signature change. A body change carries a reference
  delta such as `size 120 -> 134 | + call Foo.bar, const 0.5F | - const 0.4F`, often enough to tell a real change
  from a refactor without opening the source.
- **Moved and renamed classes are paired** by the share of identical method bodies, so a package move reads as
  one rename, not a removal and an addition.
- **Resources are compared.** JSON is flattened to `path = value` lines and diffed as a set, which makes tag and
  data changes readable. Other text gets a unified diff.
- **The server data generator runs for both versions** (`--reports`, 1.18 and later). `packets.json`,
  `blocks.json` and `registries.json` become tables: packet ids per phase and direction, block property and state
  changes, registry entries added, removed and renumbered. `-PnoReports` skips it.
- **Decompiled sources are diffed** for every top-level class whose bytecode changed, when both versions have
  been generated. The summary also counts files whose text differs while the bytecode does not, the decompiler
  noise the bytecode comparison filters out.

```
diffs/26.2..26.3/
├── summary.md                  versions, counts, per package and per resource group tables
├── packages/<package>.md       every changed class in the package, one line per member change
├── members.tsv                 the same, one tab separated row per change, for grep and awk
├── resources/<group>.md        added, removed and changed data files with flattened JSON deltas
├── reports/                    packets.json.md, blocks.json.md, registries.json.md, and other/
└── sources/<path>.java.diff    unified decompiled diffs
```

The jars for a diff stay under `work/jars/<version>/` and the reports under `work/reports/<version>/`, so a second
run only compares.

### Accepting the Minecraft EULA

Generating is gated behind the Minecraft EULA, the same way a Minecraft server is. Accept it either way:

- Pass `-Peula=true`, which writes or updates `eula.txt`.
- Edit `eula.txt` (created on the first run) and set `eula=true`.

`eula.txt` is git-ignored, so acceptance is per machine. `listVersions` does not need it.

### Flags

Switches such as `-Pforce` work bare or as `=true`.

| Flag             | Tasks              | Meaning |
|------------------|--------------------|---------|
| `-Peula=true`    | generate, ide, diff | Accept the Minecraft EULA |
| `-Pmc=<v>`       | generateVersion, ide | The version, or `<from>-<to>` for `generateVersion` |
| `-Pold=<v>` `-Pnew=<v>` | diffVersions | The two versions to compare |
| `-Pforce`        | generate           | Rebuild even when the version is up to date |
| `-Pfrom=<v>` `-Pto=<v>` | generateAll | Inclusive bounds of the selection |
| `-Plimit=N`      | generateAll        | Only the first N versions of the selection |
| `-Psnapshots`    | all                | Include snapshots, pre-releases and release candidates |
| `-Pjobs=N`       | generate           | Versions decompiled in parallel. Default: cores / 4, capped by memory and at 8 |
| `-PvfThreads=N`  | generate           | Vineflower threads per version. Default: cores / jobs |
| `-PvfHeap=4g`    | generate           | Heap of each Vineflower process. Default `4g` |
| `-PnoCache`      | generate           | Decompile without the cache, for canonical output (see below) |
| `-PresetCache`   | generate           | Delete the decompile cache first |
| `-PkeepWork`     | generate           | Keep the per-version scratch directory `work/tmp/<v>/` |
| `-PnoReports`    | diffVersions       | Skip the server data generator |

### The decompile cache

Decompiled classes are stored by content and reused across versions and runs, so a class that did not change
between releases is decompiled once. The key covers the class and its inner classes, the javadocs that would be
injected, the bytecode of every supertype, and the Vineflower version and options. It is on by default: a full
`generateAll` gets faster as it goes, and adding a new release decompiles little more than what changed.

The trade-off: the output is not byte for byte identical to an uncached decompile. A freshly decompiled class that
refers to a cached class's nested type writes an import and the simple name (`Mutable`) instead of the qualified
form (`BlockPos.Mutable`), because cached classes sit on the classpath rather than in the decompile set. The code
stays equivalent and navigable. Pass `-PnoCache` for a canonical decompile and `-PresetCache` to clear the cache.

### Memory and parallelism

`generateAll` runs `jobs` versions at once, each Vineflower process using `vfThreads` threads and up to
`vfHeap` of memory. Remapping runs inside the Gradle-launched JVM (up to 8 GB). The default job count fits both
the core count and the machine's physical memory (about `vfHeap` + 1.5 GB per job, with 4 GB left over). Lower
`-Pjobs` or `-PvfHeap` if the machine still runs short. A single version gets every core.

## Windows

Everything runs on Windows. Use `.\gradlew` (or `gradlew`) instead of `./gradlew`.

- **PowerShell splits version arguments at the first dot.** `-Pmc=1.19.4` reaches Gradle as `-Pmc=1` plus a
  task named `.19.4`, which fails with `Task '.19.4' not found`. Quote any `-P` argument that holds a version:

  ```powershell
  .\gradlew generateVersion "-Pmc=1.19.4" -Peula=true
  .\gradlew diffVersions "-Pold=26.2" "-Pnew=26.3" -Peula=true
  ```

  When only one version argument was split, the build puts it back together itself and says so. With more than
  one it stops and prints the quoted command. `cmd.exe`, Git Bash and WSL pass arguments through unchanged.
- **Symbolic links** need Developer Mode. Without it, the IDE workspace references the sources directory instead
  of linking it, with the same result.
- Line endings are pinned by `.gitattributes` (`gradlew` LF, `gradlew.bat` CRLF), so the wrappers work from a
  Windows checkout in every shell.

## How it works

For each version the generator:

1. Reads Mojang's version manifest and the version's metadata (cached under `work/cache/`).
2. Picks the mapping tier and downloads the mappings. On Mojmap it also resolves Parchment.
3. Downloads `client.jar` and `server.jar`, checks them against the SHA-1 in the metadata, and unwraps the 1.18+
   server bundler to reach the real server jar. Libraries are downloaded once into `work/libs/` and checked the
   same way.
4. Remaps client and server separately to named with
   [tiny-remapper](https://github.com/FabricMC/tiny-remapper) and [mapping-io](https://github.com/FabricMC/mapping-io).
   Mojang's ProGuard files are converted on the fly, Ornithe uses its `official` to `named` `mergedv2`. On 26.1+
   this step is a copy.
5. Merges client and server, keeping only Minecraft's classes.
6. Realigns parameter annotations on enum and inner-class constructors with ASM, on every version.
7. On Mojmap, writes parameter names into the merged jar with ASM: Parchment where it has them, otherwise type
   derived. Feather versions already carry theirs through the remap.
8. Decompiles with Vineflower in a separate JVM, with the version's libraries as context, javadocs injected by
   the decompiler (Parchment on Mojmap, Feather on legacy), and the decompiler's variable renaming only where
   the jar has no names of its own.
9. Moves the result into `sources/<version>/` and writes `.mcsg-done`.

The game jars and mappings are downloaded to the per-version scratch directory `work/tmp/<version>/`, deleted
when the version finishes, so they do not pile up (about 100 MB per modern version). Version metadata, libraries
and the decompile cache persist under `work/`. Deleting `work/` is always safe: it only costs re-downloading and
re-decompiling.

## Requirements

- A JDK 17 or newer to run Gradle. The build uses a Java 25 toolchain, which Gradle downloads automatically if
  none is installed.
- Network access to Mojang (piston-meta, piston-data, libraries.minecraft.net), Maven Central,
  maven.fabricmc.net, maven.ornithemc.net and maven.parchmentmc.org.
- Disk: about 2.2 GB for every version's sources, plus about 2 GB under `work/` for libraries and the decompile
  cache.
- Memory: 8 GB is enough for one version at a time (`-Pjobs=1`). The defaults scale up from there.

## Troubleshooting

- **`Task '.19.4' not found`**: PowerShell split the version argument. Quote it, see [Windows](#windows).
- **`Minecraft version 'x' is not in Mojang's version manifest`**: check the id with `./gradlew listVersions`.
  Snapshots need `-Psnapshots`.
- **A version failed**: the summary prints the cause. Decompiler output goes to `work/tmp/<version>/vineflower.log`,
  kept on failure. `-PkeepWork` keeps the scratch directory on success too.
- **Out of memory, or the machine swaps**: lower `-Pjobs`, then `-PvfHeap`.
- **`Could not delete …`**: a file under `sources/` or `work/` is open in another program, often an IDE indexing
  `sources/`. Close it, or open only `workspace/`, and run again.

## Project layout

```
build.gradle.kts                 tasks, dependencies from the version catalog, PowerShell argument repair
settings.gradle.kts              toolchain resolver, PowerShell argument repair
gradle/libs.versions.toml        dependency versions
src/main/java/com/deathmotion/mcsources/
├── cli/             Main                                    list, one, all, ide, diff
├── config/          GeneratorConfig, RunOptions, GeneratorProperties, Eula
├── manifest/        VersionManifest, MinecraftVersion       Mojang manifest and metadata
├── mapping/         MappingResolver, MappingTier, ResolvedMappings, MappingPlan, MappingArtifact
│   └── parchment/   ParchmentResolver, ParchmentData        Parchment names and javadocs
├── download/        Downloader, LibraryDownloader           HTTP, SHA-1 checks, libraries
├── remap/           JarRemapper                             tiny-remapper wrapper
├── jar/             ServerBundle, MinecraftClassFilter, JarAssembler,
│                    ParameterAnnotationFixer, ParameterNameInjector
├── decompile/       VineflowerDecompiler, VineflowerWorker, VineflowerOptions, DecompileSettings,
│   │                JavadocSource, ParchmentJavadocSource, TinyJavadocSource, JavadocSourceProvider
│   └── cache/       DecompileCache, CachedDecompile, JarIndex, ClassEntry, JavadocDigest
├── pipeline/        SourceGenerator, VersionJars, Phase, ProgressTracker,
│                    GenerationResult, GenerationOutcome, GenerationSummary
├── diff/            VersionDiff, JarComparison, ClassShape, InstructionText, ResourceComparison,
│                    ServerReports, ReportTables, SourceComparison, DiffReport, ...
├── ide/             IdeWorkspace                            the per-version IDE project
└── util/            PathUtils, Hashing
```

Contributor and agent guidance is in [AGENTS.md](AGENTS.md).
