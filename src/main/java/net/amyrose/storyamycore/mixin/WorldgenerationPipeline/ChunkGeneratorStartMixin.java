package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunkGenerator.class)
public abstract class ChunkGeneratorStartMixin {

    /**
     * Inject at the start of generateChunk, before vanilla terrain is generated.
     * "cancellable = true" if you want to optionally skip vanilla generation.
     */

    @Inject(
            method = "populateNoise (Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;II)Lnet/minecraft/world/chunk/Chunk;",
            at = @At("HEAD"),
            cancellable = false
    )
    private void onPopulateNoise(
            Blender blender,
            StructureAccessor structureAccessor,
            NoiseConfig noiseConfig,
            Chunk chunk,
            int minimumCellY,
            int cellHeight,
            CallbackInfoReturnable<Chunk> cir
    )  {

        // Identify what chapters are in this chunk
        int chunkX = chunk.getPos().getStartX();
        int chunkZ = chunk.getPos().getStartZ();

        IChunkChapters Ichunk = (IChunkChapters) chunk;
        Ichunk.clearPresentChapters(); // Clean it up each new chunk

        for(Story story : StoryEngine.stories.values()) {
            if (story.area.chunkInBound(chunkX, chunkZ)) {
                for (Chapter chapter : story.chapters.values()) {
                    if (chapter.ringArea.chunkInBound(chunkX, chunkZ)) {
                        Ichunk.addChapter(chapter);
                    }
                }
            }
        }

    }
}