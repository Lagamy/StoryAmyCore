package net.amyrose.storyamycore.nbt;

import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

public class worldDataNBT {
    public boolean wasCreated = false;

    private static final String STORIES_KEY = "stories";

    public static worldDataNBT fromNbt(NbtCompound nbt) {
        worldDataNBT data = new worldDataNBT();

        if (nbt.contains(STORIES_KEY, NbtElement.COMPOUND_TYPE)) {
            NbtCompound storyCompound = nbt.getCompound(STORIES_KEY);
            StoryEngine.LoadStoriesFromNBT(storyCompound);
        }

        return data;
    }

    public NbtCompound writeNbt(NbtCompound nbt) {
        nbt.put(STORIES_KEY, StoryEngine.SaveStoriesToNBT());
        return nbt;
    }
}
