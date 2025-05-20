package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.gen.feature.*;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.amyrose.storyamycore.core.StoryEngine.XandZAsLong;

@Mixin(PlacedFeature.class)
public class FeaturesMixin {
    @Redirect(
            method = "generate(Lnet/minecraft/world/gen/feature/FeaturePlacementContext;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V"
            )
    )
    private void redirectForEach(Stream<BlockPos> stream, Consumer<BlockPos> consumer, FeaturePlacementContext context, Random random, BlockPos pos) {
        ConfiguredFeature<?, ?> configuredFeature = ((PlacedFeature)(Object)this).feature().value();
        MutableBoolean mutableBoolean = new MutableBoolean();
        stream.forEach(placedPos -> {
            for(Story story : StoryEngine.stories) {
                if (story.area.inBounds(placedPos.getX(), placedPos.getZ())) {
                    for (Chapter chapter : story.chapters.values()) {
                        if (chapter.chapterArea.inBounds(placedPos.getX(), placedPos.getZ())) {
                            if (chapter.noFoliageMask.contains(XandZAsLong(placedPos.getX(), placedPos.getZ()))) {
                                return; // Skip generation
                            }
                            break; // Apply first chapter's mask and exit
                        }
                    }
                }
            }
            if (configuredFeature.generate(context.getWorld(), context.getChunkGenerator(), random, placedPos)) {
                mutableBoolean.setTrue();
            }
        });
        // LOGGER.debug("Feature checks took {} µs", (System.nanoTime() - start) / 1_000.0);
    }
}
