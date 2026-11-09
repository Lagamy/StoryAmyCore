package net.amyrose.storyamycore.mixin.Rendering;

import net.amyrose.storyamycore.render.NormalBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Client only. Rule: RenderLayer inherits startDrawing/endDrawing from RenderPhase, and both vanilla (WorldRenderer.renderLayer,
   RenderLayer.draw) and Sodium (TerrainRenderPass) call them around every layer they draw. The sub-phases of a layer call them
   too, so only RenderLayer instances count. */
@Mixin(RenderPhase.class)
public class RenderPhaseMixin {
    @Inject(method = "startDrawing", at = @At("RETURN"))
    private void storyamycore$beginNormals(CallbackInfo ci) {
        if ((Object) this instanceof RenderLayer) {
            NormalBuffer.begin();
        }
    }

    @Inject(method = "endDrawing", at = @At("HEAD"))
    private void storyamycore$endNormals(CallbackInfo ci) {
        if ((Object) this instanceof RenderLayer) {
            NormalBuffer.end();
        }
    }
}
