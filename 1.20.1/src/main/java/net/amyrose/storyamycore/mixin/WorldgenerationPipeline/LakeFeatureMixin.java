package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.util.FeatureContext;
import net.minecraft.world.gen.feature.LakeFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LakeFeature.class)
public class LakeFeatureMixin {
    @Inject(method = "generate", at = @At("HEAD"), cancellable = true)
    private void preventLakesInChapters(FeatureContext<LakeFeature.Config> context, CallbackInfoReturnable<Boolean> cir) {
        /* In 1.20.1 LakeFeature only places lava lakes (surface and underground), which open pits in the terrain.
           A lake reaches up to 8 blocks from its origin in X and Z, so that whole box is checked against the rings.
           Returning false makes the feature place nothing. */
        BlockPos origin = context.getOrigin();
        if (StoryEngine.isInsideAnyRing(origin.getX() - 8, origin.getZ() - 8, origin.getX() + 8, origin.getZ() + 8)) {
            cir.setReturnValue(false);
        }
    }
}
