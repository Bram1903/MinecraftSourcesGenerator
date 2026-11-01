# Minecraft Sources Generator

Generates readable, decompiled Minecraft: Java Edition source code for every release since 1.7.2, one directory
per version under `sources/<version>/`. There is no upper bound: when Mojang ships a release, ask for it by id and
it is picked up. Pure JVM, no Fabric, Loom or Forge toolchain needed.

```bash
./gradlew generateVersion -Pmc=1.21.8 -Peula=true          # the version you work against
./gradlew generateVersion -Pmc=1.20.1,1.21.8 -Peula=true   # or the few you support
./gradlew ide -Pmc=1.21.8 -Peula=true                      # open it in IntelliJ with full navigation
./gradlew diffVersions -Pold=26.2 -Pnew=26.3 -Peula=true
```

**Generate only the versions you need.** Decompiling is heavy work: a single modern version is about five
minutes of CPU time, and every release since 1.7.2 is about 2.2 GB of sources. On a 32 core machine one version
takes 20 to 40 seconds and all of them about five minutes. On a laptop with 8 cores, roughly a minute or two per
version and the better part of half an hour for `generateAll`. Ask for the versions your project supports with
`-Pmc` (a list, a range or both), and add more later: work already done is reused, so nothing is decompiled twice.
`generateAll` is there for when you really want everything.

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
revision=3
tier=MOJMAP
mappings=mojang official
parchment=2023.09.03 (borrowed from 1.20.1)
decompiler=vineflower-1.12.0.jar@1dfcfe974395734fa467ce620661c7623d05ba83670de0529b1fbd63ff548b9d
classes=7436
javaFiles=4786
generatedAt=2026-11-01T17:33:29.394683106Z
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

# One version, an inclusive range, or a comma separated mix of both
./gradlew generateVersion -Pmc=1.16.5 -Peula=true
./gradlew generateVersion -Pmc=1.16.5-1.18 -Peula=true
./gradlew generateVersion -Pmc=1.12.2,1.20-1.20.1,26.3 -Peula=true

# Every release, skipping versions that are already up to date (slow, see above)
./gradlew generateAll -Peula=true
./gradlew generateAll -Pfrom=1.20 -Pto=1.21.8 -Peula=true

# An IDE workspace for one version (generates it first if needed)
./gradlew ide -Pmc=1.21.8 -Peula=true

# Compare two versions class by class, member by member, with resources and server reports
./gradlew diffVersions -Pold=26.2 -Pnew=26.3 -Peula=true

# Prove the generated sources equal a full, uncached decompile, byte for byte
./gradlew verifySources -Pmc=1.21.4,26.3 -Peula=true
```

A range (`-Pmc=1.16.5-1.18`) covers every release between the two endpoints, inclusive: here 1.16.5, 1.17,
1.17.1 and 1.18. With `-Psnapshots`, snapshot and pre-release ids work as endpoints too, dashes included
(`-Pmc=1.21-pre1-1.21`).

A run prints one line per version and stage, then a summary with the time each stage took:

```
[prepare] 42/88 1.19.4 in 12.3s
[plan] 160123 unique classes, 0 already decompiled, 160123 to decompile in 88 run(s)
[decompile] 7/88 1.21.4, 2140 classes in 9.8s
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
| `-Peula=true`    | generate, ide, diff, verify | Accept the Minecraft EULA |
| `-Pmc=<v>`       | generateVersion, ide, verifySources | A version, a `<from>-<to>` range, or a comma separated list of both |
| `-Pold=<v>` `-Pnew=<v>` | diffVersions | The two versions to compare |
| `-Pforce`        | generate           | Rebuild even when the version is up to date |
| `-Pfrom=<v>` `-Pto=<v>` | generateAll | Inclusive bounds of the selection |
| `-Plimit=N`      | generateAll        | Only the first N versions of the selection |
| `-Psnapshots`    | all                | Include snapshots, pre-releases and release candidates |
| `-Pjobs=N`       | generate           | Vineflower runs at once. Default: cores / 8, bounded by the heap |
| `-PvfThreads=N`  | generate, verify   | Threads per Vineflower run. Default: cores / jobs |
| `-PmaxHeap=24g`  | all                | Heap of the generator. Default: half the machine's memory |
| `-PsourcesDir=<dir>` | all            | Write somewhere other than `sources/` |
| `-PnoCache`      | generate           | Keep decompiled classes in memory for this run only |
| `-PresetCache`   | generate           | Delete the decompile cache first |
| `-PnoReports`    | diffVersions       | Skip the server data generator |

### Speed, and why the output is exact

Decompiling is almost all of the cost: a modern version is about 290 CPU seconds of Vineflower. So the generator
never decompiles the same class twice, and does it without changing a byte of the output.

Every top-level class gets a key covering everything its decompiled text depends on: its bytecode and inner
classes, the javadocs that would be injected, the structure of every class it references and of their supertypes
and outer classes, the class names in its package, and the Vineflower build and options. Across all releases
since 1.7.2, about 56% of the 302,000 top-level classes are unique by that key. Each unique key is decompiled
once, by one version, in a Vineflower run that holds that whole version as its context, so imports, nested names
and type inference come out exactly as in a full decompile. The results go to `work/decompile-cache/` and every
version is written from there. A later run, a new release or an output revision bump decompiles only what changed.

On a 32 core machine a cold `generateAll` of 84 releases takes about five minutes, where decompiling every
version separately took nine to ten.

`./gradlew verifySources -Pmc=<versions>` is the proof: it decompiles each version again in one full, uncached
pass and compares every file byte for byte with `sources/<version>`, and writes the full decompile of any file
that differs to `work/verify/<version>/`. All 84 releases verify identical.

