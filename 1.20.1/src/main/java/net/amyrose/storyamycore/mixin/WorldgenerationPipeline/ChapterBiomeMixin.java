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

@Mixin(MultiNoiseBiomeSource.class)
public class ChapterBiomeMixin {

    /* getBiome takes quart coordinates (block >> 2) and runs on worldgen threads. It only reads stories and finished chapter fields
       (area and biome are not modified after placement / server start), so it needs no locking. */
    @Inject(method = "getBiome", at = @At("HEAD"), cancellable = true)
    private void overrideChapterBiome(int x, int y, int z, MultiNoiseUtil.MultiNoiseSampler noise, CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
        int blockX = x << 2;
        int blockZ = z << 2;
        for (Story story : StoryEngine.stories.values()) {
            for (Chapter chapter : story.chapters.values()) {
                if (chapter.biome != null && chapter.isPlaced() && chapter.chapterArea.inBounds(blockX, blockZ)) {
                    cir.setReturnValue(chapter.biome);
                    return;
                }
            }
        }
    }
}
