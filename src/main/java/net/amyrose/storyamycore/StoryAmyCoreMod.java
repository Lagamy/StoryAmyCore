package net.amyrose.storyamycore;

import net.amyrose.storyamycore.custom.ModData;
import net.fabricmc.api.ModInitializer;

public class StoryAmyCoreMod implements ModInitializer {
	@Override
	public void onInitialize() {
		ModData.LoadChapters();
		System.out.println("StoryAmy Core initialized!");
	}
}