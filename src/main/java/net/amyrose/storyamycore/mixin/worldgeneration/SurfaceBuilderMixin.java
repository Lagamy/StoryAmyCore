package net.amyrose.storyamycore.mixin.worldgeneration;

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
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.surfacebuilder.MaterialRules;
import net.minecraft.world.gen.surfacebuilder.SurfaceBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SurfaceBuilder.class)
public class SurfaceBuilderMixin {
    @Inject(method = "buildSurface", at = @At("HEAD"))
    private void modifySurfaceHeight(NoiseConfig noiseConfig, BiomeAccess biomeAccess, Registry<Biome> biomeRegistry,
                                     boolean useLegacyRandom, HeightContext heightContext, Chunk chunk,
                                     ChunkNoiseSampler chunkNoiseSampler, MaterialRules.MaterialRule materialRule,
                                     CallbackInfo ci) {
        final int fixedHeight = 64; // Desired surface height
        final int bufferBelow = 5; // Number of blocks below fixedHeight to fill with stone
        final int bufferAbove = 10; // Number of blocks above current height to clear with air

        final ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();

        BlockPos.Mutable pos = new BlockPos.Mutable();

        // Iterate over the 16x16 chunk
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                int worldX = chunkX + x;
                int worldZ = chunkZ + z;

                // Get the current surface height from the heightmap
                int currentHeight = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x, z) + 2;

//                // Define the y-range to modify
//                int minY = Math.max(chunk.getBottomY(), fixedHeight - bufferBelow); // Start a few blocks below fixedHeight
//                int maxY = Math.min(chunk.getTopY(), Math.max(fixedHeight, currentHeight) + bufferAbove); // Clear up to a bit above the highest point
//
//                // Modify blocks in the limited y-range
//                for (int y = minY; y <= maxY; ++y) {
//                    pos.set(worldX, y, worldZ);
//                    if (y > fixedHeight) {
//                        chunk.setBlockState(pos, Blocks.AIR.getDefaultState(), false);
//                    } else {
//                        chunk.setBlockState(pos, Blocks.STONE.getDefaultState(), false);
//                    }
//                }
                chunk.setBlockState(new BlockPos(x, currentHeight, z), Blocks.WATER.getDefaultState(), false);
                // Update the WORLD_SURFACE_WG heightmap to reflect the new surface height
                //chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG).trackUpdate(x, fixedHeight, z, Blocks.STONE.getDefaultState());
            }
        }

        // Do NOT cancel the CallbackInfo, allowing the original buildSurface to apply biome-specific block states
    }
}