package net.amyrose.storyamycore.core;

import net.minecraft.util.math.BlockPos;

import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;

public class Story {
    public String name;
    public Zone area;
    public String ModId;
    public static Map<String, Chapter> chapters = new HashMap<>();

    public Story(String name, String modId) {
        this.name = name;
        this.ModId = modId;
    }

    public void RandomizeStory()
    {
        int attempts = 0;
        int maxAttempts = 1000; // to avoid infinite loop
        Random rand = new Random();
        if (!StoryEngine.stories.isEmpty()) {
            boolean foundValidLocation = false;
            int radius = StoryEngine.maxDistanceBetweenStories * StoryEngine.stories.size();
            while (!foundValidLocation && attempts < maxAttempts) {
                attempts++;
                int xTry = rand.nextInt(radius * 2) - radius; // range: [-radius, +radius]
                int zTry = rand.nextInt(radius * 2) - radius;
                boolean overlaps = false;
                for (Story story : StoryEngine.stories) {
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
        LOGGER.info("{} starts at: {}", name, area.beginning.toString());

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
}
