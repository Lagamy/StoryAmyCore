package net.amyrose.storyamycore.mixin.worldgeneration;

import net.amyrose.storyamycore.core.BlockPos2D;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.ChapterEngine;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.*;

@Mixin(NoiseChunkGenerator.class)
public abstract class NoiseGeneratorMixin {

//    @Inject(method = "getHeight", at = @At("RETURN"))
//    private void getHeight(int x, int z, Heightmap.Type heightmap, HeightLimitView world, NoiseConfig noiseConfig, CallbackInfoReturnable<Integer> cir) {
//        for (Chapter chapter : ChapterEngine.chapters) {
//            if (x >= chapter.beginning.x && x <= chapter.end.x && z >= chapter.beginning.z && z <= chapter.end.z) {
//                int y = cir.getReturnValue();
//                System.out.println("x: " + x + " y: " + y + " z: " + z);
//            }
//        }
//    }
    @Inject(
            method = "populateNoise (Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;II)Lnet/minecraft/world/chunk/Chunk;",
            at = @At("RETURN"),
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

        final ChunkPos chunkPos = cir.getReturnValue().getPos();
        int chunkX = chunkPos.getStartX();
        int chunkZ = chunkPos.getStartZ();
        for (Chapter chapter : ChapterEngine.chapters) {
            if (chapter.ringArea.chunkInBound(chunkX, chunkZ)) {
                for (int x = 0; x < 16; ++x) {
                    for (int z = 0; z < 16; ++z) {

                        int X = chunkX + x;
                        int Z = chunkZ + z;
                        if (!chapter.chapterArea.inBounds(X, Z) && chapter.ringArea.inBounds(X, Z)) {
                            int blockHeight = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE_WG, x, z);
                            int ringLayer = chapter.CalculateWhatRingLayer(X, Z);


//                        for(int layer = 1; layer <= ChapterEngine.ringRadius; layer++ ) {
//
//
//                            if (((X == chapter.chapterArea.beginning.x - layer || X == chapter.chapterArea.end.x + layer) && Z >= chapter.chapterArea.beginning.z - layer && Z <= chapter.chapterArea.end.z + layer) ||
//                                    ((Z == chapter.chapterArea.beginning.z - layer || Z == chapter.chapterArea.end.z + layer) && X >= chapter.chapterArea.beginning.x - layer && X <= chapter.chapterArea.end.x + layer)) {
//
//                            }
//
//                        }
                        }
                    }
                }
            }
        }
    }
}
