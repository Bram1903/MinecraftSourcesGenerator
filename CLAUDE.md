# CLAUDE.md

Guidance for working in this repository and for using the generated sources during Minecraft plugin/mod
development.

## What this repo is

A generator that decompiles **Minecraft: Java Edition for every release since 1.7.2** (no upper bound — new
releases are picked up automatically) into `sources/<version>/net/minecraft/…` (and `com/mojang/…`). It is the
*tooling*, not the code — the decompiled output lives only on the local machine and is git-ignored.

- Generate one version: `./gradlew generateVersion -Pmc=<version> -Peula=true`
- Generate an inclusive range: `./gradlew generateVersion -Pmc=<from>-<to> -Peula=true` (e.g. `-Pmc=1.16.5-1.18`,
  runs in parallel; endpoints must be plain release ids unless `-Psnapshots` is set)
- Generate everything (parallel, resumable, prints `[progress] N/total`): `./gradlew generateAll -Peula=true`
- See the version → mapping table: `./gradlew listVersions`
- Build an IDE workspace for one version: `./gradlew ide -Pmc=<version> -Peula=true` (writes `workspace/`, a
  Gradle project whose source root is that version's sources with its libraries on the classpath, for
  go-to-definition, find-usages and references). Open `workspace/` in the IDE; re-run to switch versions.

`sources/`, `work/` and `eula.txt` are git-ignored. **Never commit decompiled Minecraft code or the mappings**
used to produce it, and never paste large verbatim chunks of it into public places — it is for local, personal
study only (Minecraft EULA).

## Reading the sources for development

The decompiled tree is a faithful, navigable copy of a given version's internals. Typical uses:

- Understand how a vanilla mechanic works before re-implementing or hooking it in a plugin/mod.
- Find the exact class/method/field to target for a mixin, access transformer, event, or reflection.
- Diff behavior between two versions to scope an update, e.g.
  `diff -ru sources/1.20.1/net/minecraft/world/level/block sources/1.21/net/minecraft/world/level/block`.
- Locate something fast: `grep -rl "someBehaviour" sources/1.21/net/minecraft`.

Always read the sources for **the version you are targeting** — internals move between versions.

## Mapping eras — names change, and this matters

Class/method/field names depend on which mapping era a version falls in. Use the right names for your target
version, and be aware they differ from some platforms' mappings.

