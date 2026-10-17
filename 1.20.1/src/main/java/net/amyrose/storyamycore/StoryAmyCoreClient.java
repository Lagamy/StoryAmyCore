package net.amyrose.storyamycore;

import net.amyrose.storyamycore.item.ModItems;
import net.amyrose.storyamycore.item.StoryCompassItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.object.builder.v1.client.model.FabricModelPredicateProviderRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public class StoryAmyCoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        /* Same math as vanilla CompassItem: bearing to the target relative to the holder's yaw, wrapped to 0..1 for the
           32 compass frames in the model overrides. Without a target the needle spins slowly. */
        FabricModelPredicateProviderRegistry.register(ModItems.STORY_COMPASS, new Identifier("angle"), (stack, world, entity, seed) -> {
            NbtCompound nbt = stack.getNbt();
            Entity holder = entity != null ? entity : MinecraftClient.getInstance().player;
            if (holder == null) {
                return 0.0f;
            }
            if (nbt == null || !nbt.contains(StoryCompassItem.TARGET_X_KEY) || !nbt.contains(StoryCompassItem.TARGET_Z_KEY)) {
                return (float) ((System.currentTimeMillis() % 3000L) / 3000.0);
            }
            double yaw = MathHelper.floorMod(holder.getYaw() / 360.0, 1.0);
            double toTarget = Math.atan2(nbt.getInt(StoryCompassItem.TARGET_Z_KEY) + 0.5 - holder.getZ(),
                    nbt.getInt(StoryCompassItem.TARGET_X_KEY) + 0.5 - holder.getX()) / (Math.PI * 2);
            return MathHelper.floorMod((float) (0.5 - (yaw - 0.25 - toTarget)), 1.0f);
        });

        /* Drawn centered above the hotbar and the status bars (health, hunger, air). */
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null || client.options.hudHidden) {
                return;
            }
            ItemStack held = client.player.getMainHandStack();
            if (!held.isOf(ModItems.STORY_COMPASS)) {
                held = client.player.getOffHandStack();
            }
            if (!held.isOf(ModItems.STORY_COMPASS) || held.getNbt() == null) {
                return;
            }
            String storyName = held.getNbt().getString(StoryCompassItem.STORY_NAME_KEY);
            if (storyName.isEmpty()) {
                return;
            }
            drawContext.drawCenteredTextWithShadow(client.textRenderer, storyName,
                    drawContext.getScaledWindowWidth() / 2, drawContext.getScaledWindowHeight() - 72, 0xFFFFFF);
        });
    }
}
