# Docs

Architecture and implementation details that are too specific for CLAUDE.md.

## Architecture (current state)
Worldgen plus a first client-side light renderer (see below). There is no networking or tests yet.

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
  `FeaturesMixin` skips placed-feature positions in the no-foliage mask (see below); `LakeFeatureMixin` blocks lava lakes inside rings.
- Landscape/environment are vanilla structure NBT in `src/main/resources/data/storyamycore/structures/`, loaded by `Identifier(modId, name)`.
- Placement is random, drawn from a `Random` seeded by the world seed and the story key (same seed, same positions), with up to 100 retries and a growing radius until the 4 corners and center are in allowed biomes; otherwise it throws. Ring layers are computed by BFS from the landscape border. Coordinate data uses fastutil maps.

## No-foliage mask

- Optional structure template named after the chapter's environment: `<name>-environment` becomes `<name>-no-foliage` (a name without the `-environment` suffix gets `-no-foliage` appended). Without the file the chapter has no mask.
- It lives in `src/main/resources/data/storyamycore/structures/` beside the environment and must have the same X/Z size. A mismatch logs an error and throws at load. Y may differ.
- It is placed at the same origin as the environment, so columns map 1:1.
- Every block in it masks its x/z column from foliage, whatever its type, except red wool. Red wool only pins the template's corners so its size matches the environment.
- `Chapter.populateEnvironmentMap` fills `Chapter.noFoliageMask`. `FeaturesMixin` filters `PlacedFeature` positions through `StoryEngine.isInNoFoliageMask`.
- The check uses the feature's origin only: a tree rooted outside a masked column can still overhang into it.

## Light renderer (client only)

- Package `render/`, started from `StoryAmyCoreClient` via `LightManager.init()`. Never referenced from common code.
- `Light` (cookie texture, intensity, fov, `viewToLight`), `ILightBackend`, `LightManager` (light list + backend choice: Sodium loaded → `SodiumLightBackend`, else `VanillaLightBackend`).
- `CookieLightPass` draws one fullscreen quad at `WorldRenderEvents.AFTER_TRANSLUCENT`: it reads the main framebuffer depth, rebuilds view-space position, projects it into the light and adds the cookie texture (additive blend). Shader: `assets/minecraft/shaders/core/storyamycore_cookie_light.*` (vanilla `ShaderProgram` only loads from the `minecraft` namespace), reloaded with resources (F3+T).
- `SodiumLightBackend` currently extends the vanilla one unchanged; it is the seam for Sodium-specific injection. Sodium is `modCompileOnly` (Modrinth maven).
- Test light in `StoryAmyCoreClient`: `flashlight_cookies/10.png` (4096x4096) at the camera, always on.
