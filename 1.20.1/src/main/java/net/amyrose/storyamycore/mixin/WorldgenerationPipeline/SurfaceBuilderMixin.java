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
        final ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();
        // Iterate over the 16x16 chunk
        IChunkChapters Ichunk = (IChunkChapters) chunk;
        /* The chapter list is not saved in chunk NBT: a ProtoChunk reloaded after NOISE arrives here empty, so it is rebuilt from the chunk position.
           A chunk that really has no chapters just repeats a cheap bounds check once. */
        if (Ichunk.getSize() == 0) {
            fillPresentChapters(chunk);
        }
        if (Ichunk.getSize() != 0)
        {
            /* One mutable position for the whole chunk: it is a local (not a field), so concurrent worldgen threads each have their own. */
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (Chapter chapter : Ichunk.getPresentChapters())
            {
                /* Below the surface block the landscape block is used, but grass becomes dirt so a grass block never sits under another block. */
                BlockState belowSurfaceState = chapter.landscapeBlockState;
                if (belowSurfaceState.equals(Blocks.GRASS_BLOCK.getDefaultState()))
                {
                    belowSurfaceState = Blocks.DIRT.getDefaultState();
                }

                for (int x = 0; x < 16; ++x)
                {
                    for (int z = 0; z < 16; ++z) {
                        int worldX = chunkX + x;
                        int worldZ = chunkZ + z;

                        BlockPos topmostBlockpos = new BlockPos(worldX, chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG, x, z), worldZ);
                        BlockState topmostBlockstate = chunk.getBlockState(topmostBlockpos); // For biome aware landscape.
                        int highestY;
                        long columnKey = XandZAsLong(worldX, worldZ);
                        int landscapeY = chapter.landscapeMap.getOrDefault(columnKey, Integer.MIN_VALUE);
                        /* When several chapters overlap this chunk, the first one whose landscape contains the column owns it.
                           A landscape is stamped only by its owner, and a ring only where no landscape owns the column,
                           so overlapping chapters do not overwrite each other's landscape. */
                        if (Ichunk.getSize() > 1) {
                            Chapter owner = findLandscapeOwner(Ichunk.getPresentChapters(), columnKey);
                            if (landscapeY == Integer.MIN_VALUE ? owner != null : owner != chapter) {
                                continue;
                            }
                        }
                        /* Heightmaps are not written here: ProtoChunk.setBlockState already calls Heightmap.trackUpdate for the
                           heightmaps of the chunk's current status. During SURFACE that status is NOISE, whose set is OCEAN_FLOOR_WG
                           and WORLD_SURFACE_WG, and trackUpdate stores top block + 1 (what sampleHeightmap expects) and rescans
                           downward when the old top becomes air. MOTION_BLOCKING is rebuilt by ChunkStatus.FEATURES. A manual set(x, z, y)
                           would overwrite the correct value with one that is 1 too low.
                           Fluids differ on purpose: the ring keeps fluids above its surface so oceans still lap against the blended terrain,
                           while the landscape clears everything above its surface so water does not flood the set piece. */
                        //Generate Blending Rings
                        if (landscapeY == Integer.MIN_VALUE) { // That means -> not chapter territory yet
                            int ringLayerBlockHeight = CalculateBlendedHighestBlock(chapter, worldX, worldZ, topmostBlockpos.getY());

                            /* CalculateBlendedHighestBlock returns Integer.MIN_VALUE for columns outside the ring (e.g. the corners of ringArea).
                               Subtracting from it would overflow, so those columns are skipped explicitly. */
                            if (ringLayerBlockHeight != Integer.MIN_VALUE) {
                                highestY = Math.max(ringLayerBlockHeight, topmostBlockpos.getY());
                                int startY = Math.max(chunk.getBottomY(), Math.min(ringLayerBlockHeight - 2, topmostBlockpos.getY()));
                                for (int y = startY; y <= highestY; y++) {
                                    pos.set(worldX, y, worldZ);
                                    if (y == ringLayerBlockHeight) {
                                        BlockState state = topmostBlockstate;
                                        if (chapter.forbiddenLandscapeBlocks.contains(topmostBlockstate))
                                        {
                                            state = chapter.landscapeBlockState;
                                        }

                                        chunk.setBlockState(pos, state, false);
                                    }
                                    else if(y < ringLayerBlockHeight) {
                                        chunk.setBlockState(pos, belowSurfaceState, false);
                                    }
                                    else if (chunk.getBlockState(pos).getFluidState().isEmpty()) {
                                        chunk.setBlockState(pos, Blocks.AIR.getDefaultState(), false);
                                    }
                                }
                            }
                        } else {
                            //Generate Landscape

                            highestY = Math.min(chunk.getTopY() - 1, Math.max(Math.max(chapter.environmentHighestPoint, chapter.landscapeHighestPoint), topmostBlockpos.getY()));
                            int startY = Math.max(chunk.getBottomY(), Math.min(chapter.chaptersBaseHeight, topmostBlockpos.getY()));
                            for(int y = startY; y <= highestY; y++) {
                                pos.set(worldX, y, worldZ);
                                if (y == landscapeY) {
                                    BlockState state = topmostBlockstate;
                                    if (chapter.forbiddenLandscapeBlocks.contains(topmostBlockstate))
                                    {
                                        state = chapter.landscapeBlockState;
                                    }

                                    chunk.setBlockState(pos, state, false);
                                }
                                else if (y < landscapeY) {
                                    chunk.setBlockState(pos, belowSurfaceState, false);
                                }
                                else {
                                    chunk.setBlockState(pos, Blocks.AIR.getDefaultState(), false);
                                }

                                // Generate Environment
                                long environmentKey = XYZasLong(worldX, y, worldZ);
                                BlockState state = chapter.environmentMap.getOrDefault(environmentKey, Blocks.AIR.getDefaultState());
                                //LOGGER.info("name: {}, block: {}",story.name, state);

                                if (state != Blocks.AIR.getDefaultState()) {
                                    //System.out.println("maxHeight: " + chapter.maxHeight);
                                    chunk.setBlockState(pos, state, false);

                                    /* Beds and signs are drawn by block entity renderers, so without a BlockEntity they are invisible.
                                       setBlockState only stores the state, so rebuild the block entity from the template NBT
                                       (with its position moved to the world position). The NBT maps are read-only after populate, so this is safe off the main thread.
                                       The position is copied with toImmutable() because the BlockEntity keeps it and pos is reused for the next y. */
                                    NbtCompound savedNbt = chapter.environmentBlockEntityNbt.get(environmentKey);
                                    if (state.hasBlockEntity() && savedNbt != null) {
                                        NbtCompound blockEntityNbt = savedNbt.copy();
                                        blockEntityNbt.putInt("x", worldX);
                                        blockEntityNbt.putInt("y", y);
                                        blockEntityNbt.putInt("z", worldZ);
                                        BlockEntity blockEntity = BlockEntity.createFromNbt(pos.toImmutable(), state, blockEntityNbt);
                                        if (blockEntity != null) {
                                            chunk.setBlockEntity(blockEntity);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            chunk.setNeedsSaving(true);
        }
    }

    @Unique
    Chapter findLandscapeOwner(List<Chapter> chapters, long columnKey) {
        for (Chapter candidate : chapters) {
            if (candidate.landscapeMap.containsKey(columnKey)) {
                return candidate;
            }
        }
        return null;
    }

    @Unique
    int CalculateBlendedHighestBlock(Chapter chapter, int worldX, int worldZ, int highestBlock){
        int ringLayer = chapter.WhatRingLayerIsIt(worldX, worldZ);
        if(ringLayer != Integer.MIN_VALUE) { // Is ring layer
            int BorderY = chapter.GetBorderYPosition(worldX, worldZ);

            // Normalize how deep into the ring this block is: 0.0 (closest to chapter) → 1.0 (outermost ring edge)
            float normalized = (float) ringLayer / StoryEngine.ringRadius;

            // Interpolate between flatY and vanilla height using the normalized value
            // At center of ring (normalized = 0) → flatY, at edge (normalized = 1) → currentHeight
            float blended = BorderY * (1 - normalized) + highestBlock * normalized;
            return Math.round(blended);
        }
        return Integer.MIN_VALUE;
    }

}