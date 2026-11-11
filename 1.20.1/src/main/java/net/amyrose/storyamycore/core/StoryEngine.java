package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.mojang.text2speech.Narrator.LOGGER;

public class StoryEngine {
    public static boolean storiesInitialized = false;
    public static boolean registrationClosed = false;
    public static boolean chapterGenEnabled = true;
    public static boolean newWorldChapterGen = true;
    /* Set when chapter placement failed during world load; players are disconnected with this text on join. */
    public static String placementFailure = null;
    public static Map<String, Story> stories = new HashMap<>();

    public static ServerWorld serverWorld;
    public static int compassStoryIndex = 0;

    public static final float multiplyDistanceBetweenStories = 1.5f;

    public static int ringRadius = 64;

    static final int COORD_OFFSET = 30_000_000; // Minecraft world limit

    public static long XandZAsLong(int x, int z) { //Bit Packing
        return ((long) (x + COORD_OFFSET) & 0x3FFFFFFFL) << 30 |
                ((long) (z + COORD_OFFSET) & 0x3FFFFFFFL);
    }

    public static int LongToX(long key) {
        return (int) ((key >> 30) & 0x3FFFFFFL) - COORD_OFFSET;
    }

    public static int LongToZ(long key) {
        return (int) (key & 0x3FFFFFFL) - COORD_OFFSET;
    }

    public static int RingLayerandYAsInt(int layer, int borderY) { // Bit Packing
        return (layer << 16) | (borderY & 0xFFFF);
    }

    public static int RingLayerFromInt(int key) {
        return key >>> 16;
    }

    public static int BorderYFromInt(int key) {
        return (short) (key & 0xFFFF);
    }

    public static long XYZasLong(int x, int y, int z) {
        return ((long) (x + COORD_OFFSET) & 0x3FFFFFFL) << 38 |  // X: bits 63–38
                ((long) (y + 2048) & 0xFFFL) << 26 |              // Y: bits 37–26
                ((long) (z + COORD_OFFSET) & 0x3FFFFFFL);         // Z: bits 25–0
    }

    public static int LongToXFromXYZ(long key) {
        return (int) ((key >> 38) & 0x3FFFFFFL) - COORD_OFFSET;
    }

    public static int LongToYFromXYZ(long key) {
        return (int) ((key >> 26) & 0xFFFL) - 2048;
    }

    public static int LongToZFromXYZ(long key) {
        return (int) (key & 0x3FFFFFFL) - COORD_OFFSET;
    }

    public BlockPos LongToBlockPos(long key)
    {
        return new BlockPos((int) ((key >> 38) & 0x3FFFFFFL) - COORD_OFFSET, (int) ((key >> 26) & 0xFFFL) - 2048, (int) (key & 0x3FFFFFFL) - COORD_OFFSET);
    }

    public static String storyKey(String modId, String name) {
        return modId + ":" + name;
    }

    public static void addStory(String name, int baseMaxDistanceRadius, String modId) {
        stories.put(storyKey(modId, name), new Story(name, baseMaxDistanceRadius, modId));
    }

    /* stories is a HashMap, so keys are sorted to give the compass a stable cycle order. */
    public static Story getCompassStory() {
        if (stories.isEmpty()) {
            return null;
        }
        List<String> keys = new ArrayList<>(stories.keySet());
        Collections.sort(keys);
        return stories.get(keys.get(Math.floorMod(compassStoryIndex, keys.size())));
    }

    /* chapters is insertion-ordered, so the first unvisited placed chapter is the next one on the path. */
    public static Chapter getCompassTarget(Story story) {
        if (story == null) {
            return null;
        }
        for (Chapter chapter : story.chapters.values()) {
            if (chapter.isPlaced() && !chapter.wasVisited) {
                return chapter;
            }
        }
        return null;
    }

