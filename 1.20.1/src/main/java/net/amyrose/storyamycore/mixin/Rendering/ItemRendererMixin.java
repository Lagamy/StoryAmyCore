package net.amyrose.storyamycore.mixin.Rendering;

import net.amyrose.storyamycore.render.ReceiverMask;
import net.amyrose.storyamycore.render.light.LightManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.TexturedRenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* Client only. Rule: every item draw (held in first and third person, dropped, in frames) ends in this renderItem overload, with the
   matrices already set up for the model's display transform. Drawing the model once more right after the normal draw, with the same
   matrices and the mask copy of the item layer, marks it as a receiver. The GUI is skipped: it is drawn after the world lights. */
@Mixin(ItemRenderer.class)
public abstract class ItemRendererMixin {
    @Shadow
    private void renderBakedItemModel(BakedModel model, ItemStack stack, int light, int overlay, MatrixStack matrices, VertexConsumer vertices) {
    }

    @Inject(method = "renderItem(Lnet/minecraft/item/ItemStack;Lnet/minecraft/client/render/model/json/ModelTransformationMode;ZLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;IILnet/minecraft/client/render/model/BakedModel;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/item/ItemRenderer;renderBakedItemModel(Lnet/minecraft/client/render/model/BakedModel;Lnet/minecraft/item/ItemStack;IILnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;)V", shift = At.Shift.AFTER))
    private void storyamycore$markReceiver(ItemStack stack, ModelTransformationMode mode, boolean leftHanded, MatrixStack matrices,
                                           VertexConsumerProvider vertexConsumers, int light, int overlay, BakedModel model, CallbackInfo ci) {
        if (mode == ModelTransformationMode.GUI || ReceiverMask.drawing || !LightManager.receivesItem(stack)) {
            return;
        }
        RenderLayer mask = ReceiverMask.layerFor(TexturedRenderLayers.getEntityCutout());
        renderBakedItemModel(model, stack, light, overlay, matrices, vertexConsumers.getBuffer(mask));
    }
}
