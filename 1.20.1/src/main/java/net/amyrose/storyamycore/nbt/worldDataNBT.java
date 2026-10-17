package net.amyrose.storyamycore.nbt;

import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import static net.amyrose.storyamycore.core.StoryEngine.storiesInitialized;

public class worldDataNBT {
    public boolean wasCreated = false;

    private static final String STORIES_KEY = "stories";
    private static final String INITIALIZED_KEY = "initialized";
    private static final String COMPASS_STORY_KEY = "compassStoryIndex";


    public static worldDataNBT fromNbt(NbtCompound nbt) {
        worldDataNBT data = new worldDataNBT();
        storiesInitialized = nbt.getBoolean(INITIALIZED_KEY);
        StoryEngine.compassStoryIndex = nbt.getInt(COMPASS_STORY_KEY);
        if (nbt.contains(STORIES_KEY, NbtElement.COMPOUND_TYPE)) {
            NbtCompound storyCompound = nbt.getCompound(STORIES_KEY);
            StoryEngine.LoadStoriesFromNBT(storyCompound);
        }

        return data;
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.put(STORIES_KEY, StoryEngine.SaveStoriesToNBT());
        nbt.putBoolean(INITIALIZED_KEY, storiesInitialized);
        nbt.putInt(COMPASS_STORY_KEY, StoryEngine.compassStoryIndex);
        return nbt;
    }
}
