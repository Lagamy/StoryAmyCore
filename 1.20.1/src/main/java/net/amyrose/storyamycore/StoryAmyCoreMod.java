package net.amyrose.storyamycore;

import net.amyrose.storyamycore.api.StoryAmyApi;
import net.amyrose.storyamycore.item.ModItemGroups;
import net.fabricmc.api.ModInitializer;
import net.minecraft.block.Blocks;
import net.minecraft.block.GrassBlock;

import java.util.ArrayList;
import java.util.Set;

public class StoryAmyCoreMod implements ModInitializer {
	public static String MOD_ID = "storyamycore";

	@Override
	public void onInitialize() {
		StoryAmyApi.registerStory(MOD_ID, "testStory", 1000);
		StoryAmyApi.addFirstChapter(
				MOD_ID,
				"testStory",
				"testChapter",
				281, 188,
				"prologue-landscape",
				Blocks.GRASS_BLOCK.getDefaultState(),
				Set.of(
						Blocks.STONE.getDefaultState(),
						Blocks.DIRT.getDefaultState(),
						Blocks.GRAVEL.getDefaultState(),
						Blocks.SAND.getDefaultState(),
						Blocks.DIORITE.getDefaultState()
				),
				"prologue-environment",
				Set.of(
						"minecraft:forest",
						"minecraft:flower_forest",
						"minecraft:birch_forest",
						"minecraft:old_growth_birch_forest",
						"minecraft:dark_forest",
						"minecraft:pale_garden",
						"minecraft:windswept_forest",
						"minecraft:taiga",
						"minecraft:snowy_taiga",
						"minecraft:old_growth_pine_taiga",
						"minecraft:old_growth_spruce_taiga",
						"minecraft:grove",
						"minecraft:sparse_jungle",
						"minecraft:bamboo_jungle"
				),
				64
		);
		ModItemGroups.registerItemGroups();
	}
}