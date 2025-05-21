package net.amyrose.storyamycore.custom;

import net.amyrose.storyamycore.core.BlockPos2D;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;

import static net.amyrose.storyamycore.StoryAmyCoreMod.MOD_ID;

public class ModData {
    public static Story testStory = new Story("test", MOD_ID);
    public static Story testStory2 = new Story("test2", MOD_ID);
    public static Story testStory3 = new Story("test3", MOD_ID);
    public static Story testStory4 = new Story("test4", MOD_ID);
    public static Story testStory5 = new Story("test5", MOD_ID);

    public static void LoadChapters() {
        testStory.AddFirstChapter("testChapter", new BlockPos2D(281, 188),"prologue-landscape", "prologue-environment", "minecraft:desert");
        testStory2.AddFirstChapter("testChapter", new BlockPos2D(281, 188),"prologue-landscape", "prologue-environment", "minecraft:desert");
        testStory3.AddFirstChapter("testChapter", new BlockPos2D(281, 188),"prologue-landscape", "prologue-environment", "minecraft:desert");
        testStory4.AddFirstChapter("testChapter", new BlockPos2D(281, 188),"prologue-landscape", "prologue-environment", "minecraft:desert");
        testStory5.AddFirstChapter("testChapter", new BlockPos2D(281, 188),"prologue-landscape", "prologue-environment", "minecraft:desert");

        StoryEngine.putStory(testStory);
        StoryEngine.putStory(testStory2);
        StoryEngine.putStory(testStory3);
        StoryEngine.putStory(testStory4);
        StoryEngine.putStory(testStory5);

    }
}