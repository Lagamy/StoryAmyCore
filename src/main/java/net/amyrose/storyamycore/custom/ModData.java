package net.amyrose.storyamycore.custom;

import net.amyrose.storyamycore.core.BlockPos2D;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.ChapterEngine;
import net.amyrose.storyamycore.core.Zone;

import static net.amyrose.storyamycore.StoryAmyCoreMod.MOD_ID;

public class ModData {
    public static void LoadChapters() {
        //ChapterEngine.chapters.add(new Chapter("test", new Zone(new BlockPos2D(0, 0), new BlockPos2D(100, 100)), "minecraft:desert", new BlockPos2D(0, 0))); // Safe place to put logic like this
        //ChapterEngine.chapters.add(new Chapter("test", new Zone(new BlockPos2D(0, 0), new BlockPos2D(100, 100)), "minecraft:mushroom_fields", new BlockPos2D(100 + ChapterEngine.ringRadius * 2, 0))); // Safe place to put logic like this
        ChapterEngine.chapters.add(new Chapter("test", MOD_ID, new BlockPos2D(0, 0), "test", "", "minecraft:birch_forest", new BlockPos2D(0, 0))); // Safe place to put logic like this
    }
}