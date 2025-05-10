package net.amyrose.storyamycore.mixin.worldgeneration;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.ChapterEngine;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.HeightContext;
import net.minecraft.world.gen.chunk.BlockColumn;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;
import java.util.function.Predicate;

@Mixin(SurfaceBuilder.class)
public class SurfaceBuilderMixin {
    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void modifySurfaceHeight(NoiseConfig noiseConfig, BiomeAccess biomeAccess, Registry<Biome> biomeRegistry,
                                     boolean useLegacyRandom, HeightContext heightContext, Chunk chunk,
                                     ChunkNoiseSampler chunkNoiseSampler, MaterialRules.MaterialRule materialRule,
                                     CallbackInfo ci) {
        System.out.println("BBBBBBBB");
        final ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();
        int ringRadius = 16;

        // Iterate over the 16x16 chunk
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int worldX = chunkX + x;
                int worldZ = chunkZ + z;
                int currentHeight;
                // Get the current surface height from the heightmap
                for (Chapter chapter : ChapterEngine.chapters) {
                    for(int layer = 1; layer <= ringRadius; layer++ ) {
                        boolean isOuterBorder = false;
                        if (((worldX == chapter.beginning.x - layer || worldX == chapter.end.x + layer) && worldZ >= chapter.beginning.z - layer && worldZ <= chapter.end.z + layer) ||
                                ((worldZ == chapter.beginning.z - layer || worldZ == chapter.end.z + layer) && worldX >= chapter.beginning.x - layer && worldX <= chapter.end.x + layer)) {
                            isOuterBorder = true;
                        }

                        if (isOuterBorder) {
                            // Ensure we're within chunk bounds for heightmap sampling
                            currentHeight = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x, z);
                            chunk.setBlockState(new BlockPos(x, currentHeight, z), Blocks.PINK_WOOL.getDefaultState(), false);
                        }
                    }
                    for(BlockPos block : chapter.test){
                        if(worldX == block.getX() && worldZ == block.getZ())
                        {
                            chunk.setBlockState(new BlockPos(x, block.getY(), z), Blocks.GREEN_WOOL.getDefaultState(), false);
                        }
                    }
                }
            }
        }
    }
}