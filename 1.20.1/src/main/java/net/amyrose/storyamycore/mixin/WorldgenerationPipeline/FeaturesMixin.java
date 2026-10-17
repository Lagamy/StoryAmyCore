package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.IChunkChapters;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.feature.*;
import org.apache.commons.lang3.mutable.MutableBoolean;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;
import java.util.stream.Stream;

import static net.amyrose.storyamycore.core.StoryEngine.XandZAsLong;


@Mixin(PlacedFeature.class)
public class FeaturesMixin {
    @Unique
    public IChunkChapters Ichunk; // not static, per-instance

    //Chunk chunk = world.getChunk(pos);

    @Inject(method = "generate(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void getIchunk(StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Ichunk = (IChunkChapters) world.getChunk(pos);
    }
    @Inject(method = "    generateUnregistered(Lnet/minecraft/world/StructureWorldAccess;Lnet/minecraft/world/gen/chunk/ChunkGenerator;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Z", at = @At("HEAD"), cancellable = true)
    private void getIchunkUnregistered(StructureWorldAccess world, ChunkGenerator generator, Random random, BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        Ichunk = (IChunkChapters) world.getChunk(pos);
    }

    @Redirect(
            method = "generate(Lnet/minecraft/world/gen/feature/FeaturePlacementContext;Lnet/minecraft/util/math/random/Random;Lnet/minecraft/util/math/BlockPos;)Z",
            at = @At(
                    value = "INVOKE",
                    target = "Ljava/util/stream/Stream;forEach(Ljava/util/function/Consumer;)V"
            )
    )
    private void redirectForEach(Stream<BlockPos> stream, Consumer<BlockPos> consumer, FeaturePlacementContext context, Random random, BlockPos pos) {

            ConfiguredFeature<?, ?> configuredFeature = ((PlacedFeature) (Object) this).feature().value();
            MutableBoolean mutableBoolean = new MutableBoolean();
            stream.forEach(placedPos -> {
                if(Ichunk.getSize() != 0)
                {
                    for (Chapter chapter : Ichunk.getPresentChapters())
                    {
                        if (chapter.chapterArea.inBounds(placedPos.getX(), placedPos.getZ()))
                        {
                            if (chapter.noFoliageMask.contains(XandZAsLong(placedPos.getX(), placedPos.getZ())))
                            {
                                return; // Skip generation
                            }
                            break; // Apply first chapter's mask and exit
                        }
                    }
                }
                if (configuredFeature.generate(context.getWorld(), context.getChunkGenerator(), random, placedPos))
                {
                    mutableBoolean.setTrue();
                }
            });
            // LOGGER.debug("Feature checks took {} µs", (System.nanoTime() - start) / 1_000.0);
    }
}
