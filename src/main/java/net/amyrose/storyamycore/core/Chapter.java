package net.amyrose.storyamycore.core;

import net.minecraft.block.Blocks;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;

import java.util.*;

import static net.amyrose.storyamycore.core.ChapterEngine.RingBoundKey.*;

public class Chapter {
    public String name;
    public Zone chapterArea;
    public Zone ringArea;
    public String biomeName;
    public RegistryEntry<Biome> biome;


    public Chapter(String name, Zone chapterArea, String biomeName, BlockPos2D offset) {
        this.name = name;
        this.chapterArea = chapterArea;
        if (!ChapterEngine.chapters.isEmpty()) {
            BlockPos2D lastEnd = ChapterEngine.chapters.get(ChapterEngine.chapters.size() - 1).chapterArea.end;
            chapterArea.beginning.add(lastEnd);
            chapterArea.end.add(lastEnd);
            chapterArea.beginning.add(offset);
            chapterArea.end.add(offset);
        }
        this.biomeName = biomeName;
        ringArea = new Zone(
                new BlockPos2D(chapterArea.beginning.x - ChapterEngine.ringRadius, chapterArea.beginning.z - ChapterEngine.ringRadius),
                new BlockPos2D(chapterArea.end.x + ChapterEngine.ringRadius, chapterArea.end.z + ChapterEngine.ringRadius)
        );
    }
}
