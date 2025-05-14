package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.ChapterEngine;
import net.minecraft.block.StructureBlock;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiNoiseBiomeSource.class)
public class BiomeSourceMixin {

    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void overrideBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
        int blockX = x << 2;
        int blockZ = z << 2;
        for(Chapter chapter : ChapterEngine.chapters) {
            if (chapter.biome != null) {
                //System.out.println("Thread: " + Thread.currentThread().getName());
                if (blockX >= chapter.chapterArea.beginning.x && blockX <= chapter.chapterArea.end.x && blockZ >= chapter.chapterArea.beginning.z && blockZ <= chapter.chapterArea.end.z) {
                    //System.out.println("Overriding biome to desert at blockX: " + blockX + ", blockZ: " + blockZ + ", Thread: " + Thread.currentThread().getName());
                    cir.setReturnValue(chapter.biome);
                }
            }
        }
    }
}