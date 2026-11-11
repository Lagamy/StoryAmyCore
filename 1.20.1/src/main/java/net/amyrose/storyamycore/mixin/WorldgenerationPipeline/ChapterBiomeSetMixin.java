package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;
import java.util.Set;

@Mixin(BiomeSource.class)
public class ChapterBiomeSetMixin {

    /* ChunkGenerator.generateFeatures keeps only the biomes found in the chunk that are also in biomeSource.getBiomes(), and indexes features
       from that same set. A chapter biome that is not part of the world's biome source (a datapack biome) would therefore get no vegetation or
       other features, so the chapter biomes are added to the set. Chapter biomes are resolved at server start, before any chunk is generated,
       and are read-only afterwards, so this is safe on worldgen threads. */
    @Inject(method = "getBiomes", at = @At("RETURN"), cancellable = true)
    private void addChapterBiomes(CallbackInfoReturnable<Set<RegistryEntry<Biome>>> cir) {
        Set<RegistryEntry<Biome>> biomes = cir.getReturnValue();
        Set<RegistryEntry<Biome>> extended = null;
        for (Story story : StoryEngine.stories.values()) {
            for (Chapter chapter : story.chapters.values()) {
                if (chapter.biome != null && !biomes.contains(chapter.biome)) {
                    if (extended == null) {
                        extended = new HashSet<>(biomes);
                    }
                    extended.add(chapter.biome);
                }
            }
        }
        if (extended != null) {
            cir.setReturnValue(extended);
        }
    }
}
