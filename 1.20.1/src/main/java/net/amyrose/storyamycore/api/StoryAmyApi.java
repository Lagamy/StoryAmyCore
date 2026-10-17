package net.amyrose.storyamycore.api;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.amyrose.storyamycore.core.BlockPos2D;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.amyrose.storyamycore.core.Zone;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.Set;

/* The only class other mods should use. Stories and chapters are addressed by string ids (modId, storyName, chapterName) so core classes can change freely. Register from your ModInitializer; registration closes when the server starts. */
public final class StoryAmyApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);

    private StoryAmyApi() {}

    public static void registerStory(String modId, String storyName, int baseMaxDistanceRadius) {
        if (StoryEngine.registrationClosed) {
            fail("Cannot register story " + storyName + " of mod " + modId + ": registration is closed once the server starts.");
        }
        if (StoryEngine.stories.containsKey(StoryEngine.storyKey(modId, storyName))) {
            fail("Story " + storyName + " of mod " + modId + " is already registered.");
        }
        StoryEngine.addStory(storyName, baseMaxDistanceRadius, modId);
    }

    public static void addFirstChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight) {
        Story story = getEditableStory(modId, storyName, chapterName);
        if (!story.chapters.isEmpty()) {
            fail("Story " + storyName + " already has a first chapter; use addChapter for " + chapterName + ".");
        }
        story.AddFirstChapter(chapterName, new BlockPos2D(sizeX, sizeZ), landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight);
    }

    public static void addChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, int offsetFromPreviousX, int offsetFromPreviousZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight) {
        Story story = getEditableStory(modId, storyName, chapterName);
        if (story.chapters.isEmpty()) {
            fail("Story " + storyName + " has no first chapter; call addFirstChapter before " + chapterName + ".");
        }
        story.AddChapter(chapterName, new BlockPos2D(sizeX, sizeZ), new BlockPos2D(offsetFromPreviousX, offsetFromPreviousZ), landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight);
    }

    /* True once the story's placement is known (world generated or loaded from story_data.dat). */
    public static boolean isStoryPlaced(String modId, String storyName) {
        Story story = StoryEngine.stories.get(StoryEngine.storyKey(modId, storyName));
        return story != null && story.area != null;
    }

    public static boolean isInsideChapter(String modId, String storyName, String chapterName, BlockPos pos) {
        Chapter chapter = findChapter(modId, storyName, chapterName);
        return chapter != null && chapter.chapterArea != null && chapter.chapterArea.inBounds(pos.getX(), pos.getZ());
    }

    /* Returns a copy, so callers cannot change the real placement. */
    public static Optional<Zone> getChapterArea(String modId, String storyName, String chapterName) {
        Chapter chapter = findChapter(modId, storyName, chapterName);
        if (chapter == null || chapter.chapterArea == null) {
            return Optional.empty();
        }
        return Optional.of(new Zone(chapter.chapterArea.beginning, chapter.chapterArea.end));
    }

    private static Story getEditableStory(String modId, String storyName, String chapterName) {
        if (StoryEngine.registrationClosed) {
            fail("Cannot add chapter " + chapterName + ": registration is closed once the server starts.");
        }
        Story story = StoryEngine.stories.get(StoryEngine.storyKey(modId, storyName));
        if (story == null) {
            fail("Story " + storyName + " of mod " + modId + " is not registered.");
        }
        if (story.chapters.containsKey(chapterName)) {
            fail("Chapter " + chapterName + " already exists in story " + storyName + ".");
        }
        return story;
    }

    private static Chapter findChapter(String modId, String storyName, String chapterName) {
        Story story = StoryEngine.stories.get(StoryEngine.storyKey(modId, storyName));
        return story == null ? null : story.chapters.get(chapterName);
    }

    private static void fail(String message) {
        LOGGER.error(message);
        throw new RuntimeException(message);
    }
}
