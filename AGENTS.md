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
| Generate a range or a list | `./gradlew generateVersion -Pmc=1.16.5-1.18,1.20.1,26.3 -Peula=true` |
| Generate everything (parallel, resumable) | `./gradlew generateAll -Peula=true` |
| Print the version to mapping table | `./gradlew listVersions` |
| IDE workspace for one version | `./gradlew ide -Pmc=<version> -Peula=true`, then open `workspace/` on its own |
| Compare two versions | `./gradlew diffVersions -Pold=26.2 -Pnew=26.3 -Peula=true` |
| Prove output equals a full decompile | `./gradlew verifySources -Pmc=<versions> -Peula=true` |

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

Pure JVM, one process, no Fabric or Loom dependency, no intermediate jars. A run is five stages, sequenced by
`pipeline/Generation` and nothing else:

1. **Select.** Versions whose `.mcsg-done` carries the current `SourceTree.OUTPUT_REVISION` are skipped.
2. **Prepare** (`version/VersionPreparer`, several versions at once). Resolve the tier and mappings (and Parchment
   on Mojmap), download the client and server jars into `work/downloads/<version>/` (checked against Mojang's
   SHA-1, the 1.18+ server bundler unwrapped), remap each side to named with tiny-remapper straight into memory
   (a plain read on 26.1+), merge client first keeping only Minecraft classes, then run the decompile transforms
   (`version/transform`): parameter annotations realigned on every tier, parameter names written on Mojmap
   (Parchment, else type derived, never a Java keyword). Diffing stops before the transforms
   (`VersionPreparer.assemble`), so it compares the game's own bytecode.
3. **Plan** (`decompile/plan`). Every top-level class and its inner classes (a `ClassGroup`) gets a key that
   covers everything Vineflower's output for it depends on: its bytecode, the javadocs that would be injected,
   the structure (header, members, signatures, inner class table, no code) of every class it references and of
   their supertypes and outer classes transitively, the class names in its package, the Vineflower jar and
   options and the Java runtime. A group that uses a synthetic member of another group (an enum switch table, an
   `access$` accessor, a `class$` lookup) gets that group as a companion: its full bytecode joins the key and it is
   processed in the same Vineflower run, because Vineflower reads those members' processed code. This only happens
   across groups where inner classes were flattened to `Outer__Inner` (Ornithe Feather before 1.8.2). `DecompileWork`
   then hands every key that is not in the store to exactly one version, largest first, so a class shared by forty
   versions is decompiled once. When a run fails, its classes are handed to another version that has them.
4. **Decompile** (`decompile/vineflower/VineflowerRun`, `jobs` runs at once, `vfThreads` threads each). Vineflower
   runs in process on the whole version as its context, so imports, nested names and inference are exactly
   those of a full decompile, and `getClassEntryName` skips every class the run was not handed or does not need as
   a companion. A run that hits `OutOfMemoryError` anywhere (Vineflower turns errors into comments) keeps nothing.
   Each run is two Vineflower passes: first the standalone switch table classes (`SwitchTables`: a synthetic
   top-level class holding a `static final synthetic int[]`, which only the flattened Feather versions have), then
   everything else. Vineflower rebuilds an enum switch while processing the class that switches, from the table
   class's processed code, so in one pass the result depended on which thread got there first. Two passes make
   the output the same at any thread count, in a full decompile and in a selective one. The output goes
   to the store (`decompile/store`, on disk under `work/decompile-cache/`, in memory with `-PnoCache`).
5. **Write** (`pipeline/SourceTree`). Each version is assembled from the store into `sources/.<version>.partial`,
   swapped into place, and given its `.mcsg-done`.

Vineflower's `jad` variable renaming runs only where the jar has no local variable names of its own
(`MappingTier.shipsLocalNames()`), so the real names in 26.1+ jars survive.

**Exactness is proved, not argued.** `./gradlew verifySources -Pmc=<versions>` decompiles each version in one
full, uncached Vineflower pass and compares every file byte for byte with `sources/<version>`, writing the full
decompile of each differing file to `work/verify/<version>/`. Any difference means the plan misses an input, and
the plan is fixed, never the comparison loosened. The companion rule and the two passes came out of exactly
that: 1.7.2 to 1.8.1 differed on enum switches, and the full decompile itself gave different output at 1 and 32
threads until the switch tables were processed first.

