package net.amyrose.storyamycore.custom;

import net.amyrose.storyamycore.core.BlockPos2D;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.ChapterEngine;

public class ModData {
    public static void LoadChapters() {
        ChapterEngine.chapters.add(new Chapter("test", new BlockPos2D(0, 0), new BlockPos2D(100, 100), "minecraft:desert", new BlockPos2D(50, 50))); // Safe place to put logic like this
        ChapterEngine.chapters.add(new Chapter("test", new BlockPos2D(0, 0), new BlockPos2D(100, 100), "minecraft:mushroom_fields", new BlockPos2D(50, 50))); // Safe place to put logic like this
    }
}