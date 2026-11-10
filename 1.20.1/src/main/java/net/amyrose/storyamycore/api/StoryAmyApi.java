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
import net.amyrose.storyamycore.core.ChapterCreateInfo;
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

    /* Adds the first chapter of a story from a ChapterCreateInfo (see there for the required and optional fields); offsetFromPrevious must be null. */
    public static void addFirstChapter(String modId, String storyName, String chapterName, ChapterCreateInfo info) {
        checkEditable(modId, storyName, chapterName);
        checkInfo(chapterName, info);
        if (info.offsetFromPrevious != null) {
            fail("Chapter " + chapterName + " is the first chapter of " + storyName + " and cannot have an offsetFromPrevious.");
        }
        if (sessionLastChapter.containsKey(StoryEngine.storyKey(modId, storyName))) {
            fail("Story " + storyName + " already has a first chapter; use addChapter for " + chapterName + ".");
        }
        StoryJson.ensureChapterEntry(modId, storyName, chapterName, null, StoryJson.chapterEntry(info));
        sessionLastChapter.put(StoryEngine.storyKey(modId, storyName), chapterName);
    }

    /* Adds a chapter after the one added before it from a ChapterCreateInfo; offsetFromPrevious is required. */
    public static void addChapter(String modId, String storyName, String chapterName, ChapterCreateInfo info) {
        checkEditable(modId, storyName, chapterName);
        checkInfo(chapterName, info);
        if (info.offsetFromPrevious == null) {
            fail("Chapter " + chapterName + " needs an offsetFromPrevious (only the first chapter of a story has none).");
        }
        if (!sessionLastChapter.containsKey(StoryEngine.storyKey(modId, storyName))) {
            fail("Story " + storyName + " has no first chapter; call addFirstChapter before " + chapterName + ".");
        }
        StoryJson.ensureChapterEntry(modId, storyName, chapterName, sessionLastChapter.get(StoryEngine.storyKey(modId, storyName)), StoryJson.chapterEntry(info));
        sessionLastChapter.put(StoryEngine.storyKey(modId, storyName), chapterName);
    }

    /* The long overloads below are the same calls with every value as an argument; they build a ChapterCreateInfo and are kept for existing callers. New code should use the ChapterCreateInfo overloads. */
    public static void addFirstChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseElevation) {
        addFirstChapter(modId, storyName, chapterName, sizeX, sizeZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseElevation, null, null, 1.0);
    }

    public static void addChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, int offsetFromPreviousX, int offsetFromPreviousZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight) {
        addChapter(modId, storyName, chapterName, sizeX, sizeZ, offsetFromPreviousX, offsetFromPreviousZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight, null, null, 1.0);
    }

    /* Same as addFirstChapter plus minBiomeShare only (1.0 = every sampled point must be in an allowed biome); the tile size and biome override stay off. */
    public static void addFirstChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseElevation, double minBiomeShare) {
        addFirstChapter(modId, storyName, chapterName, sizeX, sizeZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseElevation, null, null, minBiomeShare);
    }

    /* Same as addChapter plus minBiomeShare only, see the addFirstChapter overload. */
    public static void addChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, int offsetFromPreviousX, int offsetFromPreviousZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight, double minBiomeShare) {
        addChapter(modId, storyName, chapterName, sizeX, sizeZ, offsetFromPreviousX, offsetFromPreviousZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight, null, null, minBiomeShare);
    }

    /* Same as addFirstChapter plus the optional chapter fields (see Docs.md): landscapeTileSize (null = one landscape template), biome (null = no override) and minBiomeShare (1.0 = every sampled point must be in an allowed biome). The blend ring keeps its default size. */
    public static void addFirstChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseElevation, BlockPos2D landscapeTileSize, String biome, double minBiomeShare) {
        addFirstChapter(modId, storyName, chapterName, sizeX, sizeZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseElevation, landscapeTileSize, biome, minBiomeShare, StoryEngine.ringRadius);
    }

    /* Same as addFirstChapter plus all optional fields, including ringRadius: how many blocks the blend ring between the landscape and the vanilla terrain spans (default StoryEngine.ringRadius, 64). Each ring step changes the height by about one block, so a chapter whose edge differs a lot from the terrain needs a bigger value for a gradual slope. */
    public static void addFirstChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseElevation, BlockPos2D landscapeTileSize, String biome, double minBiomeShare, int ringRadius) {
        addFirstChapter(modId, storyName, chapterName, sizeX, sizeZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseElevation, landscapeTileSize, biome, minBiomeShare, ringRadius, false, 1.0);
    }

    /* Same as addFirstChapter plus all optional fields, including adaptiveRing and steepnessAdaptationFactor: when adaptiveRing is true the blend ring is made wider than ringRadius where the vanilla terrain around the chapter differs a lot from its edge, to ring blocks = steepnessAdaptationFactor x the worst height difference found (1.0 = about one block of height per ring block, bigger = more gradual). It never makes the ring smaller than ringRadius and never wider than Chapter.MAX_ADAPTIVE_RING_RADIUS. steepnessAdaptationFactor must be above 0 when adaptiveRing is true. */
    public static void addFirstChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseElevation, BlockPos2D landscapeTileSize, String biome, double minBiomeShare, int ringRadius, boolean adaptiveRing, double steepnessAdaptationFactor) {
        ChapterCreateInfo info = new ChapterCreateInfo();
        info.sizeX = sizeX;
        info.sizeZ = sizeZ;
        info.landscapeName = landscapeName;
        info.landscapeBlockState = landscapeBlockState;
        info.forbiddenLandscapeBlocks = forbiddenLandscapeBlocks;
        info.environmentName = environmentName;
        info.allowedBiomes = allowedBiomes;
        info.baseElevation = chaptersBaseElevation;
        info.landscapeTileSize = landscapeTileSize;
        info.biome = biome;
        info.minBiomeShare = minBiomeShare;
        info.ringRadius = ringRadius;
        info.adaptiveRing = adaptiveRing;
        info.steepnessAdaptationFactor = steepnessAdaptationFactor;
        addFirstChapter(modId, storyName, chapterName, info);
    }

    /* Same as addChapter plus the optional chapter fields, see the addFirstChapter overload. */
    public static void addChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, int offsetFromPreviousX, int offsetFromPreviousZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight, BlockPos2D landscapeTileSize, String biome, double minBiomeShare) {
        addChapter(modId, storyName, chapterName, sizeX, sizeZ, offsetFromPreviousX, offsetFromPreviousZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight, landscapeTileSize, biome, minBiomeShare, StoryEngine.ringRadius);
    }

    /* Same as addChapter plus all optional fields, including ringRadius, see the addFirstChapter overload. */
    public static void addChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, int offsetFromPreviousX, int offsetFromPreviousZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight, BlockPos2D landscapeTileSize, String biome, double minBiomeShare, int ringRadius) {
        addChapter(modId, storyName, chapterName, sizeX, sizeZ, offsetFromPreviousX, offsetFromPreviousZ, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight, landscapeTileSize, biome, minBiomeShare, ringRadius, false, 1.0);
    }

    /* Same as addChapter plus all optional fields, including adaptiveRing and steepnessAdaptationFactor: when adaptiveRing is true the blend ring is made wider than ringRadius where the vanilla terrain around the chapter differs a lot from its edge, to ring blocks = steepnessAdaptationFactor x the worst height difference found (1.0 = about one block of height per ring block, bigger = more gradual). It never makes the ring smaller than ringRadius and never wider than Chapter.MAX_ADAPTIVE_RING_RADIUS. steepnessAdaptationFactor must be above 0 when adaptiveRing is true. */
    public static void addChapter(String modId, String storyName, String chapterName, int sizeX, int sizeZ, int offsetFromPreviousX, int offsetFromPreviousZ, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight, BlockPos2D landscapeTileSize, String biome, double minBiomeShare, int ringRadius, boolean adaptiveRing, double steepnessAdaptationFactor) {
        ChapterCreateInfo info = new ChapterCreateInfo();
        info.sizeX = sizeX;
        info.sizeZ = sizeZ;
        info.landscapeName = landscapeName;
        info.landscapeBlockState = landscapeBlockState;
        info.forbiddenLandscapeBlocks = forbiddenLandscapeBlocks;
        info.environmentName = environmentName;
        info.allowedBiomes = allowedBiomes;
        info.baseElevation = chaptersBaseHeight;
        info.landscapeTileSize = landscapeTileSize;
        info.biome = biome;
        info.minBiomeShare = minBiomeShare;
        info.ringRadius = ringRadius;
        info.adaptiveRing = adaptiveRing;
        info.steepnessAdaptationFactor = steepnessAdaptationFactor;
        info.offsetFromPrevious = new BlockPos2D(offsetFromPreviousX, offsetFromPreviousZ);
        addChapter(modId, storyName, chapterName, info);
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

    /* Required fields must be set; invalid values fail loudly here, at registration, instead of when the server starts. */
    private static void checkInfo(String chapterName, ChapterCreateInfo info) {
        if (info == null) {
            fail("Chapter " + chapterName + " has no ChapterCreateInfo.");
        }
        if (info.sizeX <= 0 || info.sizeZ <= 0) {
            fail("Chapter " + chapterName + " needs sizeX and sizeZ above 0.");
        }
        if (info.landscapeName == null || info.environmentName == null || info.landscapeBlockState == null || info.allowedBiomes == null) {
            fail("Chapter " + chapterName + " needs landscapeName, landscapeBlockState, environmentName and allowedBiomes.");
        }
        if (info.forbiddenLandscapeBlocks == null) {
            fail("Chapter " + chapterName + " has forbiddenLandscapeBlocks set to null; use an empty set.");
        }
        if (info.minBiomeShare <= 0 || info.minBiomeShare > 1) {
            fail("Chapter " + chapterName + " needs minBiomeShare above 0 and at most 1.");
        }
        if (info.ringRadius < 1) {
            fail("Chapter " + chapterName + " needs ringRadius of at least 1.");
        }
        if (info.adaptiveRing && info.steepnessAdaptationFactor <= 0) {
            fail("Chapter " + chapterName + " needs steepnessAdaptationFactor above 0 when adaptiveRing is on.");
        }
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
