package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.gen.feature.ConfiguredFeature;
import net.minecraft.world.gen.feature.Feature;
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
    private void skipPositionsInNoFeaturesMask(Stream<BlockPos> stream, Consumer<BlockPos> consumer) {
        /* PlacedFeature instances are shared by all worldgen threads, so no per-feature state is kept here (the old version stored the chunk in a field).
           Filtering the stream drops masked positions before the original consumer runs, so it still sets the return value itself.
           Ground cover (random patches and flowers) is told apart by its feature type, because a chapter can allow it inside the mask (litterGrass). */
        PlacedFeature placed = (PlacedFeature) (Object) this;
        ConfiguredFeature<?, ?> configured = placed.feature().value();
        boolean grass = configured.feature() == Feature.RANDOM_PATCH || configured.feature() == Feature.FLOWER || configured.feature() == Feature.NO_BONEMEAL_FLOWER;
        stream.filter(placedPos -> !StoryEngine.isInNoFeaturesMask(placedPos.getX(), placedPos.getZ(), grass)).forEach(consumer);
    }
}
