package net.amyrose.storyamycore.mixin.Rendering;

import com.mojang.blaze3d.systems.RenderSystem;
import net.amyrose.storyamycore.render.light.LightManager;
import net.amyrose.storyamycore.render.post.PostEffectManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Client only. Rule: GameRenderer.render draws the world (hand included), then vanilla's own post processor, then rebinds the main
   framebuffer for the HUD with beginWrite. Injecting right before that call runs our effects over the finished world and
   before any HUD or screen is drawn. It is the only beginWrite call in render, and it is on the render thread, which GL needs. */
@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gl/Framebuffer;beginWrite(Z)V"))
    private void storyamycore$postEffects(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        PostEffectManager.render(tickDelta);
    }

    /* Rule: GameRenderer.renderHand draws the first-person hand and held item after the world, with the hand projection set and
       the depth buffer cleared. At its end, the projection is still the hand's, which the lights with receivers need to light the
       hand and the held item (see LightManager.renderHand). */
    @Inject(method = "renderHand", at = @At("TAIL"))
    private void storyamycore$handLights(MatrixStack matrices, Camera camera, float tickDelta, CallbackInfo ci) {
        if (MinecraftClient.getInstance().options.getPerspective().isFirstPerson()) {
            LightManager.renderHand(camera, RenderSystem.getProjectionMatrix());
        }
    }
}
