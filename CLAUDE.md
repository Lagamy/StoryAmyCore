# StoryAmyCore

Fabric core mod for Minecraft 1.20.1 (Yarn `1.20.1+build.10`, Fabric Loader 0.16.14, Fabric API 0.92.5, Java 17).
Mod id `storyamycore`, package `net.amyrose.storyamycore`. It is meant to be the main core for all my mods:
other story mods register their stories through it by `modId`. The repo is laid out per Minecraft version;
each version folder (currently `1.20.1/`) is its own Gradle project. This file lives in the repo root and applies to all of them.

## How we work
This is my codebase and I am the architect. I give you small-to-medium tasks and review every diff myself,
so implementation details within a task are yours to decide. If a task looks larger than that, tell me and
suggest a split. I must be able to maintain everything without you: prefer simple code that looks like mine.

## Rules
- Match the nearest existing pattern. Existing code is the source of truth, even where it is inconsistent: don't rename or reformat old code.
- Keep changes scoped to the task. Mention anything else you notice in short form at the end and leave it alone.
- If a change makes closely related code stale (its counterpart method, callers, comments beside it, this file), update it in the same task without asking, and list it in the summary.
- Proceed by default. Propose and wait for approval only for: public API changes, new abstractions or dependencies, ownership/state model changes (e.g. what `StoryEngine` holds, what gets persisted), or changes to behavior beyond what the task asks for.
- Ask only when the answer would materially change the result and the code doesn't answer it. Otherwise state your assumption and continue.
- Don't edit CLAUDE.md without showing me the change first.

## Minecraft / Fabric
- Before writing mixin, client/server-side, worldgen-threading, or NBT-persistence code, briefly explain the rule you rely on, then write it. If unsure of a rule, say so and name the source (Yarn class/method, Fabric API docs).
- Use Yarn 1.20.1 names only. `@Inject` uses a full descriptor only when the method is overloaded.
- Chunk generation does not run on the main server thread: don't touch non-thread-safe shared state from worldgen mixins.
- Client-only classes must never be loaded on a dedicated server (keep them behind the client entrypoint / client mixins).
- Private/protected vanilla members are opened through `noiseaccess.accesswidener`.

## Comments
- One `/* ... */` block before each logical block with non-obvious behavior: what it does and, when relevant, why (for Minecraft/Fabric, the rule behind the choice).
- No trailing or per-line comments. Nothing on trivial code. Don't leave commented-out dead code in new changes.

## Building
I run and test the game myself. Don't run `runClient`, `runServer`, `runDatagen` or any tests. After a change,
compile with `./gradlew build` from the version folder (e.g. `1.20.1/`) and show me only the first 20 error lines (never the full log).
Fix errors my own change caused; for errors in code I didn't touch, report them and leave them alone.
Gradle 8.12.1 + Loom 1.10 need JDK 17 or 21; the shell default is newer, so set `JAVA_HOME` if the build fails on that.
Use errors, logs and crash reports I paste for anything runtime. After a change, say what changed and that it
compiled (or the errors), and that nothing was run; don't claim it works.

## Architecture (current state)
Worldgen only. There is no client code, networking, shader code or tests yet.

- `StoryAmyCoreMod` (main entrypoint) defines stories in Java: `StoryEngine.addStory(...)`, then `story.AddFirstChapter(...)`. `StoryAmyCoreClient` and `StoryAmyCoreModDataGenerator` are empty.
- `core/`: `Story` (name, modId, chapters map, overall `Zone`), `Chapter` (size, landscape/environment structures, allowed biomes, base Y, placement data), `StoryEngine` (static global state + coordinate bit-packing like `XandZAsLong`), `Zone`, `BlockPos2D`, `IChunkChapters` (mixed into `Chunk`).
- `nbt/`: `ServerDataManager` reads/writes `<world>/story_data.dat`. Only each story's `storyArea` and each chapter's `ringArea`/`chapterArea` are persisted, as packed longs.
- `item/`: one creative tab (`ModItemGroups`); `ModItems` is empty.
- `mixin/WorldgenerationPipeline/`: the flow is
  `MinecraftServerMixin` (load data + structure templates at init, save on shutdown) →
  `ServerWorldMixin` (first Overworld load: randomize placement) →
  `ChunkChapterFilterMixin` (at `populateNoise`, tag chunks overlapping a chapter's ring) →
  `CarverMixin` (cancel caves in chapter chunks) →
  `SurfaceBuilderMixin` (stamp landscape, blend rings toward vanilla height, place environment, update heightmaps).
  `ChunkMixin` implements `IChunkChapters`.
- Landscape/environment are vanilla structure NBT in `src/main/resources/data/storyamycore/structures/`, loaded by `Identifier(modId, name)`. Pink wool in the environment template is skipped.
- Placement is random with up to 100 retries and a growing radius until the 4 corners and center are in allowed biomes; otherwise it throws. Ring layers are computed by BFS from the landscape border. Coordinate data uses fastutil maps.

## Conventions for new code
- 4 spaces, K&R braces (`if (x) {`). Classes `PascalCase`, methods/fields/locals `camelCase`. Interfaces get an `I` prefix, mixins end in `Mixin`.
- Model classes are plain mutable classes with public fields; serialization lives on them as `SaveToNbt`/`LoadFromNbt`.
- Invalid config fails loudly: `LOGGER.error` then `throw new RuntimeException(...)`.
- Use a real mod logger (`LoggerFactory.getLogger(MOD_ID)`) in new code, not the existing `Narrator.LOGGER` static import. No `System.out` or `printStackTrace`.
- Don't copy the Cyrillic lookalike characters found in some identifiers in `Chapter`.

## Known issues (report if relevant, fix only when asked)
- `core/StoryEngine - Copy.java` is a stray backup file.
- `FeaturesMixin` and `NoiseSamplerMixin` exist but are not registered; `BiomeSourceMixin` is fully commented out.
- Multi-story overlap placement in `Story.RandomizeStory` is commented out.
- `landscape_block` blockstate/model assets exist but no such block is registered.
- `fabric.mod.json` still has template placeholders (description, homepage, sources, authors, `suggests`).
- `worldDataNBT` has a lowercase class name; `MOD_ID` is a mutable static.
- `.gradle/` cache files were tracked in git earlier.

## Roadmap (planned, not built)
- Stories and chapters placement: exists, being extended.
- Client-side graphics shaders integrated into gameplay elements (e.g. a flashlight). The client side is a blank slate: empty client entrypoint, no client mixins or resources, no networking, and `StoryEngine` state is server-only. A shader library choice, a networking layer, or a client/common source-set split are new dependencies/abstractions: propose first.

## Git
Commit only when I ask. Match history: short, sentence-case, past-tense messages.
