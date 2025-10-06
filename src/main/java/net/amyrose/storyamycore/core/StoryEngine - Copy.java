//package net.amyrose.storyamycore.core;
//
//import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
//import net.minecraft.block.Block;
//import net.minecraft.block.Blocks;
//import net.minecraft.nbt.NbtCompound;
//import net.minecraft.nbt.NbtElement;
//import net.minecraft.nbt.NbtList;
//import net.minecraft.server.world.ServerWorld;
//import net.minecraft.util.math.BlockPos;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import static com.mojang.text2speech.Narrator.LOGGER;
//
//public class StoryEngine {
//    public static boolean storiesInitialized = false;
//    public static Map<String, Story> stories = new HashMap<>();
//
//    public static ServerWorld serverWorld;
//
//    public static final float multiplyDistanceBetweenStories = 1.5f;
//
//    public static int ringRadius = 64;
//
//    static final int COORD_OFFSET = 30_000_000; // Minecraft world limit
//
//    public static long XandZAsLong(int x, int z) { //Bit Packing
//        return ((long) (x + COORD_OFFSET) & 0x3FFFFFFFL) << 30 |
//                ((long) (z + COORD_OFFSET) & 0x3FFFFFFFL);
//    }
//
//    public static int LongToX(long key) {
//        return (int) ((key >> 30) & 0x3FFFFFFFL) - COORD_OFFSET;
//    }
//
//    public static int LongToZ(long key) {
//        return (int) (key & 0x3FFFFFFFL) - COORD_OFFSET;
//    }
//
//    public static int RingLayerandYAsInt(int layer, int borderY) { // Bit Packing
//        return (layer << 16) | (borderY & 0xFFFF);
//    }
//
//    public static int RingLayerFromInt(int key) {
//        return key >>> 16;
//    }
//
//    public static int BorderYFromInt(int key) {
//        return key & 0xFFFF;
//    }
//
//    public static long XYZasLong(int x, int y, int z) {
//        return ((long) (x + COORD_OFFSET) & 0x3FFFFFFL) << 38 |  // X: bits 63–38
//                ((long) (y + 2048) & 0xFFFL) << 26 |              // Y: bits 37–26
//                ((long) (z + COORD_OFFSET) & 0x3FFFFFFL);         // Z: bits 25–0
//    }
//
//    public static int LongToXFromXYZ(long key) {
//        return (int) ((key >> 38) & 0x3FFFFFFL) - COORD_OFFSET;
//    }
//
//    public static int LongToYFromXYZ(long key) {
//        return (int) ((key >> 26) & 0xFFFL) - 2048;
//    }
//
//    public static int LongToZFromXYZ(long key) {
//        return (int) (key & 0x3FFFFFFL) - COORD_OFFSET;
//    }
//
//    public BlockPos LongToBlockPos(long key)
//    {
//        return new BlockPos((int) ((key >> 38) & 0x3FFFFFFL) - COORD_OFFSET, (int) ((key >> 26) & 0xFFFL) - 2048, (int) (key & 0x3FFFFFFL) - COORD_OFFSET);
//    }
//
//    public static void putStory(Story story) {
//        stories.put(story.name, story);
//    }
//
//    public static NbtCompound SaveStoriesToNBT() {
//        NbtCompound storiesNbt = new NbtCompound();
//        for (Map.Entry<String, Story> story : stories.entrySet()) {
//            NbtCompound storyNbt = story.getValue().SaveToNbt();
//            storiesNbt.put(story.getKey(), storyNbt);
//        }
//        return storiesNbt;
//    }
//
//    public static void LoadStoriesFromNBT(NbtCompound compound) {
//        for (Map.Entry<String, Story> story : stories.entrySet()) {
//            String key = story.getKey();
//            if (compound.contains(key, NbtElement.COMPOUND_TYPE)) {
//                story.getValue().LoadFromNbt(compound.getCompound(key));
//                //LOGGER.info("Loaded {} story starts at: {}", story.getValue().name, story.getValue().area.beginning.toString());
//            }
////            else {
////                story.getValue().RandomizeStory(); // Create user added story after map was already created.(So I don't need to handle uninitialized stories)
////                LOGGER.info("Loaded {} story starts at: {}", story.getValue().name, story.getValue().area.beginning.toString());
////            }
//        }
//    }
//
//    public static BlockPos2D findStoriesSpawnRadius(Map<String, Story> initializedStories)
//    {
//        int storiesXCombinedSize = 0;
//        int storiesZCombinedSize = 0;
//        for (Story story : initializedStories.values()) {
//            storiesXCombinedSize += story.area.end.x - story.area.beginning.x;
//            storiesZCombinedSize += story.area.end.z - story.area.beginning.z;
//        }
//
//        return new BlockPos2D((int) (storiesXCombinedSize * multiplyDistanceBetweenStories), (int) (storiesZCombinedSize * multiplyDistanceBetweenStories));
//    }
//}
