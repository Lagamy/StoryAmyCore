package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.IChunkChapters;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.carver.Carver;
import net.minecraft.world.gen.carver.CarverConfig;
import net.minecraft.world.gen.carver.CarverContext;
import net.minecraft.world.gen.carver.CarvingMask;
import net.minecraft.world.gen.chunk.AquiferSampler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

@Mixin(Carver.class)
public class CarverMixin {
    @Inject(method = "carveRegion", at = @At("HEAD"), cancellable = true)
    private void preventCaveCarving(CarverContext context, CarverConfig config, Chunk chunk, Function<BlockPos, RegistryEntry<Biome>> posToBiome, AquiferSampler aquiferSampler, double x, double y, double z, double width, double height, CarvingMask mask, Carver.SkipPredicate skipPredicate, CallbackInfoReturnable<Boolean> cir)
    {
        IChunkChapters Ichunk = (IChunkChapters) chunk;
        /* carveRegion runs many times per chunk, so it only reads the chapter list.
           ChunkChapterFilterMixin rebuilds the list once per chunk at the start of NoiseChunkGenerator.carve. */
        if(Ichunk.getSize() != 0) {
            cir.setReturnValue(false);
        }
    }
}