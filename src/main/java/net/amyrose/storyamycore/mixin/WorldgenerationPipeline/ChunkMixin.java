package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.IChunkChapters;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.ArrayList;
import java.util.List;

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
}