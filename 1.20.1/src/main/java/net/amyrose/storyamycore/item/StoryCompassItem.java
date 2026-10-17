package net.amyrose.storyamycore.item;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class StoryCompassItem extends Item {
    public static final String STORY_NAME_KEY = "storyName";
    public static final String TARGET_X_KEY = "targetX";
    public static final String TARGET_Z_KEY = "targetZ";

    public StoryCompassItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity player && !StoryEngine.stories.isEmpty()) {
            StoryEngine.compassStoryIndex = Math.floorMod(StoryEngine.compassStoryIndex + 1, StoryEngine.stories.size());
            refresh(stack);
            world.playSound(null, player.getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), player.getSoundCategory(), 0.6f, 1.2f);
            Story story = StoryEngine.getCompassStory();
            player.sendMessage(Text.literal(story.name), true);
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (!world.isClient) {
            refresh(stack);
        }
    }

    /* The client has no access to StoryEngine on a dedicated server, so the story name and target are stored in the stack NBT,
       which the normal slot sync sends to the owning client (read by the angle predicate and the HUD in StoryAmyCoreClient).
       Values are only written when they change so the stack does not resync every tick. */
    private static void refresh(ItemStack stack) {
        Story story = StoryEngine.getCompassStory();
        Chapter target = StoryEngine.getCompassTarget(story);
        NbtCompound nbt = stack.getOrCreateNbt();

        if (story == null) {
            nbt.remove(STORY_NAME_KEY);
        } else if (!story.name.equals(nbt.getString(STORY_NAME_KEY))) {
            nbt.putString(STORY_NAME_KEY, story.name);
        }

        if (target == null) {
            nbt.remove(TARGET_X_KEY);
            nbt.remove(TARGET_Z_KEY);
            return;
        }
        int x = (target.chapterArea.beginning.x + target.chapterArea.end.x) / 2;
        int z = (target.chapterArea.beginning.z + target.chapterArea.end.z) / 2;
        if (!nbt.contains(TARGET_X_KEY) || nbt.getInt(TARGET_X_KEY) != x) {
            nbt.putInt(TARGET_X_KEY, x);
        }
        if (!nbt.contains(TARGET_Z_KEY) || nbt.getInt(TARGET_Z_KEY) != z) {
            nbt.putInt(TARGET_Z_KEY, z);
        }
    }
}
