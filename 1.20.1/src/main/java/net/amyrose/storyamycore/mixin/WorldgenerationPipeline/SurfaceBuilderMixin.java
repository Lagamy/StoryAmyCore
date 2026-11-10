package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.IChunkChapters;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.dimension.DimensionType;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.chunk.BlockColumn;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.amyrose.storyamycore.core.StoryEngine.*;

@Mixin(SurfaceBuilder.class)
public abstract class SurfaceBuilderMixin {

//    @Unique
//    IChunkChapters Ichunk;
//    @Inject(method = "buildSurface", at = @At("HEAD"))
//    private void chunkChapterFilter(NoiseConfig noiseConfig, BiomeAccess biomeAccess, Registry<Biome> biomeRegistry,
//                     boolean useLegacyRandom, HeightContext heightContext, Chunk chunk,
//                     ChunkNoiseSampler chunkNoiseSampler, MaterialRules.MaterialRule materialRule, CallbackInfo ci) {
//        Ichunk = (IChunkChapters) chunk;
//        Ichunk.clearPresentChapters();
//
//        final ChunkPos chunkPos = chunk.getPos();
//        int chunkX = chunkPos.getStartX();
//        int chunkZ = chunkPos.getStartZ();
//
//        for (Story story : StoryEngine.stories.values()) {
//            //if (story.area.chunkInBound(chunkX, chunkZ)) {
//                for (Chapter chapter : story.chapters.values()) {
//                    if (chapter.ringArea.chunkInBound(chunkX, chunkZ)) {
//                        Ichunk.addChapter(chapter);
//                    }
//                }
//            //}
//        }
//    }

    @Shadow @Final
    private BlockState defaultState;

    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void sealCavesInChapters(NoiseConfig noiseConfig, BiomeAccess biomeAccess, Registry<Biome> biomeRegistry,
                                     boolean useLegacyRandom, HeightContext heightContext, Chunk chunk,
                                     ChunkNoiseSampler chunkNoiseSampler, MaterialRules.MaterialRule materialRule,
                                     CallbackInfo ci) {
        IChunkChapters Ichunk = (IChunkChapters) chunk;
        if (Ichunk.getSize() == 0) {
            fillPresentChapters(chunk);
        }
        if (Ichunk.getSize() == 0) {
            return;
        }

        /* Noise caves, cave entrances and aquifer lava/water come from the density function in populateNoise, not from carvers,
           so CarverMixin cannot stop them. This runs at HEAD, before the surface rules, so the filled stone is dressed by vanilla
           (grass, sand, deepslate) and the heightmap read by the ring blending in createChapter is already the sealed surface.
           estimateSurfaceHeight is the cave-free terrain height the aquifer sampler uses; it is quantized, so it can be off by a few blocks.
           Fluids above it (oceans) are left alone. pos is a local, so concurrent worldgen threads do not share it. */
        final ChunkPos chunkPos = chunk.getPos();
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int worldX = chunkPos.getStartX() + x;
                int worldZ = chunkPos.getStartZ() + z;

                boolean inRing = false;
                for (Chapter chapter : Ichunk.getPresentChapters()) {
                    if (chapter.ringArea.inBounds(worldX, worldZ)) {
                        inRing = true;
                        break;
                    }
                }
                if (!inRing) {
                    continue;
                }

                int surfaceY = chunkNoiseSampler.estimateSurfaceHeight(worldX, worldZ);
                if (surfaceY == Integer.MAX_VALUE) {
                    continue;
                }
                int topY = Math.min(surfaceY, chunk.getTopY() - 1);
                for (int y = chunk.getBottomY(); y <= topY; y++) {
                    pos.set(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(pos);
                    if (state.isAir() || !state.getFluidState().isEmpty()) {
                        chunk.setBlockState(pos, defaultState, false);
                    }
                }
            }
        }
    }

    @Inject(method = "buildSurface", at = @At("TAIL"))
    private void createChapter(NoiseConfig noiseConfig, BiomeAccess biomeAccess, Registry<Biome> biomeRegistry,
                                   boolean useLegacyRandom, HeightContext heightContext, Chunk chunk,
                                   ChunkNoiseSampler chunkNoiseSampler, MaterialRules.MaterialRule materialRule,
                                   CallbackInfo ci) {
        StoryEngine.stampChapters(chunk);
    }

}
