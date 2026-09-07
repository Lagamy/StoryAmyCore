package net.amyrose.storyamycore;

import net.amyrose.storyamycore.core.BlockPos2D;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
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
		StoryEngine.addStory("testStory", 1000, MOD_ID);
		Story story = StoryEngine.stories.get("testStory");
		story.AddFirstChapter(
				"testChapter",
				new BlockPos2D(281, 188),
				"prologue-landscape",
				Blocks.GRASS_BLOCK.getDefaultState(),
				Set.of(
						Blocks.STONE.getDefaultState(),
						Blocks.DIRT.getDefaultState(),
						Blocks.GRAVEL.getDefaultState(),
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
						"minecraft:jungle",
						"minecraft:sparse_jungle",
						"minecraft:bamboo_jungle"
				),
				64
		);
		ModItemGroups.registerItemGroups();
	}
}