package net.amyrose.storyamycore.core;

import java.util.ArrayList;
import java.util.List;

public class ChapterEngine {
    public static List<Chapter> chapters = new ArrayList<>();
    public static int ringRadius = 64;
    public enum RingBoundKey { MIN_X, MAX_X, MIN_Z, MAX_Z }
    public enum Direction { POS_X, NEG_X, POS_Z, NEG_Z }


    static final int COORD_OFFSET = 30_000_000; // Minecraft world limit

    public static long XandZAsLong(int x, int z) {
        return ((long)(x + COORD_OFFSET) & 0x3FFFFFFFL) << 30 |
                ((long)(z + COORD_OFFSET) & 0x3FFFFFFFL);
    }

    public static int LongToX(long key){
        return (int) ((key >> 30) & 0x3FFFFFFL) - COORD_OFFSET;
    }

    public static int LongToZ(long key){
        return (int) (key & 0x3FFFFFFL) - COORD_OFFSET;
    }
}
