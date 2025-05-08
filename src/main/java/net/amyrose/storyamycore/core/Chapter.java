package net.amyrose.storyamycore.core;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

public class Chapter {
    String name;
    public BlockPos2D beginning;
    public BlockPos2D end;
    public String biomeName;
    public RegistryEntry<Biome> biome;

    public Chapter(String name, BlockPos2D beginning, BlockPos2D end, String biomeName, BlockPos2D offset){
        this.name = name;
        this.beginning = beginning;
        this.end = end;
        if(!ChapterEngine.chapters.isEmpty())
        {
            BlockPos2D lastEnd = ChapterEngine.chapters.get(ChapterEngine.chapters.size() - 1).end;
            beginning.add(lastEnd);
            end.add(lastEnd);
            beginning.add(offset);
            end.add(offset);
        }
        this.biomeName = biomeName;
    }
}
