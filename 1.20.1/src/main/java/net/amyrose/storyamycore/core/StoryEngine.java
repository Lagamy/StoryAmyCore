package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
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
}
