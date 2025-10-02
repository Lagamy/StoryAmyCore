package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.IChunkChapters;
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
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.amyrose.storyamycore.core.StoryEngine.XYZasLong;
import static net.amyrose.storyamycore.core.StoryEngine.XandZAsLong;

@Mixin(SurfaceBuilder.class)
public abstract class SurfaceBuilderMixin {


    // Place everything here:
    @Inject(method = "buildSurface", at = @At("TAIL"))
    private void createEnvironment(NoiseConfig noiseConfig, BiomeAccess biomeAccess, Registry<Biome> biomeRegistry,
                                   boolean useLegacyRandom, HeightContext heightContext, Chunk chunk,
                                   ChunkNoiseSampler chunkNoiseSampler, MaterialRules.MaterialRule materialRule,
                                   CallbackInfo ci) {
        final ChunkPos chunkPos = chunk.getPos();
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();
        // Iterate over the 16x16 chunk
        IChunkChapters Ichunk = (IChunkChapters) chunk;
        Heightmap heightmap = chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG);
        if (Ichunk.getSize() != 0)
        {
            for (Chapter chapter : Ichunk.getPresentChapters())
            {
                for (int x = 0; x < 16; ++x)
                {
                    for (int z = 0; z < 16; ++z)
                    {
                        int worldX = chunkX + x;
                        int worldZ = chunkZ + z;

                        BlockPos topmostBlockpos = new BlockPos(worldX, chunk.sampleHeightmap(Heightmap.Type.OCEAN_FLOOR_WG, worldX, worldZ), worldZ);
                        BlockState topmostBlockstate = chunk.getBlockState(topmostBlockpos); // For biome aware landscape.

                        for (int y = 64; y <= chapter.maxEnvironmentHeight; y++)
                        {
                            BlockPos pos = new BlockPos(worldX, y, worldZ);

                            int landscapeY = chapter.landscapeMap.getOrDefault(XandZAsLong(worldX, worldZ), Integer.MIN_VALUE);
                            if (y == landscapeY)
                            {
                                chunk.setBlockState(pos, topmostBlockstate, false);
                                chunk.getHeightmap(Heightmap.Type.WORLD_SURFACE_WG).set(x, z, y);
                                chunk.getHeightmap(Heightmap.Type.MOTION_BLOCKING).set(x, z, y);
                                chunk.setNeedsSaving(true);
                            }
                            else if(y > landscapeY)
                            {
                                chunk.setBlockState(pos, Blocks.AIR.getDefaultState(), false);
                            }

                            BlockState state = chapter.environmentMap.getOrDefault(XYZasLong(worldX, y, worldZ), Blocks.AIR.getDefaultState());
                            //LOGGER.info("name: {}, block: {}",story.name, state);

                            if (state != Blocks.AIR.getDefaultState())
                            {
                                //System.out.println("maxHeight: " + chapter.maxHeight);
                                chunk.setBlockState(pos, state, false);
                            }
                        }
                    }
                }
            }
        }
    }
}