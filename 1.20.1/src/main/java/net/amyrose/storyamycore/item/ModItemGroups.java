package net.amyrose.storyamycore.item;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ModItemGroups {
    public static final ItemGroup STORYAMY_GROUP = Registry.register(Registries.ITEM_GROUP, new Identifier(StoryAmyCoreMod.MOD_ID),
            FabricItemGroup.builder().displayName(Text.translatable("itemGroup.storyamycore")).icon(() -> new ItemStack(ModItems.FLASHLIGHT)).entries((displayContext, entries) -> {
                entries.add(ModItems.STORY_BOOK);
                entries.add(ModItems.STORY_COMPASS);
                entries.add(ModItems.FLASHLIGHT);
                entries.add(ModItems.FLASHLIGHT_BLOCK);
            }).build());
    public static void registerItemGroups() {}
}
