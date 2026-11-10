package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.FlatChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.EnumSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Mixin(FlatChunkGenerator.class)
public class FlatChunkGeneratorMixin {

    /* The flat generator never runs SurfaceBuilder, so the chapters are stamped here, once its layers are in the chunk. The stamp is chained onto the
       returned future so it runs whenever the layers are really done, on the worldgen thread of that chunk, with only local state.
       The chunk's status is still the previous one while the NOISE step runs, so setBlockState does not track the heightmaps: they are
       rebuilt from the blocks afterwards (the sets of ChunkStatus.NOISE), or the features and the surface step would read the flat ground. */
    @Inject(method = "populateNoise", at = @At("RETURN"), cancellable = true)
    private void stampChapters(Executor executor, Blender blender, NoiseConfig noiseConfig, StructureAccessor structureAccessor, Chunk chunk, CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        cir.setReturnValue(cir.getReturnValue().thenApply(generated -> {
            StoryEngine.fillPresentChapters(generated);
            StoryEngine.stampChapters(generated);
            Heightmap.populateHeightmaps(generated, EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG, Heightmap.Type.WORLD_SURFACE_WG));
            return generated;
        }));
    }
}
