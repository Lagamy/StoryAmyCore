package net.amyrose.storyamycore.core;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.world.biome.source.BiomeAccess;

import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;

public class Story {
    public String name;
    public Zone area;
    public String ModId;
    /* Insertion-ordered: chapters are initialized in the order they were added, so each one's previous chapter is placed first. */
    public Map<String, Chapter> chapters = new LinkedHashMap<>();
    private Chapter lastAddedChapter;
    public int baseMaxDistanceRadius;

    public Story(String name, int baseMaxDistanceRadius, String modId) {
        this.name = name;
        this.ModId = modId;
        this.baseMaxDistanceRadius = baseMaxDistanceRadius;
    }

    public void RandomizeStory(Map<String, Story> initializedStories, BiomeAccess biomeAccess, long seed)
    {
        //LOGGER.info("Story {}", this.name);
        // Initialize every story at 0, 0 coords first.
        this.area = new Zone(0, 0);

        /* Placement is drawn from a Random seeded by the world seed and this story's key, so the same seed gives the same chapter positions.
           String.hashCode is specified by the JDK (stable across JVMs) and chapters is insertion-ordered, so the draw order is stable too. */
        Random random = new Random(seed ^ StoryEngine.storyKey(ModId, name).hashCode());

        // Initialize chapters and define its shape
        for(Chapter chapter : chapters.values()) {
            chapter.initializeChapter(this, biomeAccess, random);
            updateStoryBounds(chapter);
        }

//        int attempts = 0;
//        int maxAttempts = 1000; // to avoid infinite loop
//        Random rand = new Random();
//        if (!initializedStories.isEmpty()) {
//            boolean foundValidLocation = false;
//            BlockPos2D radius = StoryEngine.findStoriesSpawnRadius(initializedStories);
//            while (!foundValidLocation && attempts < maxAttempts) {
//                attempts++;
//                int xTry = rand.nextInt(radius.x * 2) - radius.x; // range: [-radius, +radius]
//                int zTry = rand.nextInt(radius.z * 2) - radius.z;
//                boolean overlaps = false;
//                for (Story story : initializedStories.values()) {
//                    if (story.area.CheckCollision(xTry + this.area.beginning.x, zTry + this.area.beginning.z, xTry + this.area.end.x, zTry + this.area.end.z)) {
//                        overlaps = true;
//                        break;
//                    }
//                }
//                if (!overlaps) {
//                    this.area.beginning.add(xTry, zTry);
//                    this.area.end.add(xTry, zTry);
//
//                    foundValidLocation = true;
//                }
//            }
//            if (!foundValidLocation) {
//                throw new RuntimeException("Couldn't find a free location for new Story.");
//            }
            // Working
//            else {
//                LOGGER.info("Success");
//            }
//                        }
        //LOGGER.info("Story {} starts at: {}. Ends at: {}. Amount of chapters: {}", this.name, this.area.beginning.toString(), this.area.end.toString(), chapters.size());
//        for(Chapter chapter : chapters.values()) {
//            chapter.addStoryCoords();
//            LOGGER.info("Chapter Area start: {}. Chapter Area end: {}",chapter.ringArea.beginning, chapter.ringArea.end);
//            //LOGGER.info("Chapter start: {}. Chapter end: {}", chapter.chapterArea.beginning,  chapter.chapterArea.end);
//            //            chapter.populateLandscapeMap(chapter.landscapeTemplate);
////            chapter.populateEnvironmentMap(chapter.environmentTemplate);
//
//        }

        initializedStories.put(StoryEngine.storyKey(ModId, name), this);
    }



    public void AddFirstChapter(String name, BlockPos2D size, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight) {
        Chapter chapter = new Chapter(size, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight);
        chapter.parentStory = this;
        chapters.put(name, chapter);
        lastAddedChapter = chapter;
    }

    public void AddChapter(String name, BlockPos2D size, BlockPos2D offsetFromPrevious, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight) {
        Chapter chapter = new Chapter(size, offsetFromPrevious, landscapeName, landscapeBlockState, forbiddenLandscapeBlocks, environmentName, allowedBiomes, chaptersBaseHeight);
        chapter.parentStory = this;
        chapter.previousChapter = lastAddedChapter;
        chapters.put(name, chapter);
        lastAddedChapter = chapter;
    }

    public void updateStoryBounds(Chapter chapter) {
        if (chapter.first) {
            // First chapter: initialize the bounding area as a copy
            area = new Zone(
                    new BlockPos2D(chapter.ringArea.beginning.x, chapter.ringArea.beginning.z),
                    new BlockPos2D(chapter.ringArea.end.x, chapter.ringArea.end.z)
            );
        } else {
            // Expand storyArea to include this chapter's area
            area.beginning.setMin(chapter.ringArea.beginning);
            area.end.setMax(chapter.ringArea.end);
        }
    }
    public NbtCompound SaveToNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.put("storyArea", area.SaveToNbt());

        // Save the chapters map
        NbtCompound chaptersNbt = new NbtCompound();
        for (Map.Entry<String, Chapter> entry : chapters.entrySet()) {
            if (entry.getValue().isPlaced()) {
                chaptersNbt.put(entry.getKey(), entry.getValue().SaveToNbt());
            }
        }
        nbt.put("chapters", chaptersNbt);

        return nbt;
    }

    public void LoadFromNbt(NbtCompound nbt) {
        // Load the story area
        if (nbt.contains("storyArea")) {
            this.area = Zone.LoadFromNbt(nbt.getCompound("storyArea"));
        }

        // Load the chapters map
        if (nbt.contains("chapters")) {
            NbtCompound chaptersNbt = nbt.getCompound("chapters");
            for (String chapterKey : chaptersNbt.getKeys()) {
                Chapter chapter = chapters.get(chapterKey);
                chapter.LoadFromNbt(chaptersNbt.getCompound(chapterKey)); // mutate existing
            }
        }
    }
}
