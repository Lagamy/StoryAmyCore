package net.amyrose.storyamycore.mixin.Rendering;

import net.amyrose.storyamycore.render.NormalBuffer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.WindowFramebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Client only. Rule: initFbo runs on creation and on every window resize, and delete runs before a resize, so the normals
   texture follows the main framebuffer's size. Only the window framebuffer gets it. */
@Mixin(Framebuffer.class)
public class FramebufferMixin {
    @Inject(method = "initFbo", at = @At("TAIL"))
    private void storyamycore$attachNormals(int width, int height, boolean getError, CallbackInfo ci) {
        if ((Object) this instanceof WindowFramebuffer) {
            NormalBuffer.attach(((Framebuffer) (Object) this).fbo, width, height);
        }
    }

    @Inject(method = "delete", at = @At("HEAD"))
    private void storyamycore$releaseNormals(CallbackInfo ci) {
        if ((Object) this instanceof WindowFramebuffer) {
            NormalBuffer.release();
        }
    }
}
