package net.amyrose.storyamycore.custom;

import net.amyrose.storyamycore.core.BlockPos2D;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;

import java.util.Set;

import static net.amyrose.storyamycore.StoryAmyCoreMod.MOD_ID;

public class ModData {
    public static Story testStory = new Story("Story1", 200, MOD_ID);
//    public static Story testStory2 = new Story("Story2", 500, MOD_ID);
//    public static Story testStory3 = new Story("Story3", 500, MOD_ID);
//    public static Story testStory4 = new Story("Story4", 500, MOD_ID);
//    public static Story testStory5 = new Story("Story5", 500, MOD_ID);

    public static void LoadChapters() {
        testStory.AddFirstChapter(
                "testChapter", new BlockPos2D(281, 188), "prologue-landscape", "prologue-environment", "minecraft:forest",
                Set.of("minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean",
                        "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean"),
                64
        );
//        testStory2.AddFirstChapter(
//                "testChapter", new BlockPos2D(281, 188), "prologue-landscape", "prologue-environment", "minecraft:forest",
//                Set.of("minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean",
//                        "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean"),
//                64
//        );
//        testStory3.AddFirstChapter(
//                "testChapter", new BlockPos2D(281, 188), "prologue-landscape", "prologue-environment", "minecraft:forest",
//                Set.of("minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean",
//                        "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean"),
//                64
//        );
//        testStory4.AddFirstChapter(
//                "testChapter", new BlockPos2D(281, 188), "prologue-landscape", "prologue-environment", "minecraft:forest",
//                Set.of("minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean",
//                        "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean"),
//                64
//        );
//        testStory5.AddFirstChapter(
//                "testChapter", new BlockPos2D(281, 188), "prologue-landscape", "prologue-environment", "minecraft:forest",
//                Set.of("minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:lukewarm_ocean", "minecraft:warm_ocean",
//                        "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_lukewarm_ocean"),
//                64
//        );

        StoryEngine.putStory(testStory);
//        StoryEngine.putStory(testStory2);
//        StoryEngine.putStory(testStory3);
//        StoryEngine.putStory(testStory4);
//        StoryEngine.putStory(testStory5);
    }


}