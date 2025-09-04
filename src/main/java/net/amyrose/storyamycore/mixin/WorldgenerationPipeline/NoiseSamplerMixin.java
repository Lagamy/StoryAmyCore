package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import static net.amyrose.storyamycore.core.StoryEngine.XandZAsLong;

@Mixin(ChunkNoiseSampler.class)
public class NoiseSamplerMixin {
    // Define flat region coordinates
    @Unique
    public IChunkChapters Ichunk; // not static, per-instance

    @Inject(
            method = "create(Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/densityfunction/DensityFunctionTypes$Beardifying;Lnet/minecraft/world/gen/chunk/ChunkGeneratorSettings;Lnet/minecraft/world/gen/chunk/AquiferSampler$FluidLevelSampler;Lnet/minecraft/world/gen/chunk/Blender;)Lnet/minecraft/world/gen/chunk/ChunkNoiseSampler;",
            at = @At("RETURN")
    )
    private static void onCreate(
            Chunk chunk,
            NoiseConfig noiseConfig,
            DensityFunctionTypes.Beardifying beardifying,
            ChunkGeneratorSettings chunkGeneratorSettings,
            AquiferSampler.FluidLevelSampler fluidLevelSampler,
            Blender blender,
            CallbackInfoReturnable<ChunkNoiseSampler> cir
    ) {
        ChunkNoiseSampler sampler = cir.getReturnValue(); // not static, since returned info.
        ((NoiseSamplerMixin)(Object) sampler).Ichunk = (IChunkChapters) chunk;
    }

    @Inject(
            method = "sampleBlockState()Lnet/minecraft/block/BlockState;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void modifyBlockState(CallbackInfoReturnable<BlockState> cir) {
        //LOGGER.info("Noise Sampler");
        BlockState originalState = cir.getReturnValue();
        ChunkNoiseSampler sampler = (ChunkNoiseSampler) (Object) this;
        int x = sampler.blockX();
        int y = sampler.blockY();
        int z = sampler.blockZ();

        // Apply existing Chapter logic for flat regions
        for (Chapter chapter : Ichunk.getPresentChapters()) {
            if (chapter.chapterArea.inBounds(x, z)) {
                int MapedY = chapter.landscapeMap.getOrDefault(XandZAsLong(x, z), Integer.MIN_VALUE);
                if (MapedY != Integer.MIN_VALUE) {
                    if (y <= 50) {
                        // Below or at Y=50: Preserve air (caves), otherwise use stone
                        cir.setReturnValue(originalState != null && originalState.isAir() ? originalState : Blocks.STONE.getDefaultState());
                    } else if (y > MapedY) {
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
    }


    @Inject(
            method = "estimateSurfaceHeight(II)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void modifySurfaceHeight(int blockX, int blockZ, CallbackInfoReturnable<Integer> cir) {

        for (Chapter chapter : Ichunk.getPresentChapters()) {
            if (chapter.chapterArea.inBounds(blockX, blockZ)) {
                int MapedY = chapter.landscapeMap.getOrDefault(XandZAsLong(blockX, blockZ), Integer.MIN_VALUE);
                if (MapedY != Integer.MIN_VALUE) {
                    cir.setReturnValue(MapedY);
                }
            }
        }

    }
}