package net.amyrose.storyamycore.mixin.Rendering;

import net.amyrose.storyamycore.render.ReceiverMask;
import net.amyrose.storyamycore.render.light.LightManager;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Client only. Rule: every block entity (chests, signs, banners) is drawn through this one render method, with the matrices already
   placed at the block. Calling it again with the mask provider draws the same model into the receiver mask. The method is
   overloaded with a private static helper, so the descriptor is given. */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {
    @Inject(method = "render(Lnet/minecraft/block/entity/BlockEntity;FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;)V", at = @At("TAIL"))
    private void storyamycore$markReceiver(BlockEntity blockEntity, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, CallbackInfo ci) {
        if (ReceiverMask.drawing || !LightManager.receivesBlockEntity(blockEntity)) {
            return;
        }
        ReceiverMask.drawing = true;
        try {
            ((BlockEntityRenderDispatcher) (Object) this).render(blockEntity, tickDelta, matrices, ReceiverMask.provider(vertexConsumers));
        } finally {
            ReceiverMask.drawing = false;
        }
    }
}