Code under `src/main/java/com/deathmotion/mcsources/`:

- `cli/`: `Main` (`list | one | all | ide | diff | verify`), `VersionSelection` (ids, ranges, comma lists,
  `-Pfrom`/`-Pto`/`-Plimit`).
- `config/`: `GeneratorConfig` (paths and switches), `RunOptions` (parallelism sized to the heap and cores),
  `GeneratorProperties` (the `mcsg.*` system properties the Gradle tasks pass), `Eula`.
- `manifest/`: `VersionManifest`, `MinecraftVersion`.
- `mapping/` and `mapping/parchment/`: tier selection and loading, Parchment (exact release, else the nearest
  release in the same minor line).
- `download/`: `Downloader` (retries, SHA-1 checks, one writer per file), `LibraryDownloader`.
- `version/`: `VersionPreparer` (the prepare stage), `AssembledVersion`, `PreparedVersion`, `GameJars`,
  `ServerBundle`, `JarRemapper`, `MergedClasses`, `MinecraftClassFilter`, and `transform/` (`ClassTransforms`,
  `ParameterAnnotationFixer`, `ParameterNameInjector`).
- `decompile/plan/`: `DecompileKeys`, `ClassGroup`, `ClassStructure`, `References`, `LibraryIndex`,
  `JavadocDigest`, `VersionPlan`, `DecompileWork`.
- `decompile/vineflower/`: `VineflowerRun` (the selective in-process run), `MemoryClassSource`,
  `VineflowerOptions`, `JavadocSourceProvider`.
- `decompile/javadoc/`: `JavadocSource`, `ParchmentJavadocSource`, `TinyJavadocSource`.
- `decompile/store/`: `DecompileStore`, `DiskStore`, `MemoryStore`.
- `pipeline/`: `Generation` (the driver), `Planning`, `Decompilation`, `SourceTree` (the output tree: what is
  current, where a file goes, the marker), `PreparedVersions` (soft references, so a full run fits the heap and a
  dropped version is prepared again), `VersionTasks` (parallel per-version work with failures recorded per
  version), `StageClock`, `Verification`, `Console`, `GenerationResult`, `GenerationOutcome`, `GenerationSummary`.
- `diff/`: `VersionDiff` and the comparison, unchanged in behaviour.
- `ide/`: `IdeWorkspace`.
- `classfile/`: `InternalNames` (top-level class and package of an internal name), `JarClasses` (class entries of
  a jar).
- `io/`: `Directories`, `Hashing`.

## Conventions

- **No code comments and no Javadoc.** Names carry intent.
- Self-contained, single-responsibility classes per package. No catch-all utility classes.
- Dependency versions live in `gradle/libs.versions.toml` and are referenced through `libs.*`.
- Generation is resumable. A version is finished when `sources/<version>/.mcsg-done` exists **and** carries the
  current `revision`. `generateAll` skips finished versions unless `-Pforce` is passed.
- **Bump `SourceTree.OUTPUT_REVISION`** in any change that alters the generated sources (decompiler options,
  bytecode transforms, mappings handling). The next `generateAll` then rebuilds every version made by the older
  revision. The decompile cache keeps that cheap for classes the change did not touch.
- Anything that spawns a process or writes a path must work on Windows: no shell, no `/` assumptions in output
  that another tool parses, paths written into generated build scripts go through `IdeWorkspace.kotlinString`.
- **A refactor is proved by byte identical output.** Before the change, generate a fixed set covering every
  tier into a side directory (`-PsourcesDir=work/compare/before -Pmc=1.8.9,1.12.2,1.14.4,1.16.5,1.20,1.20.1,1.21.4,26.2,26.3
  -Pforce`) and hash it. After it, generate the same set into another directory and compare. A change meant to
  move no output that moves a byte has a bug in it. Then run `verifySources` on the set.
- A change that is meant to alter the output says so, bumps `OUTPUT_REVISION`, and is shown on real output, for
  example `grep -rhoE "\((Class<[^>]*>|Boolean) (class|boolean)[,)]" sources/1.15.2` finding nothing.

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
