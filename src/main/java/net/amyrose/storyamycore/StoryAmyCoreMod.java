package net.amyrose.storyamycore;

import net.amyrose.storyamycore.block.ModBlocks;
import net.amyrose.storyamycore.custom.ModData;
import net.amyrose.storyamycore.item.ModItemGroups;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class StoryAmyCoreMod implements ModInitializer {
	public static String MOD_ID = "storyamycore";
	@Override
	public void onInitialize() {
		ModData.LoadChapters();
		ModBlocks.registerModBlocks();
		ModItemGroups.registerItemGroups();
		System.out.println("StoryAmy Core initialized!");
	}
}