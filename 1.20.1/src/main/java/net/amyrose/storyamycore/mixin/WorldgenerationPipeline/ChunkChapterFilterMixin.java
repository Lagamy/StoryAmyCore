package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.GenerationStep;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mixin(NoiseChunkGenerator.class)
public abstract class ChunkChapterFilterMixin  {

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
        /* The chunk generator is shared by all worldgen threads, so the chunk reference must stay a local:
           an instance field would let two chunks being generated at once overwrite each other's reference. */
        StoryEngine.fillPresentChapters(chunk);
    }

    @Inject(method = "carve", at = @At("HEAD"))
    private void refillChaptersForCarving(ChunkRegion chunkRegion, long seed, NoiseConfig noiseConfig, BiomeAccess biomeAccess, StructureAccessor structureAccessor, Chunk chunk, GenerationStep.Carver carverStep, CallbackInfo ci) {
        /* The chapter list is not saved in chunk NBT, so a chunk reloaded between SURFACE and CARVERS has an empty list.
           carve runs once per chunk, unlike Carver.carveRegion which CarverMixin hits many times, so the list is rebuilt here. */
        StoryEngine.fillPresentChapters(chunk);
    }
}

//RegistryKey<World> dimensionKey = chunkGenerator.getWorldKey();
