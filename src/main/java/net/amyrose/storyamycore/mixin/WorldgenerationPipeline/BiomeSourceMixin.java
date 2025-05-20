package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.amyrose.storyamycore.core.StoryEngine.XandZAsLong;

@Mixin(MultiNoiseBiomeSource.class)
public class BiomeSourceMixin {

    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void overrideBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
        int blockX = x * 4;
        int blockZ = z * 4;
        for(Story story : StoryEngine.stories) {
            if(story.area.chunkInBound(x, z)) {
                for (Chapter chapter : story.chapters.values()) {
                    if(chapter.chapterArea.chunkInBound(x, z)) {
                        if (chapter.biome != null) {
                            //System.out.println("Thread: " + Thread.currentThread().getName());
                            if (chapter.landscapeMap.containsKey(XandZAsLong(blockX, blockZ))) {
                                //System.out.println("Overriding biome to desert at blockX: " + blockX + ", blockZ: " + blockZ + ", Thread: " + Thread.currentThread().getName());
                                cir.setReturnValue(chapter.biome);
                            }
                        }
                    }
                }
            }
        }
    }
}