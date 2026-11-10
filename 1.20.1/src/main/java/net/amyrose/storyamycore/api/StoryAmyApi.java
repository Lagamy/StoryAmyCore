package net.amyrose.storyamycore.api;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.amyrose.storyamycore.core.BlockPos2D;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.amyrose.storyamycore.core.StoryJson;
import net.amyrose.storyamycore.core.Zone;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;

/* The only class other mods should use. Stories and chapters are addressed by string ids (modId, storyName, chapterName) so core classes can change freely. Register from your ModInitializer; registration closes when the server starts.
   Stories live in <gameDir>/Stories/<modId>/<storyName>.json: the register/add calls only create the file or a missing chapter entry, existing values (user edits) are never overwritten. Chapters are built from the file when the server starts. */
public final class StoryAmyApi {
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);

    /* Per-session duplicate checks: the JSON legitimately already holds entries from earlier runs. */
    private static final Set<String> sessionChapters = new HashSet<>();
    /* Story key -> the chapter registered last this session, so a missing JSON entry is re-inserted right after it (its place in the script). */
    private static final Map<String, String> sessionLastChapter = new HashMap<>();

    private StoryAmyApi() {}

    public static void registerStory(String modId, String storyName, int baseMaxDistanceRadius) {
        if (StoryEngine.registrationClosed) {
            fail("Cannot register story " + storyName + " of mod " + modId + ": registration is closed once the server starts.");
        }
        if (StoryEngine.stories.containsKey(StoryEngine.storyKey(modId, storyName))) {
            fail("Story " + storyName + " of mod " + modId + " is already registered.");
        }
        int radius = StoryJson.ensureStoryFile(modId, storyName, baseMaxDistanceRadius);
        StoryEngine.addStory(storyName, radius, modId);
    }

    public static void addFirstChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseElevation) {
        checkEditable(modId, storyName, chapterName);
        if (sessionLastChapter.containsKey(StoryEngine.storyKey(modId, storyName))) {
            fail("Story " + storyName + " already has a first chapter; use addChapter for " + chapterName + ".");
        }
        StoryJson.ensureChapterEntry(modId, storyName, chapterName, null, StoryJson.chapterEntry(sizeX, sizeZ, null, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseElevation));
        sessionLastChapter.put(StoryEngine.storyKey(modId, storyName), chapterName);
    }

    public static void addChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, int offsetFromPreviousX, int offsetFromPreviousZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight) {
        checkEditable(modId, storyName, chapterName);
        if (!sessionLastChapter.containsKey(StoryEngine.storyKey(modId, storyName))) {
            fail("Story " + storyName + " has no first chapter; call addFirstChapter before " + chapterName + ".");
        }
        StoryJson.ensureChapterEntry(modId, storyName, chapterName, sessionLastChapter.get(StoryEngine.storyKey(modId, storyName)), StoryJson.chapterEntry(sizeX, sizeZ, new BlockPos2D(offsetFromPreviousX, offsetFromPreviousZ), landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight));
        sessionLastChapter.put(StoryEngine.storyKey(modId, storyName), chapterName);
    }

    /* Same as addFirstChapter plus minBiomeShare only (1.0 = every sampled point must be in an allowed biome); the tile size and biome override stay off. */
    public static void addFirstChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseElevation, double minBiomeShare) {
        addFirstChapter(modId, storyName, chapterName, sizeX, sizeZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseElevation, null, null, minBiomeShare);
    }

    /* Same as addChapter plus minBiomeShare only, see the addFirstChapter overload. */
    public static void addChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, int offsetFromPreviousX, int offsetFromPreviousZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight, double minBiomeShare) {
        addChapter(modId, storyName, chapterName, sizeX, sizeZ, offsetFromPreviousX, offsetFromPreviousZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight, null, null, minBiomeShare);
    }

    /* Same as addFirstChapter plus the optional chapter fields (see Docs.md): landscapeTileSize (null = one landscape template), biome (null = no override) and minBiomeShare (1.0 = every sampled point must be in an allowed biome). */
    public static void addFirstChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseElevation, BlockPos2D landscapeTileSize, String biome, double minBiomeShare) {
        checkEditable(modId, storyName, chapterName);
        if (sessionLastChapter.containsKey(StoryEngine.storyKey(modId, storyName))) {
            fail("Story " + storyName + " already has a first chapter; use addChapter for " + chapterName + ".");
        }
        StoryJson.ensureChapterEntry(modId, storyName, chapterName, null, StoryJson.chapterEntry(sizeX, sizeZ, null, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseElevation, landscapeTileSize, biome, minBiomeShare));
        sessionLastChapter.put(StoryEngine.storyKey(modId, storyName), chapterName);
    }

    /* Same as addChapter plus the optional chapter fields, see the addFirstChapter overload. */
    public static void addChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, int offsetFromPreviousX, int offsetFromPreviousZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight, BlockPos2D landscapeTileSize, String biome, double minBiomeShare) {
        checkEditable(modId, storyName, chapterName);
        if (!sessionLastChapter.containsKey(StoryEngine.storyKey(modId, storyName))) {
            fail("Story " + storyName + " has no first chapter; call addFirstChapter before " + chapterName + ".");
        }
        StoryJson.ensureChapterEntry(modId, storyName, chapterName, sessionLastChapter.get(StoryEngine.storyKey(modId, storyName)), StoryJson.chapterEntry(sizeX, sizeZ, new BlockPos2D(offsetFromPreviousX, offsetFromPreviousZ), landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight, landscapeTileSize, biome, minBiomeShare));
        sessionLastChapter.put(StoryEngine.storyKey(modId, storyName), chapterName);
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

    private static void checkEditable(String modId, String storyName, String chapterName) {
        if (StoryEngine.registrationClosed) {
            fail("Cannot add chapter " + chapterName + ": registration is closed once the server starts.");
        }
        if (!StoryEngine.stories.containsKey(StoryEngine.storyKey(modId, storyName))) {
            fail("Story " + storyName + " of mod " + modId + " is not registered.");
        }
        if (!sessionChapters.add(StoryEngine.storyKey(modId, storyName) + "/" + chapterName)) {
            fail("Chapter " + chapterName + " already exists in story " + storyName + ".");
        }
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
