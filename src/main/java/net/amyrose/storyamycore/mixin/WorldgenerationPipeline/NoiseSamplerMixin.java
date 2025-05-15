package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.ChapterEngine;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.amyrose.storyamycore.core.ChapterEngine.XandZAsLong;

@Mixin(ChunkNoiseSampler.class)
public class NoiseSamplerMixin {
    // Define flat region coordinates

    @Inject(
            method = "sampleBlockState()Lnet/minecraft/block/BlockState;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void modifyBlockState(CallbackInfoReturnable<BlockState> cir) {
        BlockState originalState = cir.getReturnValue();
        ChunkNoiseSampler sampler = (ChunkNoiseSampler) (Object) this;
        int x = sampler.blockX();
        int y = sampler.blockY();
        int z = sampler.blockZ();
        // Apply existing Chapter logic for flat regions
        for (Chapter chapter : ChapterEngine.chapters) {
            if (chapter.chapterArea.inBounds(x, z)) {
                int MapedY = chapter.landscapeMap.getOrDefault(XandZAsLong(x, z), Integer.MIN_VALUE);
                if (MapedY != Integer.MIN_VALUE) {
                    if (y <= 50) {
                        // Below or at Y=50: Preserve air (caves), otherwise use stone
                        cir.setReturnValue(originalState != null && originalState.isAir() ? originalState : Blocks.STONE.getDefaultState());
                    } else if (y > MapedY + 64) {
                        // Above Y=64: Always air
                        cir.setReturnValue(Blocks.AIR.getDefaultState());
                    } else {
                        // At Y=64: Stone
                        cir.setReturnValue(Blocks.STONE.getDefaultState());
                    }
                    return; // Exit early if in a Chapter region
                }
            }
//            else
//            {
//                int ringLayer = chapter.CalculateWhatRingLayer(x, z);
//                if (y > FLAT_Y - ringLayer) {
//                    // Above Y=64: Always air
//                    cir.setReturnValue(Blocks.AIR.getDefaultState());
//                } else {
//                    // At Y=64: Stone
//                    cir.setReturnValue(Blocks.STONE.getDefaultState());
//                }
//
//            }
        }
    }




    @Inject(
            method = "estimateSurfaceHeight(II)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void modifySurfaceHeight(int blockX, int blockZ, CallbackInfoReturnable<Integer> cir) {
        for (Chapter chapter : ChapterEngine.chapters) {
            // Check if within flat region
            if (chapter.chapterArea.inBounds(blockX, blockZ)) {
                int MapedY = chapter.landscapeMap.getOrDefault(XandZAsLong(blockX, blockZ), Integer.MIN_VALUE);
                if(MapedY != Integer.MIN_VALUE) {
                cir.setReturnValue(MapedY);
                }
            }
        }
    }
}