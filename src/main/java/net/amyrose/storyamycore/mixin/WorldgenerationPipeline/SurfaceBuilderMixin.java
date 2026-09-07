package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.IChunkChapters;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.block.AirBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
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
        if (Ichunk.getSize() != 0)
        {
            for (Chapter chapter : Ichunk.getPresentChapters())
            {
                for (int x = 0; x < 16; ++x)
                {
                    for (int z = 0; z < 16; ++z) {
                        int worldX = chunkX + x;
                        int worldZ = chunkZ + z;

                        BlockPos topmostBlockpos = new BlockPos(worldX, chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG, x, z), worldZ);
                        BlockState topmostBlockstate = chunk.getBlockState(topmostBlockpos); // For biome aware landscape.
                        int highestY;
                        int landscapeY = chapter.landscapeMap.getOrDefault(XandZAsLong(worldX, worldZ), Integer.MIN_VALUE);
                        //Generate Blending Rings
                        if (landscapeY == Integer.MIN_VALUE) { // That means -> not chapter territory yet
                            int ringLayerBlockHeight = CalculateBlendedHighestBlock(chapter, worldX, worldZ, topmostBlockpos.getY());

                            highestY = Math.max(ringLayerBlockHeight, topmostBlockpos.getY());
                            for (int y = ringLayerBlockHeight - 2; y <= highestY; y++) {
                                BlockPos pos = new BlockPos(worldX, y, worldZ);
                                if (y == ringLayerBlockHeight) {
                                    BlockState state = topmostBlockstate;
                                    if (chapter.forbiddenLandscapeBlocks.contains(topmostBlockstate))
                                    {
                                        state = chapter.landscapeBlockState;
                                    }

                                    chunk.setBlockState(pos, state, false);
                                    chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG).set(x, z, y);
                                    chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG).set(x, z, y);
                                    chunk.getHeightmap(Heightmap.Type.MOTION_BLOCKING).set(x, z, y);
                                }
                                else if(y < ringLayerBlockHeight) {
                                    BlockState state = chapter.landscapeBlockState;
                                    BlockState above = chunk.getBlockState(pos.up());
                                    if (state.equals(Blocks.GRASS_BLOCK.getDefaultState()) && !above.isAir())
                                    {
                                        state = Blocks.DIRT.getDefaultState();
                                    }

                                    chunk.setBlockState(pos, state, false);
                                }
                                else if (ringLayerBlockHeight != Integer.MIN_VALUE && y > ringLayerBlockHeight && chunk.getBlockState(pos).getFluidState().isEmpty()) {
                                    chunk.setBlockState(pos, Blocks.AIR.getDefaultState(), false);
                                }
                            }
                        } else {
                            //Generate Landscape

                            highestY = Math.max(chapter.environmentHighestPoint, topmostBlockpos.getY());
                            for(int y = chapter.chaptersBaseHeight; y <= highestY; y++) {
                                BlockPos pos = new BlockPos(worldX, y, worldZ);
                                if (y == landscapeY) {
                                    BlockState state = topmostBlockstate;
                                    if (chapter.forbiddenLandscapeBlocks.contains(topmostBlockstate))
                                    {
                                        state = chapter.landscapeBlockState;
                                    }

                                    chunk.setBlockState(pos, state, false);
                                    chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG).set(x, z, y);
                                    chunk.getHeightmap(Heightmap.Type.OCEAN_FLOOR_WG).set(x, z, y);
                                    chunk.getHeightmap(Heightmap.Type.MOTION_BLOCKING).set(x, z, y);
                                }
                                else if (y == landscapeY - 1)
                                {
                                    chunk.setBlockState(pos, Blocks.DIRT.getDefaultState(), false); // temporary
                                }
//                            else if(y > chapter.chaptersBaseHeight && y < landscapeY) {
//                                chunk.setBlockState(pos, Blocks.STONE.getDefaultState(), false);
//                            }
                                else if (landscapeY != Integer.MIN_VALUE && y > landscapeY) {
                                    chunk.setBlockState(pos, Blocks.AIR.getDefaultState(), false);
                                }

                                // Generate Environment
                                BlockState state = chapter.environmentMap.getOrDefault(XYZasLong(worldX, y, worldZ), Blocks.AIR.getDefaultState());
                                //LOGGER.info("name: {}, block: {}",story.name, state);

                                if (state != Blocks.AIR.getDefaultState()) {
                                    //System.out.println("maxHeight: " + chapter.maxHeight);
                                    chunk.setBlockState(pos, state, false);
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