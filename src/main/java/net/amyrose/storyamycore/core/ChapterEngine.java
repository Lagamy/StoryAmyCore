package net.amyrose.storyamycore.core;

import java.util.ArrayList;
import java.util.List;

public class ChapterEngine {
    public static List<Chapter> chapters = new ArrayList<>();
    public static int ringRadius = 64;
    public enum RingBoundKey { MIN_X, MAX_X, MIN_Z, MAX_Z }
    public enum Direction { POS_X, NEG_X, POS_Z, NEG_Z }
}
