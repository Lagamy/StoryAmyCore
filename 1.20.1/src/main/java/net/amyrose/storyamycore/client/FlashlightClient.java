package net.amyrose.storyamycore.client;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.amyrose.storyamycore.api.StoryAmyClientApi;
import net.amyrose.storyamycore.item.ModItems;
import net.amyrose.storyamycore.render.light.Light;
import net.amyrose.storyamycore.sound.ModSounds;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import org.lwjgl.glfw.GLFW;

/* Client only. Started from StoryAmyCoreClient after LightManager.init(). */
public class FlashlightClient {
    private static boolean flashlightOn;
    private static boolean flashlightFar;

    public static void init() {
        /* The built-in example of the light API: the flashlight is an ordinary light registered like any mod's would be, with cookie 10
           projected from the camera. It is only visible while the flashlight item is held and switched on. The key (or nothing else)
           switches it on and off; right click while on toggles the far beam. */
        Light flashlight = new Light(new Identifier(StoryAmyCoreMod.MOD_ID, "textures/misc/flashlight_cookies/10.png"));
        flashlight.followCamera = true;
        flashlight.enabled = false;
        /* The near beam: these are the flashlight's own values (same as the json defaults). The far beam is derived from them every
           frame, 80% more range and 70% less fov, so changing the numbers here changes both. */
        float nearRange = 25.0f;
        float nearFov = 62.0f;
        flashlight.range = nearRange;
        flashlight.fovDegrees = nearFov;
        flashlight.updater = (light, context) -> {
            light.enabled = flashlightOn && isHoldingFlashlight();
            light.range = flashlightFar ? nearRange * 1.8f : nearRange;
            light.fovDegrees = flashlightFar ? nearFov * 0.3f : nearFov;
        };
        StoryAmyClientApi.registerLight(StoryAmyCoreMod.MOD_ID, "flashlight", flashlight);

        KeyBinding flashlightKey = KeyBindingHelper.registerKeyBinding(
                new KeyBinding("key.storyamycore.flashlight", GLFW.GLFW_KEY_G, "key.category.storyamycore.flashlight"));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (flashlightKey.wasPressed()) {
                if (isHoldingFlashlight()) {
                    flashlightOn = !flashlightOn;
                    playClick(client);
                }
            }
        });

        /* UseItemCallback also fires on the server, where the light state does not exist, so it is limited to the client world.
           It returns PASS on purpose: any accepted result makes vanilla swing the arm and replay the held-item raise animation, which
           is unwanted for a toggle. Because PASS lets the other hand fire too, the off hand is skipped when the main hand holds one. */
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getStackInHand(hand);
            if (!world.isClient || !stack.isOf(ModItems.FLASHLIGHT) || !flashlightOn
                    || (hand == Hand.OFF_HAND && player.getMainHandStack().isOf(ModItems.FLASHLIGHT))) {
                return TypedActionResult.pass(stack);
            }
            flashlightFar = !flashlightFar;
            playClick(MinecraftClient.getInstance());
            return TypedActionResult.pass(stack);
        });
    }

    private static boolean isHoldingFlashlight() {
        MinecraftClient client = MinecraftClient.getInstance();
        return client.player != null && (client.player.getMainHandStack().isOf(ModItems.FLASHLIGHT)
                || client.player.getOffHandStack().isOf(ModItems.FLASHLIGHT));
    }

    private static void playClick(MinecraftClient client) {
        if (client.player != null) {
            client.player.playSound(ModSounds.FLASHLIGHT_CLICK, 0.2f, 1.0f);
        }
    }
}
