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
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

/* Client only. Started from StoryAmyCoreClient after LightManager.init(). */
public class FlashlightClient {
    private static boolean flashlightOn;
    private static boolean flashlightFar;

    public static void init() {
        /* The built-in example of the light API: the flashlight is an ordinary light registered like any mod's would be, with cookie 10
           projected from the player's head. It is only visible while the flashlight item is held and switched on. The key (or nothing else)
           switches it on and off; right click while on toggles the far beam. */
        Light flashlight = new Light(new Identifier(StoryAmyCoreMod.MOD_ID, "textures/misc/flashlight_cookies/10.png"));
        flashlight.enabled = false;
        /* The near beam: these are the flashlight's own values (same as the json defaults). The far beam is derived from them every
           frame, 80% more range and 70% less fov, so changing the numbers here changes both. */
        float nearRange = 25.0f;
        float nearFov = 62.0f;
        flashlight.range = nearRange;
        flashlight.fovDegrees = nearFov;
        /* The light sits at the player's head and follows the player's yaw and pitch instead of the camera, so it stays correct in
           second and third person, where the camera is not at the head. */
        flashlight.updater = (light, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player != null) {
                float tickDelta = context.tickDelta();
                light.position = client.player.getCameraPosVec(tickDelta);
                light.yaw = client.player.getYaw(tickDelta);
                light.pitch = client.player.getPitch(tickDelta);
            }
            light.enabled = flashlightOn && isHoldingFlashlight();
            light.range = flashlightFar ? nearRange * 1.8f : nearRange;
            light.fovDegrees = flashlightFar ? nearFov * 0.3f : nearFov;
        };
        StoryAmyClientApi.registerLight(StoryAmyCoreMod.MOD_ID, "flashlight", flashlight);

        /* A point light at the tip that affects only the flashlight item itself (held in either view, or dropped), so it lights the
           flashlight and nothing around it. The tip is a fixed offset from the head: 0.8 forward, 0.3 right, 0.25 down. */
        /* A point light ignores its cookie, but the field must still name an existing texture. */
        Light tip = new Light(new Identifier(StoryAmyCoreMod.MOD_ID, "textures/misc/flashlight_cookies/10.png"));
        tip.point = true;
        tip.affectItem(ModItems.FLASHLIGHT);
        tip.enabled = false;
        tip.range = 1.0f;
        tip.intensity = 3.0f;
        tip.color = new Vector3f(1.0f, 0.95f, 0.8f);
        tip.updater = (light, context) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            light.enabled = flashlightOn && isHoldingFlashlight();
            if (client.player != null) {
                float tickDelta = context.tickDelta();
                double yawRad = Math.toRadians(client.player.getYaw(tickDelta));
                Vec3d forward = client.player.getRotationVec(tickDelta);
                Vec3d right = new Vec3d(-Math.cos(yawRad), 0.0, -Math.sin(yawRad));
                /* The tip sits on the side of the arm holding the flashlight: the main arm for the main hand, the other one for the off
                   hand, so moving it between hands moves the light with it. */
                boolean inMainHand = client.player.getMainHandStack().isOf(ModItems.FLASHLIGHT);
                boolean rightSide = (client.player.getMainArm() == Arm.RIGHT) == inMainHand;
                light.position = client.player.getCameraPosVec(tickDelta)
                        .add(forward.multiply(0.8)).add(right.multiply(rightSide ? 0.3 : -0.3)).add(0.0, -0.25, 0.0);
            }
        };
        StoryAmyClientApi.registerLight(StoryAmyCoreMod.MOD_ID, "flashlight_tip", tip);

        /* Model predicate for the item models: 1 while the flashlight is on and the stack is in the local player's hands, which
           switches flashlight.json to flashlight_on.json (creamy white lens); otherwise the grey lens is shown. The entity passed in
           is not always the player (it can be null), so the stack is also compared with the player's two hand stacks, which are the
           same objects the hand renderers pass. */
        ModelPredicateProviderRegistry.register(ModItems.FLASHLIGHT, new Identifier(StoryAmyCoreMod.MOD_ID, "lit"),
                (stack, world, entity, seed) -> {
                    PlayerEntity player = MinecraftClient.getInstance().player;
                    boolean inHands = player != null && (entity == player || stack == player.getMainHandStack() || stack == player.getOffHandStack());
                    return flashlightOn && inHands ? 1.0f : 0.0f;
                });

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