| Versions        | Names in `sources/` come from | Package/name style |
|-----------------|-------------------------------|--------------------|
| 26.1 and up     | **Unobfuscated vanilla** (Mojang's real names) | Mojang official style (`net.minecraft.world.level.block.Block`) |
| 1.14.4 → 1.21.x | **Mojang official mappings** (Mojmap)          | Mojang official style (`net.minecraft.world.level.block.Block`, `net.minecraft.world.entity.player.Player`) |
| 1.7.2 → 1.14.3  | **Ornithe Feather** (Yarn-style community)     | Yarn style (`net.minecraft.block.Block`, `net.minecraft.entity.player.PlayerEntity`) |

Key implications when matching a development platform:

- **Mojmap-based platforms** (Paper/Folia runtime mappings on modern versions, NeoForge, Fabric with
  `officialMojangMappings()`): names for **1.14.4+** here line up with what you write against. Good.
- **Yarn-based Fabric/Quilt mods**: modern Fabric Yarn names differ from Mojmap (e.g. `PlayerEntity` vs
  `Player`, `Identifier` vs `ResourceLocation`). For **1.7.2 → 1.14.3** this repo's Ornithe Feather output *is*
  Yarn-style and lines up well; for **1.14.4+** this repo is Mojmap, so translate names if your project uses
  Yarn.
- **Spigot/Bukkit (Spigot mappings) and old MCP/Forge (1.8–1.12)**: those use different obfuscation maps than
  Ornithe Feather, so legacy names here will not match them one-to-one — use the sources to understand
  *behavior*, then map to your platform's names.
- The naming style is continuous across the 1.21.x → 26.x boundary (Mojmap → unobfuscated), so modern
  knowledge carries forward.

Parameter names and javadocs come from the best source each era has:

- **Mojmap with a Parchment release** (1.16.5 through the latest 1.21.x): real parameter names and javadocs from
  Parchment, layered on top of Mojmap (which ships neither).
- **Ornithe Feather** (1.7.2 to 1.14.3): parameter names and the javadocs Feather provides come straight from
  the mappings.
- **Mojmap without Parchment** (e.g. 1.14.4, 1.15.x, a few patch releases): type-derived parameter names (e.g.
  `blockState`, `level`), no javadocs.

This never changes class/method/field names, only parameters and docs.

If unsure which era a version is in, run `./gradlew listVersions` or read `sources/<version>/.mcsg-done`
(`tier=MOJMAP | ORNITHE_FEATHER | VANILLA`).

## Generator architecture (for changing the tool itself)

Pure JVM pipeline, no Fabric/Loom dependency. Per version: resolve mappings (and Parchment on Mojmap) → download
client/server jars (unwrap the 1.18+ server bundler) → remap each side to named with tiny-remapper + mapping-io
→ merge and keep only Minecraft classes → fix parameter annotations on enum/inner-class constructors with ASM
(all tiers) → on Mojmap, inject parameter names with ASM (Parchment where it exists, otherwise type-derived) →
decompile with Vineflower in a separate worker JVM (javadocs injected via the decompiler: Parchment on Mojmap,
Feather on legacy) → write `sources/<version>/`.

Code under `src/main/java/com/deathmotion/mcsources/`:

- `cli/` — `Main`, the `list | one | all` entry point.
- `config/` — `GeneratorConfig`, `RunOptions` (parallelism/selection), `Eula` (the EULA gate).
- `manifest/` — `VersionManifest`, `MinecraftVersion` (Mojang piston manifest + metadata, cached).
- `mapping/` — `MappingResolver` (tier selection + loading), `MappingTier`, `ResolvedMappings`, `MappingPlan`,
  `MappingArtifact`.
- `mapping/parchment/` — `ParchmentResolver` (resolve + download per version), `ParchmentData` (parsed names + javadocs).
- `download/` — `Downloader` (HTTP + disk cache), `LibraryDownloader` (decompiler classpath context).
- `remap/` — `JarRemapper` (tiny-remapper wrapper).
- `jar/` — `ServerBundle` (bundler unwrap), `MinecraftClassFilter`, `JarAssembler` (merge), `ParameterAnnotationFixer`
  (ASM: realigns parameter annotations past the synthetic leading parameters of enum/inner-class constructors, all
  tiers), `ParameterNameInjector` (ASM: replaces placeholder parameter names with Parchment or type-derived ones, on Mojmap).
- `decompile/` — `VineflowerDecompiler` (launches the worker), `VineflowerWorker` (in-process Vineflower),
  `VineflowerOptions` (the output-affecting Vineflower options, shared by the worker and the cache key),
  `JavadocSource` + `ParchmentJavadocSource`/`TinyJavadocSource` (javadoc lookups for Mojmap and Feather) and
  `JavadocSourceProvider` (the Vineflower `IFabricJavadocProvider` adapter over a `JavadocSource`).
- `decompile/cache/` — content-addressed decompile cache (on by default, `-PnoCache` to opt out). `ClassEntry`
  (a top-level class plus its inner classes), `JarIndex` (walks the merged jar and groups classes), `JavadocDigest`
  (hashes the docs that would be injected so reuse never carries stale javadocs), `DecompileCache` (the store) and
  `CachedDecompile` (splits hits from misses, decompiles only misses, merges with cached sources). Not byte for
  byte identical to a full decompile: a miss that references a hit's nested type imports it by simple name instead
  of qualifying it, because reused classes sit on the classpath rather than the decompile set (a Vineflower
  constraint, see `ImportCollector.getShortName`). That is why it is opt-in.
- `pipeline/` — `SourceGenerator` (per-version orchestration), `GenerationResult`/`GenerationOutcome`/`GenerationSummary`.
- `ide/` — `IdeWorkspace` (writes the per-version Gradle project under `workspace/`).
- `util/` — `PathUtils`.

Conventions in this codebase:

- **No code comments / Javadoc** — names carry intent; keep it that way.
- Self-contained, single-responsibility classes per package; do not dump new code into a catch-all class.
- Dependency versions live in `gradle/libs.versions.toml`; reference them via the `libs.*` catalog accessors.
- Generation is resumable: a finished version has `sources/<version>/.mcsg-done`; `generateAll` skips it unless
  `-Pforce`.

## Adding/maintaining versions

New Minecraft releases are picked up automatically from the Mojang manifest — there is no upper bound, so a new
release just needs another `generateAll` run (`VersionManifest.BASELINE_VERSION` only sets the 1.7.2 floor). Tier
selection is data-driven: if the metadata has `client_mappings` it uses Mojmap; otherwise it tries Ornithe
Feather `mergedv2`; otherwise it treats the jar as unobfuscated vanilla (26.1+).
