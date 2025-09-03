package net.amyrose.storyamycore.core;

import net.minecraft.nbt.NbtCompound;
import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;

public class Story {
    public String name;
    public Zone area;
    public String ModId;
    public Map<String, Chapter> chapters = new HashMap<>();

    public Story(String name, String modId) {
        this.name = name;
        this.ModId = modId;
    }

    public void RandomizeStory(Map<String, Story> initializedStories)
    {
        //LOGGER.info("Story {}", this.name);
        // Initialize every story at 0, 0 coords first.
        this.area = new Zone(0, 0);

        // Initialize chapters and define its shape
        for(Chapter chapter : chapters.values()) {
            chapter.initializeChapter(this);
            updateStoryBounds(chapter);
        }

        int attempts = 0;
        int maxAttempts = 1000; // to avoid infinite loop
        Random rand = new Random();
        if (!initializedStories.isEmpty()) {
            boolean foundValidLocation = false;
            BlockPos2D radius = StoryEngine.findStoriesSpawnRadius(initializedStories);
            while (!foundValidLocation && attempts < maxAttempts) {
                attempts++;
                int xTry = rand.nextInt(radius.x * 2) - radius.x; // range: [-radius, +radius]
                int zTry = rand.nextInt(radius.z * 2) - radius.z;
                boolean overlaps = false;
                for (Story story : initializedStories.values()) {
                    if (story.area.CheckCollision(xTry + this.area.beginning.x, zTry + this.area.beginning.z, xTry + this.area.end.x, zTry + this.area.end.z)) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    this.area.beginning.add(xTry, zTry);
                    this.area.end.add(xTry, zTry);

                    foundValidLocation = true;
                }
            }
            if (!foundValidLocation) {
                throw new RuntimeException("Couldn't find a free location for new Story.");
            }
            // Working
//            else {
//                LOGGER.info("Success");
//            }
        }
        LOGGER.info("Story {} starts at: {}. Ends at: {}. Amount of chapters: {}", this.name, this.area.beginning.toString(), this.area.end.toString(), chapters.size());
        for(Chapter chapter : chapters.values()) {
            chapter.addStoryCoords();
            LOGGER.info("Story: {}. Chapter start: {}. Chapter end: {}", this.name, chapter.ringArea.beginning, chapter.ringArea.end);
            //            chapter.populateLandscapeMap(chapter.landscapeTemplate);
//            chapter.populateEnvironmentMap(chapter.environmentTemplate);

        }

        initializedStories.put(this.name, this);
    }

    public void AddFirstChapter(String name, BlockPos2D size, String landscapeName, String environmentName,  String biomeName) {
        chapters.put(name, new Chapter(size, landscapeName, environmentName, biomeName));
    }

    public void AddChapter(String name, BlockPos2D size, BlockPos2D offsetFromPrevious, String landscapeName, String environmentName,  String biomeName) {
        chapters.put(name, new Chapter(size, offsetFromPrevious, landscapeName, environmentName, biomeName));
    }

    public void updateStoryBounds(Chapter chapter) {
        if (chapter.first) {
            // First chapter: initialize the bounding area directly
                area = chapter.ringArea;
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
            chaptersNbt.put(entry.getKey(), entry.getValue().SaveToNbt());
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
