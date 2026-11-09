package net.amyrose.storyamycore.mixin.Rendering;

import net.amyrose.storyamycore.render.ReceiverMask;
import net.amyrose.storyamycore.render.entity.EntityShaderManager;
import net.amyrose.storyamycore.render.light.LightManager;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/* Client only. Rule: EntityRenderDispatcher.render hands one VertexConsumerProvider to the entity's renderer, and that renderer
   and all its feature layers (armor, held items) get their buffers from it. Replacing the argument of this one call therefore
   covers the whole entity. Arguments of EntityRenderer.render: 0 entity, 1 yaw, 2 tickDelta, 3 matrices, 4 provider, 5 light. */
@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @ModifyArgs(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V"))
    private void storyamycore$swapEntityShader(Args args) {
        args.set(4, EntityShaderManager.wrap(args.<Entity>get(0), args.<VertexConsumerProvider>get(4)));
    }

    /* Same call, right after it: an entity matched by a light's receivers is drawn once more with the mask provider (on top of the
       entity shader wrapper, so the mask copy of the swapped layer is used). The matrices are still the entity's own. */
    @Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/entity/EntityRenderer;render(Lnet/minecraft/entity/Entity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", shift = At.Shift.AFTER))
    private void storyamycore$markReceiver(Entity entity, double x, double y, double z, float yaw, float tickDelta, MatrixStack matrices,
                                           VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        if (ReceiverMask.drawing || !LightManager.receivesEntity(entity)) {
            return;
        }
        EntityRenderer<? super Entity> renderer = ((EntityRenderDispatcher) (Object) this).getRenderer(entity);
        ReceiverMask.drawing = true;
        try {
            renderer.render(entity, yaw, tickDelta, matrices, ReceiverMask.provider(EntityShaderManager.wrap(entity, vertexConsumers)), light);
        } finally {
            ReceiverMask.drawing = false;
        }
    }
}
