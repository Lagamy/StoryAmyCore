package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mixin(NoiseChunkGenerator.class)
public abstract class ChunkChapterFilterMixin  {

    @Unique
    IChunkChapters Ichunk;

    @Inject(
            method = "populateNoise (Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;II)Lnet/minecraft/world/chunk/Chunk;",
            at = @At("HEAD"),
            cancellable = false
    )
    private void filterChaptersForThatChunk(
            Blender blender,
            StructureAccessor structureAccessor,
            NoiseConfig noiseConfig,
            Chunk chunk,
            int minimumCellY,
            int cellHeight,
            CallbackInfoReturnable<Chunk> cir
    )  {
        Ichunk = (IChunkChapters) chunk;
        Ichunk.clearPresentChapters();

        final ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();

        for (Story story : StoryEngine.stories.values()) {
            //if (story.area.chunkInBound(chunkX, chunkZ)) {
                for (Chapter chapter : story.chapters.values()) {
                    if (chapter.ringArea.chunkInBound(chunkX, chunkZ)) {
                        Ichunk.addChapter(chapter);
                    }
                }
           // }
        }
    }
}

//RegistryKey<World> dimensionKey = chunkGenerator.getWorldKey();
