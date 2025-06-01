package net.amyrose.storyamycore.core;

import net.minecraft.nbt.NbtCompound;
import java.util.*;

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
                    if (story.area.inBounds(xTry, zTry)) {
                        overlaps = true;
                        break;
                    }
                }
                if (!overlaps) {
                    this.area = new Zone(xTry, zTry);
                    foundValidLocation = true;
                }
            }
            if (!foundValidLocation) {
                throw new RuntimeException("Couldn't find a free location for new Story.");
            }
        } else {
            // First story — safe default
            this.area = new Zone(-StoryEngine.ringRadius, -StoryEngine.ringRadius); // So chapter would start at 0
        }
        initializedStories.put(this.name, this);

        for(Chapter chapter : chapters.values()) {
            chapter.initializeChapter(this);
            updateStoryBounds(chapter);
        }
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
