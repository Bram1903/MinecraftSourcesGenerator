# AGENTS.md

Guidance for working in this repository, and for using the generated sources while developing Minecraft plugins
and mods. `CLAUDE.md` points here, so this is the one file to keep current.

## What this repository is

A generator that decompiles **Minecraft: Java Edition, every release since 1.7.2**, into
`sources/<version>/net/minecraft/…` (and `com/mojang/…`). There is no upper bound: new releases are picked up
from Mojang's manifest automatically. This repository is the *tooling*. The decompiled output only ever exists on
the local machine and is git-ignored.

| Task | Command |
|------|---------|
| Generate one version | `./gradlew generateVersion -Pmc=<version> -Peula=true` |
| Generate an inclusive range, in parallel | `./gradlew generateVersion -Pmc=<from>-<to> -Peula=true` (e.g. `-Pmc=1.16.5-1.18`) |
| Generate everything (parallel, resumable) | `./gradlew generateAll -Peula=true` |
| Print the version to mapping table | `./gradlew listVersions` |
| IDE workspace for one version | `./gradlew ide -Pmc=<version> -Peula=true`, then open `workspace/` on its own |
| Compare two versions | `./gradlew diffVersions -Pold=26.2 -Pnew=26.3 -Peula=true` |

`diffVersions` writes `diffs/<old>..<new>/`: `summary.md`, `packages/<package>.md` (member level changes),
`members.tsv`, `resources/`, `reports/` (the server data generator's packets, blocks and registries) and
`sources/` (decompiled diffs). `-PnoReports` skips the data generator. Start with `summary.md`, then grep
`members.tsv`.

On **Windows PowerShell**, quote any `-P` argument that holds a version (`"-Pmc=1.19.4"`). PowerShell splits an
unquoted `-Pmc=1.19.4` at the first dot. `settings.gradle.kts` and `build.gradle.kts` repair the split when only
one version property was given, and otherwise fail with the quoted command to run.

`sources/`, `diffs/`, `work/`, `workspace/` and `eula.txt` are git-ignored. **Never commit decompiled Minecraft
code or the mappings used to produce it**, and never paste large verbatim chunks of it anywhere public. It is for
local, personal study only (Minecraft EULA).

## Reading the sources for development

The decompiled tree is a faithful, navigable copy of one version's internals. Typical uses:

- Understand how a vanilla mechanic works before re-implementing or hooking it in a plugin or mod.
- Find the exact class, method or field to target for a mixin, access transformer, event or reflection.
- Scope an update between two versions: `./gradlew diffVersions`, or a plain
  `diff -ru sources/1.20.1/net/minecraft/world/level/block sources/1.21/net/minecraft/world/level/block`.
- Locate something fast: `grep -rl "someBehaviour" sources/1.21/net/minecraft`.

Always read the sources for **the version you are targeting**. Internals move between versions.

## Mapping eras: names change, and this matters

Class, method and field names depend on the mapping era of a version.

| Versions        | Names in `sources/` come from                   | Style |
|-----------------|-------------------------------------------------|-------|
| 26.1 and later  | **Unobfuscated vanilla** (Mojang's real names)  | Mojang (`net.minecraft.world.level.block.Block`) |
| 1.14.4 to 1.21.x | **Mojang official mappings** (Mojmap)          | Mojang (`net.minecraft.world.entity.player.Player`) |
| 1.7.2 to 1.14.3 | **Ornithe Feather** (Yarn style, community)     | Yarn (`net.minecraft.block.Block`, `net.minecraft.entity.player.PlayerEntity`) |

Matching a development platform:

- **Mojmap platforms** (Paper and Folia on modern versions, NeoForge, Fabric with `officialMojangMappings()`):
  names for 1.14.4 and later line up with what you write against.
- **Yarn based Fabric or Quilt mods**: modern Yarn differs from Mojmap (`PlayerEntity` vs `Player`,
  `Identifier` vs `ResourceLocation`). For 1.7.2 to 1.14.3 the Feather output here is Yarn style and lines up
  well. For 1.14.4 and later this repository is Mojmap, so translate names.
- **Spigot/Bukkit mappings and old MCP/Forge (1.8 to 1.12)** use different names from Feather. Use the sources
  here to understand *behaviour*, then map to your platform's names.
- The style is continuous across the 1.21.x to 26.x boundary (Mojmap to unobfuscated).

Parameter names, local variable names and javadocs come from the best source each era has. None of this changes
class, method or field names.

| Era | Parameters | Locals | Javadocs |
|-----|-----------|--------|----------|
| 26.1 and later | Mojang's own, from the jar | Mojang's own, from the jar | none (Mojang ships none) |
| Mojmap with Parchment | Parchment | decompiler assigned (`blockpos`, `i`) | Parchment |
| Mojmap without its own Parchment release (1.18, 1.19, 1.19.1, 1.20, 1.21.2) | Parchment of the nearest release in the same line, then type derived | decompiler assigned | the borrowed Parchment |
| Mojmap before 1.16.5 (1.14.4 to 1.16.4) | type derived, lowercase like the decompiler's locals (`blockpos`, `level`, `clazz`) | decompiler assigned | none |
| Ornithe Feather | Feather | decompiler assigned | Feather |

`sources/<version>/.mcsg-done` records what a version was built from: `tier` (`MOJMAP`, `ORNITHE_FEATHER` or
`VANILLA`), `mappings`, `parchment` (with `borrowed from` when it came from a sibling release), `decompiler`,
`revision` and file counts. `./gradlew listVersions` prints the tier of every version.

## Generator architecture

Pure JVM pipeline, no Fabric or Loom dependency. Per version:

1. Resolve the mapping tier, then the mappings (and Parchment on Mojmap).
2. Download the client and server jars, checked against Mojang's SHA-1, and unwrap the 1.18+ server bundler.
3. Remap each side to named with tiny-remapper and mapping-io (a plain copy on 26.1+).
4. Merge, client first, keeping only Minecraft classes.
5. Realign parameter annotations on enum and inner-class constructors with ASM (every tier).
6. On Mojmap, write parameter names into the jar with ASM: Parchment, else type derived, never a Java keyword.
7. Decompile with Vineflower in a separate worker JVM. Javadocs come in through the decompiler. Vineflower's
   `jad` variable renaming runs only where the jar has no local variable names of its own (`MappingTier
   .shipsLocalNames()`), so the real names in 26.1+ jars survive.
8. Move the result into `sources/<version>/` and write `.mcsg-done`.

Code under `src/main/java/com/deathmotion/mcsources/`:

- `cli/`: `Main`, the `list | one | all | ide | diff` entry point, version selection, progress output.
- `config/`: `GeneratorConfig` (paths and switches), `RunOptions` (parallelism, heap, selection, sized to RAM and
  cores), `GeneratorProperties` (reads the `mcsg.*` system properties the Gradle tasks pass), `Eula`.
- `manifest/`: `VersionManifest`, `MinecraftVersion` (Mojang's piston manifest and cached per-version metadata).
- `mapping/`: `MappingResolver` (tier selection and loading), `MappingTier`, `ResolvedMappings`, `MappingPlan`,
  `MappingArtifact`.
- `mapping/parchment/`: `ParchmentResolver` (exact release, else the nearest release in the same minor line),
  `ParchmentData` (parsed names and javadocs).
- `download/`: `Downloader` (HTTP with retries, SHA-1 checks, one writer per file), `LibraryDownloader`
  (decompiler classpath).
- `remap/`: `JarRemapper` (tiny-remapper wrapper).
- `jar/`: `ServerBundle`, `MinecraftClassFilter`, `JarAssembler`, `ParameterAnnotationFixer`,
  `ParameterNameInjector`.
- `decompile/`: `VineflowerDecompiler` (launches the worker, passes libraries in a file so Windows' command line
  limit never applies), `VineflowerWorker`, `VineflowerOptions` (the output-affecting options, shared by the worker
  and the cache key), `DecompileSettings`, `JavadocSource` with `ParchmentJavadocSource` and `TinyJavadocSource`,
  `JavadocSourceProvider`.
- `decompile/cache/`: the content-addressed decompile cache, **on by default** (`-PnoCache` turns it off).
  `ClassEntry`, `JarIndex`, `JavadocDigest`, `DecompileCache`, `CachedDecompile`. A key covers the class and its
  inner classes, the javadocs that would be injected, the bytecode of every supertype, the Vineflower jar and
  options. The output is not byte for byte identical to an uncached decompile: a freshly decompiled class that
  references a cached class's nested type imports it by simple name instead of qualifying it, because cached
  classes sit on the classpath rather than in the decompile set (Vineflower's `ImportCollector.getShortName`).
- `pipeline/`: `SourceGenerator` (per-version orchestration, `OUTPUT_REVISION`), `VersionJars` (download,
  unwrap, remap, merge, shared with diffing), `Phase`, `ProgressTracker`, `GenerationResult`,
  `GenerationOutcome`, `GenerationSummary`.
- `diff/`: `VersionDiff` (orchestration), `ClassIndex`, `ClassShape`, `InstructionText` (normalised instruction
  text with lambdas folded in), `JarComparison` and `MemberChange`, `ResourceIndex`, `ResourceComparison`,
  `TextDelta`, `ServerReports` (runs `net.minecraft.data.Main --reports`), `ReportTables`, `SourceComparison`,
  `DiffReport`.
- `ide/`: `IdeWorkspace` (the per-version Gradle project under `workspace/`).
- `util/`: `PathUtils`, `Hashing`.

## Conventions

- **No code comments and no Javadoc.** Names carry intent.
- Self-contained, single-responsibility classes per package. No catch-all utility classes.
- Dependency versions live in `gradle/libs.versions.toml` and are referenced through `libs.*`.
- Generation is resumable. A version is finished when `sources/<version>/.mcsg-done` exists **and** carries the
  current `revision`. `generateAll` skips finished versions unless `-Pforce` is passed.
- **Bump `SourceGenerator.OUTPUT_REVISION`** in any change that alters the generated sources (decompiler options,
  bytecode transforms, mappings handling). The next `generateAll` then rebuilds every version made by the older
  revision. The decompile cache keeps that cheap for classes the change did not touch.
- Anything that spawns a process or writes a path must work on Windows: no shell, no `/` assumptions in output
  that another tool parses, paths written into generated build scripts go through `IdeWorkspace.kotlinString`.
- Verify a change against real output: regenerate one version of each affected tier with `-Pforce` and read the
  result, for example `grep -rhoE "\((Class<[^>]*>|Boolean) (class|boolean)[,)]" sources/1.15.2` must find nothing.

## Commits

Conventional Commits 1.0.0 header, `type(scope)!: description`, lowercase, no full stop, signed (`git commit
-S`). Types: feat, fix, perf, refactor, test, docs, build, ci, chore, style, revert. The scope is one lowercase
word for the package or subsystem (`decompile`, `diff`, `cli`, `build`, …). No body, at most one footer
(`BREAKING CHANGE: ...`), never a co-author, session or other trailer. No semicolons or em dashes in the header.
A task lands as a few commits that group related work, never one per step.

## Adding and maintaining versions

New releases are picked up from the Mojang manifest automatically. `VersionManifest.BASELINE_VERSION` only sets
the 1.7.2 floor. Tier selection is data driven: metadata with `client_mappings` means Mojmap. Otherwise Ornithe
Feather `mergedv2` is looked up on maven.ornithemc.net, and if that repository cannot be reached the version
fails instead of being misread as unobfuscated. Otherwise the jar is treated as unobfuscated vanilla (26.1+).
