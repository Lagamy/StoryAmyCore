package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StoryEngine {
    public static List<Story> stories = new ArrayList<>();

    public static final int maxDistanceBetweenStories = 500;

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
        return key & 0xFFFF;
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


}
