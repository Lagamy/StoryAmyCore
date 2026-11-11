package net.amyrose.storyamycore;

import net.amyrose.storyamycore.core.StoryEngine;
import net.amyrose.storyamycore.core.StructureStreamJobs;
import net.amyrose.storyamycore.item.ModItemGroups;
import net.amyrose.storyamycore.item.ModItems;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;

import java.util.Set;

public class StoryAmyCoreMod implements ModInitializer {
	public static String MOD_ID = "storyamycore";

	@Override
	public void onInitialize() {
		ModItems.registerItems();
		ModItemGroups.registerItemGroups();

		/* Join and tick events run on the server thread. A player without a compass gets one, so a lost compass is regiven on next login. */
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			if (StoryEngine.placementFailure != null) {
				handler.disconnect(Text.literal(StoryEngine.placementFailure));
				return;
			}
			if (!handler.player.getInventory().containsAny(Set.of(ModItems.STORY_COMPASS))) {
				handler.player.giveItemStack(new ItemStack(ModItems.STORY_COMPASS));
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(StoryEngine::markVisitedChapters);
		ServerTickEvents.END_SERVER_TICK.register(StructureStreamJobs::tick);
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> StructureStreamJobs.clear());
	}
}