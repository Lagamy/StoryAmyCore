package net.amyrose.storyamycore.mixin.Rendering;

import net.amyrose.storyamycore.render.NormalBuffer;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Client only. Rule: particles are not drawn through a RenderLayer (each particle sheet sets its own shader and draws with the
   tessellator), so RenderPhaseMixin never sees them. renderParticles wraps all the sheets of one frame, so the extra attachments
   are enabled for its whole duration. Every vanilla sheet uses the overridden particle shader. */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {
    @Inject(method = "renderParticles", at = @At("HEAD"))
    private void storyamycore$beginNormals(CallbackInfo ci) {
        NormalBuffer.beginAlways();
    }

    @Inject(method = "renderParticles", at = @At("RETURN"))
    private void storyamycore$endNormals(CallbackInfo ci) {
        NormalBuffer.end();
    }
}
