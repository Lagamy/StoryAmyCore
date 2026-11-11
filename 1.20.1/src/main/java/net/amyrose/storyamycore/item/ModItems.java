package net.amyrose.storyamycore.item;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModItems {
    public static final Item STORY_BOOK = Registry.register(Registries.ITEM, new Identifier(StoryAmyCoreMod.MOD_ID, "story_book"),
            new StoryBookItem(new FabricItemSettings().maxCount(1)));

    public static final Item STORY_COMPASS = Registry.register(Registries.ITEM, new Identifier(StoryAmyCoreMod.MOD_ID, "story_compass"),
            new StoryCompassItem(new FabricItemSettings().maxCount(1)));

    public static void registerItems() {}
}
