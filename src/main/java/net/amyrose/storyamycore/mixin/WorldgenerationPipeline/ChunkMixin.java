package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.*;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static com.mojang.text2speech.Narrator.LOGGER;

@Mixin(Chunk.class)
public abstract class ChunkMixin implements IChunkChapters {
    @Unique
    public List<Chapter> presentChapters = new ArrayList<>(); // collection of references.

    @Override
    public List<Chapter> getPresentChapters() { return presentChapters; }

    @Override
    public void clearPresentChapters() { presentChapters.clear(); }

    @Override
    public void addChapter(Chapter chapter){ presentChapters.add(chapter); }

    @Override
    public boolean isChaptersPresentEmpty(){ return presentChapters.isEmpty(); }

    @Override
    public int getSize() { return presentChapters.size();}
}