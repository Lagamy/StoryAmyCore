package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.FixedBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FixedBiomeSource.class)
public class FixedBiomeSourceMixin {

    /* Flat worlds use a FixedBiomeSource, which ChapterBiomeMixin (MultiNoiseBiomeSource) never sees. Same rule: quart coordinates, worldgen threads,
       read-only access to finished chapter fields. */
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
