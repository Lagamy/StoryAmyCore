package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.ChapterEngine;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SurfaceBuilder.class)
public class SurfaceBuilderMixinOld {
    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void modifySurfaceHeight(NoiseConfig noiseConfig, BiomeAccess biomeAccess, Registry<Biome> biomeRegistry,
                                     boolean useLegacyRandom, HeightContext heightContext, Chunk chunk,
                                     ChunkNoiseSampler chunkNoiseSampler, MaterialRules.MaterialRule materialRule,
                                     CallbackInfo ci) {
        final ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();
        // Iterate over the 16x16 chunk
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int worldX = chunkX + x;
                int worldZ = chunkZ + z;
                int currentHeight;
                int chapterHeight = 64;
                // Get the current surface height from the heightmap
                for (Chapter chapter : ChapterEngine.chapters) {
                    //for(int layer = 1; layer <= ringRadius; layer++ ) {
                    boolean isOuterBorder = !chapter.chapterArea.inBounds(worldX, worldZ) && chapter.ringArea.inBounds(worldX, worldZ);

                    if (isOuterBorder) {

                        // Get the vanilla terrain height at this block
                        currentHeight = chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG, x, z);

                        // Calculate which "ring layer" this block is in (distance from chapter edge)
                        int ringLayer = chapter.WhatRingLayerIsIt(worldX, worldZ);

                        // Normalize how deep into the ring this block is: 0.0 (closest to chapter) → 1.0 (outermost ring edge)
                        float normalized = (float) ringLayer / ChapterEngine.ringRadius;

                        // Interpolate between flatY and vanilla height using the normalized value
                        // At center of ring (normalized = 0) → flatY, at edge (normalized = 1) → currentHeight
                        int resultHeight = (int) (chapterHeight * (1 - normalized) + currentHeight * normalized);

                        // Clear all blocks above the resultHeight in this column
                        for (int y = resultHeight + 1; y <= currentHeight; y++) {
                            chunk.setBlockState(new BlockPos(worldX, y, worldZ), Blocks.AIR.getDefaultState(), false);
                        }


                        // Now do something with resultHeight — for testing, maybe set a block at that height
                        BlockPos targetPos = new BlockPos(worldX, resultHeight, worldZ);
                        chunk.setBlockState(targetPos, Blocks.STONE.getDefaultState(), false); // Replace with desired block





                        if(ringLayer == 1) {
                            chunk.setBlockState(new BlockPos(x, currentHeight, z), Blocks.RED_WOOL.getDefaultState(), false);
                        }
                        if(ringLayer == 2)
                        {
                            chunk.setBlockState(new BlockPos(x, currentHeight, z), Blocks.MAGENTA_WOOL.getDefaultState(), false);
                        }
                    }
                    //}
                }
//                if(x == 0 && z == 0)
//                {
//                    currentHeight = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x, z);
//                    chunk.setBlockState(new BlockPos(x, currentHeight, z), Blocks.PINK_WOOL.getDefaultState(), false);
//                }
//
//                if(x == 15 && z == 15){
//                    currentHeight = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x, z);
//                    chunk.setBlockState(new BlockPos(x, currentHeight, z), Blocks.YELLOW_WOOL.getDefaultState(), false);
//                }
            }
        }
    }
}