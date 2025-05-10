package net.amyrose.storyamycore.mixin.worldgeneration;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.ChapterEngine;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkNoiseSampler.class)
public class NoiseSamplerMixin {
    // Define flat region coordinates
    private static final int FLAT_Y = 64;    // Flat surface height

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
            if (x >= chapter.beginning.x && x <= chapter.end.x && z >= chapter.beginning.z && z <= chapter.end.z) {
                if (y <= 50) {
                    // Below or at Y=50: Preserve air (caves), otherwise use stone
                    cir.setReturnValue(originalState != null && originalState.isAir() ? originalState : Blocks.STONE.getDefaultState());
                } else if (y > FLAT_Y) {
                    // Above Y=64: Always air
                    cir.setReturnValue(Blocks.AIR.getDefaultState());
                } else {
                    // At Y=64: Stone
                    cir.setReturnValue(Blocks.STONE.getDefaultState());
                }
                return; // Exit early if in a Chapter region
            }
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
            if (blockX >= chapter.beginning.x && blockX <= chapter.end.x && blockZ >= chapter.beginning.z && blockZ <= chapter.beginning.z) {
                cir.setReturnValue(FLAT_Y); // Force surface height to Y=64
            }
        }
    }
}