package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;
import java.util.stream.Stream;

@Mixin(PlacedFeature.class)
public class FeaturesMixin {
    @Redirect(
            method = "generate(Lnet/minecraft/world/gen/feature/FeaturePlacementContext;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V"
            )
    )
    private void skipPositionsInFoliageMask(Stream<BlockPos> stream, Consumer<BlockPos> consumer) {
        /* PlacedFeature instances are shared by all worldgen threads, so no per-feature state is kept here (the old version stored the chunk in a field).
           Filtering the stream drops masked positions before the original consumer runs, so it still sets the return value itself. */
        stream.filter(placedPos -> !StoryEngine.isInNoFoliageMask(placedPos.getX(), placedPos.getZ())).forEach(consumer);
    }
}