    /* Runs on the server thread (END_SERVER_TICK), once per second. Only the compass target chapter can be visited,
       so chapters are completed in order. */
    public static void markVisitedChapters(MinecraftServer server) {
        if (server.getTicks() % 20 != 0) {
            return;
        }
        Chapter target = getCompassTarget(getCompassStory());
        if (target == null) {
            return;
        }
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            if (player.getWorld().getRegistryKey() == World.OVERWORLD && target.chapterArea.inBounds(player.getBlockX(), player.getBlockZ())) {
                target.wasVisited = true;
                return;
            }
        }
    }

    public static NbtCompound SaveStoriesToNBT() {
        NbtCompound storiesNbt = new NbtCompound();
        for (Map.Entry<String, Story> story : stories.entrySet()) {
            NbtCompound storyNbt = story.getValue().SaveToNbt();
            storiesNbt.put(story.getKey(), storyNbt);
        }
        return storiesNbt;
    }

    public static void LoadStoriesFromNBT(NbtCompound compound) {
        for (Map.Entry<String, Story> story : stories.entrySet()) {
            String key = story.getKey();
            if (compound.contains(key, NbtElement.COMPOUND_TYPE)) {
                story.getValue().LoadFromNbt(compound.getCompound(key));
                //LOGGER.info("Loaded {} story starts at: {}", story.getValue().name, story.getValue().area.beginning.toString());
            }
//            else {
//                story.getValue().RandomizeStory(); // Create user added story after map was already created.(So I don't need to handle uninitialized stories)
//                LOGGER.info("Loaded {} story starts at: {}", story.getValue().name, story.getValue().area.beginning.toString());
//            }
        }
    }

    /* Rebuilds the chunk's present chapters from its position. The list lives in a field added by ChunkMixin and is not saved in chunk NBT,
       so a ProtoChunk that was saved and reloaded between statuses comes back with an empty list. Only the given chunk is written;
       stories and ringArea are not modified after registration/placement, so this is safe off the main thread. */
    public static void fillPresentChapters(Chunk chunk) {
        IChunkChapters Ichunk = (IChunkChapters) chunk;
        Ichunk.clearPresentChapters();

        final ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();

        for (Story story : stories.values()) {
            for (Chapter chapter : story.chapters.values()) {
                if (chapter.isPlaced() && chapter.ringArea.chunkInBound(chunkX, chunkZ)) {
                    Ichunk.addChapter(chapter);
                }
            }
        }
    }

    /* Reads stories directly instead of a chunk's present chapters, so it is usable from stages where that list is not filled.
       Read-only, so it is safe off the main thread. */
    public static boolean isInsideAnyRing(int minX, int minZ, int maxX, int maxZ) {
        for (Story story : stories.values()) {
            for (Chapter chapter : story.chapters.values()) {
                if (chapter.isPlaced() && chapter.ringArea.CheckCollision(minX, minZ, maxX, maxZ)) {
                    return true;
                }
            }
        }
        return false;
    }

    /* Reads stories directly instead of a chunk's present chapters, so it is usable from the features stage after a chunk reload.
       Read-only, so it is safe off the main thread. */
    public static boolean isInNoFeaturesMask(int x, int z, boolean grass) {
        for (Story story : stories.values()) {
            for (Chapter chapter : story.chapters.values()) {
                if (!chapter.isPlaced() || chapter.noFeaturesMask.isEmpty() || (grass && chapter.litterGrass)) {
                    continue;
                }
                /* A feature can reach a few blocks from its origin (a tree crown, a rock blob), so the columns around the origin are tested as well.
                   The corner test first keeps the many lookups for positions near a chapter only. */
                if (!chapter.chapterArea.inBounds(x - NO_FEATURES_MARGIN, z - NO_FEATURES_MARGIN) && !chapter.chapterArea.inBounds(x + NO_FEATURES_MARGIN, z - NO_FEATURES_MARGIN)
                        && !chapter.chapterArea.inBounds(x - NO_FEATURES_MARGIN, z + NO_FEATURES_MARGIN) && !chapter.chapterArea.inBounds(x + NO_FEATURES_MARGIN, z + NO_FEATURES_MARGIN)) {
                    continue;
                }
                for (int dx = -NO_FEATURES_MARGIN; dx <= NO_FEATURES_MARGIN; dx++) {
                    for (int dz = -NO_FEATURES_MARGIN; dz <= NO_FEATURES_MARGIN; dz++) {
                        if (chapter.noFeaturesMask.contains(XandZAsLong(x + dx, z + dz))) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    static final int NO_FEATURES_MARGIN = 4;

    /* Vanilla scatters a new player up to 10 blocks (spawnRadius) around the world spawn, so the spawn is kept at least this far from any chapter's middle. */
    static final int SPAWN_EDGE_MARGIN = 16;

    /* Returns where the world spawn should move to, or null when it is fine. A spawn deeper than SPAWN_EDGE_MARGIN inside a chapter is moved
       to just outside the front edge of that chapter (SPAWN_EDGE_MARGIN past it), so a spawn on a chapter's corner or edge is left alone.
       Repeated because the new spot can fall inside another chapter. Reads placement only, so it runs on the server thread at world setup. */
    public static BlockPos2D findSpawnOutsideChapters(int spawnX, int spawnZ) {
        int x = spawnX;
        int z = spawnZ;
        boolean moved = false;
        for (int attempt = 0; attempt < 8; attempt++) {
            boolean insideAny = false;
            for (Story story : stories.values()) {
                for (Chapter chapter : story.chapters.values()) {
                    if (!chapter.isPlaced()) {
                        continue;
                    }
                    Zone area = chapter.chapterArea;
                    int toWest = x - area.beginning.x;
                    int toEast = area.end.x - x;
                    int toNorth = z - area.beginning.z;
                    int toSouth = area.end.z - z;
                    int deepest = Math.min(Math.min(toWest, toEast), Math.min(toNorth, toSouth));
                    if (deepest <= SPAWN_EDGE_MARGIN) {
                        continue;
                    }
                    insideAny = true;
                    moved = true;
                    /* The player is moved to the chapter's front edge, not the nearest one, so the story is entered from its start and not from the side.
                       The unrotated front is the west side; the rotation turns it: RIGHT to the north, LEFT to the south, BACK to the east. */
                    switch (chapter.rotation) {
                        case NONE -> x = area.beginning.x - SPAWN_EDGE_MARGIN;
                        case BACK -> x = area.end.x + SPAWN_EDGE_MARGIN;
                        case RIGHT -> z = area.beginning.z - SPAWN_EDGE_MARGIN;
                        case LEFT -> z = area.end.z + SPAWN_EDGE_MARGIN;
                    }
                }
            }
            if (!insideAny) {
                break;
            }
        }
        return moved ? new BlockPos2D(x, z) : null;
    }

    public static BlockPos2D findStoriesSpawnRadius(Map<String, Story> initializedStories)
    {
        int storiesXCombinedSize = 0;
        int storiesZCombinedSize = 0;
        for (Story story : initializedStories.values()) {
            storiesXCombinedSize += story.area.end.x - story.area.beginning.x;
            storiesZCombinedSize += story.area.end.z - story.area.beginning.z;
        }

        return new BlockPos2D((int) (storiesXCombinedSize * multiplyDistanceBetweenStories), (int) (storiesZCombinedSize * multiplyDistanceBetweenStories));
    }

    /* Writes the landscape, rings and environment of every chapter touching the chunk. Shared by the noise generators (SurfaceBuilderMixin, after the
       surface rules) and the flat generator (FlatChunkGeneratorMixin). The chapter list is rebuilt here when empty because it is not saved in chunk NBT:
       a ProtoChunk reloaded after NOISE arrives without it. A chunk that really has no chapters just repeats a cheap bounds check once.
       Everything is local state, so concurrent worldgen threads do not share anything. */
    public static void stampChapters(Chunk chunk) {
        final ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();
        // Iterate over the 16x16 chunk
        IChunkChapters Ichunk = (IChunkChapters) chunk;
        /* The chapter list is not saved in chunk NBT: a ProtoChunk reloaded after NOISE arrives here empty, so it is rebuilt from the chunk position.
           A chunk that really has no chapters just repeats a cheap bounds check once. */
        if (Ichunk.getSize() == 0) {
            fillPresentChapters(chunk);
        }
        if (Ichunk.getSize() != 0)
        {
            /* One mutable position for the whole chunk: it is a local (not a field), so concurrent worldgen threads each have their own. */
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (Chapter chapter : Ichunk.getPresentChapters())
            {
                /* Below the surface block the landscape block is used, but grass becomes dirt so a grass block never sits under another block. */
                BlockState belowSurfaceState = chapter.landscapeBlockState;
                if (belowSurfaceState.equals(Blocks.GRASS_BLOCK.getDefaultState()))
                {
                    belowSurfaceState = Blocks.DIRT.getDefaultState();
                }

                for (int x = 0; x < 16; ++x)
                {
                    for (int z = 0; z < 16; ++z) {
                        int worldX = chunkX + x;
                        int worldZ = chunkZ + z;

                        BlockPos topmostBlockpos = new BlockPos(worldX, chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG, x, z), worldZ);
                        BlockState topmostBlockstate = chunk.getBlockState(topmostBlockpos); // For biome aware landscape.
                        int highestY;
                        int landscapeY = chapter.getLandscapeY(worldX, worldZ);
                        /* When several chapters overlap this chunk, the first one whose landscape contains the column owns it.
                           A landscape is stamped only by its owner, and a ring only where no landscape owns the column,
                           so overlapping chapters do not overwrite each other's landscape. */
                        if (Ichunk.getSize() > 1) {
                            Chapter owner = findLandscapeOwner(Ichunk.getPresentChapters(), worldX, worldZ);
                            if (landscapeY == Integer.MIN_VALUE ? owner != null : owner != chapter) {
                                continue;
                            }
                        }
                        /* Heightmaps are not written here: ProtoChunk.setBlockState already calls Heightmap.trackUpdate for the
                           heightmaps of the chunk's current status. During SURFACE that status is NOISE, whose set is OCEAN_FLOOR_WG
                           and WORLD_SURFACE_WG, and trackUpdate stores top block + 1 (what sampleHeightmap expects) and rescans
                           downward when the old top becomes air. MOTION_BLOCKING is rebuilt by ChunkStatus.FEATURES. A manual set(x, z, y)
                           would overwrite the correct value with one that is 1 too low.
                           Fluids differ on purpose: the ring keeps fluids above its surface so oceans still lap against the blended terrain,
                           while the landscape clears everything above its surface so water does not flood the set piece. */
                        //Generate Blending Rings
                        if (landscapeY == Integer.MIN_VALUE) { // That means -> not chapter territory yet
                            int ringLayerBlockHeight = CalculateBlendedHighestBlock(chapter, worldX, worldZ, topmostBlockpos.getY());

                            /* CalculateBlendedHighestBlock returns Integer.MIN_VALUE for columns outside the ring (e.g. the corners of ringArea).
                               Subtracting from it would overflow, so those columns are skipped explicitly. */
                            if (ringLayerBlockHeight != Integer.MIN_VALUE) {
                                highestY = Math.max(ringLayerBlockHeight, topmostBlockpos.getY());
                                int startY = Math.max(chunk.getBottomY(), Math.min(ringLayerBlockHeight - 2, topmostBlockpos.getY()));
                                for (int y = startY; y <= highestY; y++) {
                                    pos.set(worldX, y, worldZ);
                                    if (y == ringLayerBlockHeight) {
                                        BlockState state = topmostBlockstate;
                                        if (chapter.forbiddenLandscapeBlocks.contains(topmostBlockstate))
                                        {
                                            state = chapter.landscapeBlockState;
                                        }

                                        chunk.setBlockState(pos, state, false);
                                    }
                                    else if(y < ringLayerBlockHeight) {
                                        chunk.setBlockState(pos, belowSurfaceState, false);
                                    }
                                    else if (chunk.getBlockState(pos).getFluidState().isEmpty()) {
                                        chunk.setBlockState(pos, Blocks.AIR.getDefaultState(), false);
                                    }
                                }
                            }
                        } else {
                            //Generate Landscape

                            highestY = Math.min(chunk.getTopY() - 1, Math.max(Math.max(chapter.environmentHighestPoint, chapter.landscapeHighestPoint), topmostBlockpos.getY()));
                            int startY = Math.max(chunk.getBottomY(), Math.min(chapter.chaptersBaseHeight, topmostBlockpos.getY()));
                            /* The landscape template decides the blocks: its own surface block is used when it has one, and the
                               world's top block (biome aware) only as a fallback. Under stone, andesite and gravel the fill is stone,
                               under sand it is sand, otherwise the usual below-surface block. Water above the surface (rivers, lakes) is filled up to its level. */
                            BlockState templateSurface = chapter.getLandscapeSurface(worldX, worldZ);
                            int waterY = chapter.getWaterY(worldX, worldZ);
                            BlockState columnBelowState = belowSurfaceState;
                            if (templateSurface != null) {
                                if (templateSurface.isOf(Blocks.STONE) || templateSurface.isOf(Blocks.ANDESITE) || templateSurface.isOf(Blocks.GRAVEL)) {
                                    columnBelowState = Blocks.STONE.getDefaultState();
                                } else if (templateSurface.isOf(Blocks.SAND)) {
                                    columnBelowState = Blocks.SAND.getDefaultState();
                                }
                            }
                            for(int y = startY; y <= highestY; y++) {
                                pos.set(worldX, y, worldZ);
                                if (y == landscapeY) {
                                    BlockState state = templateSurface != null ? templateSurface : topmostBlockstate;
                                    if (templateSurface == null && chapter.forbiddenLandscapeBlocks.contains(topmostBlockstate))
                                    {
                                        state = chapter.landscapeBlockState;
                                    }

                                    chunk.setBlockState(pos, state, false);
                                }
                                else if (y < landscapeY) {
                                    chunk.setBlockState(pos, columnBelowState, false);
                                }
                                else if (y <= waterY) {
                                    chunk.setBlockState(pos, Blocks.WATER.getDefaultState(), false);
                                }
                                else {
                                    chunk.setBlockState(pos, Blocks.AIR.getDefaultState(), false);
                                }

                                // Generate Environment
                                BlockState state = chapter.getEnvironmentState(worldX, y, worldZ);
                                //LOGGER.info("name: {}, block: {}",story.name, state);

                                if (state != Blocks.AIR.getDefaultState()) {
                                    //System.out.println("maxHeight: " + chapter.maxHeight);
                                    chunk.setBlockState(pos, state, false);

                                    /* Beds and signs are drawn by block entity renderers, so without a BlockEntity they are invisible.
                                       setBlockState only stores the state, so rebuild the block entity from the template NBT
                                       (with its position moved to the world position). The NBT maps are read-only after populate, so this is safe off the main thread.
                                       The position is copied with toImmutable() because the BlockEntity keeps it and pos is reused for the next y. */
                                    NbtCompound savedNbt = chapter.getEnvironmentBlockEntityNbt(worldX, y, worldZ);
                                    if (state.hasBlockEntity() && savedNbt != null) {
                                        NbtCompound blockEntityNbt = savedNbt.copy();
                                        blockEntityNbt.putInt("x", worldX);
                                        blockEntityNbt.putInt("y", y);
                                        blockEntityNbt.putInt("z", worldZ);
                                        BlockEntity blockEntity = BlockEntity.createFromNbt(pos.toImmutable(), state, blockEntityNbt);
                                        if (blockEntity != null) {
                                            chunk.setBlockEntity(blockEntity);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            chunk.setNeedsSaving(true);
        }
    }

    private static Chapter findLandscapeOwner(List<Chapter> chapters, int worldX, int worldZ) {
        for (Chapter candidate : chapters) {
            if (candidate.getLandscapeY(worldX, worldZ) != Integer.MIN_VALUE) {
                return candidate;
            }
        }
        return null;
    }

    private static int CalculateBlendedHighestBlock(Chapter chapter, int worldX, int worldZ, int highestBlock){
        int ringLayer = chapter.WhatRingLayerIsIt(worldX, worldZ);
        if(ringLayer != Integer.MIN_VALUE) { // Is ring layer
            int BorderY = chapter.GetBorderYPosition(worldX, worldZ);

            // Normalize how deep into the ring this block is: 0.0 (closest to chapter) → 1.0 (outermost ring edge)
            float normalized = (float) ringLayer / chapter.ringRadius;

            // Interpolate between flatY and vanilla height using the normalized value
            // At center of ring (normalized = 0) → flatY, at edge (normalized = 1) → currentHeight
            float blended = BorderY * (1 - normalized) + highestBlock * normalized;
            return Math.round(blended);
        }
        return Integer.MIN_VALUE;
    }

}