Vineflower on its own is not deterministic for 1.7.2 to 1.8.1: their mappings make enum switch tables separate
classes, and whether a switch comes out rebuilt depended on thread timing. The generator decompiles those table
classes in a first pass, so these versions now come out the same on every run and every machine.

### Memory and parallelism

Everything runs in the one JVM Gradle starts. It prepares several versions at once (download, remap, merge,
transform, all in memory), then runs `jobs` Vineflower passes at once with `vfThreads` threads each. Prepared
versions are held through soft references, so on a smaller heap the JVM drops them and they are prepared again
when needed rather than running out of memory. If a run still fails, its classes are handed to another version
that contains them. Lower `-Pjobs` or raise `-PmaxHeap` if a machine runs short.

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

A run has five stages:

1. **Select** the versions whose `.mcsg-done` is missing or from an older output revision.
2. **Prepare** each version in memory: Mojang's metadata (cached under `work/cache/`), the mapping tier and
   mappings, Parchment on Mojmap, `client.jar` and `server.jar` downloaded once into `work/downloads/<version>/`
   and checked against Mojang's SHA-1 (the 1.18+ server bundler unwrapped), libraries into `work/libs/`.
   Client and server are remapped to named with [tiny-remapper](https://github.com/FabricMC/tiny-remapper) and
   [mapping-io](https://github.com/FabricMC/mapping-io) straight into memory, merged client first keeping only
   Minecraft's classes, then parameter annotations are realigned and, on Mojmap, parameter names written.
3. **Plan**: key every class and hand each key that is not cached to one version.
4. **Decompile** with Vineflower in process: one run per version that has work, each skipping the classes it
   was not handed, javadocs injected by the decompiler and its variable renaming only where the jar has no
   names of its own.
5. **Write** each version from the cache into `sources/<version>/`, then its `.mcsg-done`.

`work/` holds downloads (about 100 MB per modern version), libraries, the decompile cache and logs. Deleting it
is always safe: it only costs downloading and decompiling again.

## Requirements

- A JDK 17 or newer to run Gradle. The build uses a Java 25 toolchain, which Gradle downloads automatically if
  none is installed.
- Network access to Mojang (piston-meta, piston-data, libraries.minecraft.net), Maven Central,
  maven.fabricmc.net, maven.ornithemc.net and maven.parchmentmc.org.
- Disk: about 2.2 GB for every version's sources, plus about 7 GB under `work/` for every version's downloads,
  the libraries and the decompile cache.
- Memory: `-PmaxHeap=8g -Pjobs=1` is enough for one version at a time. The defaults scale up from there.

## Troubleshooting

- **`Task '.19.4' not found`**: PowerShell split the version argument. Quote it, see [Windows](#windows).
- **`Minecraft version 'x' is not in Mojang's version manifest`**: check the id with `./gradlew listVersions`.
  Snapshots need `-Psnapshots`.
- **A version failed**: the summary prints the cause. Vineflower's warnings for a run are in
  `work/logs/<version>.log`.
- **Out of memory, or the machine swaps**: lower `-Pjobs`, or set `-PmaxHeap` to what the machine can spare.
- **`Could not delete …`**: a file under `sources/` or `work/` is open in another program, often an IDE indexing
  `sources/`. Close it, or open only `workspace/`, and run again.

## Project layout

```
build.gradle.kts                 tasks, heap sizing, dependencies from the version catalog, PowerShell repair
settings.gradle.kts              toolchain resolver, PowerShell argument repair
gradle/libs.versions.toml        dependency versions
src/main/java/com/deathmotion/mcsources/
├── cli/             Main, VersionSelection                  list, one, all, ide, diff, verify
├── config/          GeneratorConfig, RunOptions, GeneratorProperties, Eula
├── manifest/        VersionManifest, MinecraftVersion       Mojang manifest and metadata
├── mapping/         MappingResolver, MappingTier, ResolvedMappings, MappingPlan, MappingArtifact
│   └── parchment/   ParchmentResolver, ParchmentData        Parchment names and javadocs
├── download/        Downloader, LibraryDownloader           HTTP, SHA-1 checks, libraries
├── version/         VersionPreparer, AssembledVersion, PreparedVersion, GameJars, ServerBundle,
│   │                JarRemapper, MergedClasses, MinecraftClassFilter      a version in memory
│   └── transform/   ClassTransforms, ParameterAnnotationFixer, ParameterNameInjector
├── decompile/
│   ├── plan/        DecompileKeys, ClassGroup, ClassStructure, LibraryIndex, JavadocDigest,
│   │                VersionPlan, DecompileWork               keys and who decompiles what
│   ├── vineflower/  VineflowerRun, MemoryClassSource, VineflowerOptions, JavadocSourceProvider
│   ├── javadoc/     JavadocSource, ParchmentJavadocSource, TinyJavadocSource
│   └── store/       DecompileStore, DiskStore, MemoryStore
├── pipeline/        Generation, Verification, SourceTree, Console, GenerationResult, GenerationOutcome,
│   │                GenerationSummary
│   └── generation/  Planning, Decompilation, PreparedVersions, VersionTasks, StageClock
├── diff/            VersionDiff, DiffReport, SourceComparison, TextDelta      the version comparison
│   ├── bytecode/    ClassIndex, ClassShape, InstructionText, JarComparison, MemberChange, ClassNames
│   └── data/        ResourceIndex, ResourceComparison, ServerReports, ReportTables, ReportComparison
├── ide/             IdeWorkspace                            the per-version IDE project
├── classfile/       InternalNames, JarClasses
└── io/              Directories, Hashing
```

Contributor and agent guidance is in [AGENTS.md](AGENTS.md).
