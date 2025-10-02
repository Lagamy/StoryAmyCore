package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.*;
import net.minecraft.util.math.ChunkPos;
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

@Mixin(NoiseChunkGenerator.class)
public abstract class ChunkChapterFilterMixin {

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
            if (story.area.chunkInBound(chunkX, chunkZ)) {
                for (Chapter chapter : story.chapters.values()) {
                    if (chapter.ringArea.chunkInBound(chunkX, chunkZ)) {
                        Ichunk.addChapter(chapter);
                    }
                }
            }
        }
    }

    //  x = world X, t = world Y, aa = world Z. y = local X, u = local Y, ab = local Z. ;
//    @Inject(
//            method = "populateNoise(Lnet/minecraft/world/gen/chunk/Blender;" +
//                    "Lnet/minecraft/world/gen/StructureAccessor;" +
//                    "Lnet/minecraft/world/gen/noise/NoiseConfig;" +
//                    "Lnet/minecraft/world/chunk/Chunk;II)Lnet/minecraft/world/chunk/Chunk;",
//            at = @At(
//                    value = "INVOKE",
//                    target = "Lnet/minecraft/world/chunk/ChunkSection;setBlockState(IIILnet/minecraft/block/BlockState;Z)Lnet/minecraft/block/BlockState;",
//                    shift = At.Shift.AFTER
//            ),
//            locals = LocalCapture.CAPTURE_FAILEXCEPTION
//    )
//    private void afterBlockPlaced(
//            Blender blender,
//            StructureAccessor structureAccessor,
//            NoiseConfig noiseConfig,
//            Chunk chunk,
//            int minimumCellY,
//            int cellHeight,
//            CallbackInfoReturnable<Chunk> cir,
//            ChunkNoiseSampler chunkNoiseSampler,
//            Heightmap heightmap,
//            Heightmap heightmap2,
//            ChunkPos chunkPos,
//            int i, int j,
//            AquiferSampler aquiferSampler,
//            BlockPos.Mutable mutable,
//            int k, int l, int m, int n,
//            int o, int p, int q, ChunkSection chunkSection,
//            int r, int s, int t, int u, int v,
//            double d,
//            int w, int x, int y, double e,
//            int z, int aa, int ab, double f,
//            BlockState blockState
//    ) {
//        if(Ichunk.getSize() != 0) {
//            int worldX = x;
//            int worldY = t;
//            int worldZ = aa;
////
//            for (Chapter chapter : Ichunk.getPresentChapters()) {
//                if (chapter.chapterArea.inBounds(worldX, worldZ) && worldY >= 64) {
//                    BlockState state = chapter.environmentMap.getOrDefault(XYZasLong(worldX, worldY, worldZ), Blocks.AIR.getDefaultState());
//                    chunkSection.setBlockState(y, u, ab, state, false);
//                }
//            }
//        }
//    }
}
