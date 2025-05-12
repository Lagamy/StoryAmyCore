package net.amyrose.storyamycore.core;

import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;

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
            chapterArea.beginning.add(offset);
            chapterArea.end.add(offset);
        }
        this.biomeName = biomeName;
        ringArea = new Zone(
                new BlockPos2D(chapterArea.beginning.x - ChapterEngine.ringRadius, chapterArea.beginning.z - ChapterEngine.ringRadius),
                new BlockPos2D(chapterArea.end.x + ChapterEngine.ringRadius, chapterArea.end.z + ChapterEngine.ringRadius)
        );
    }

    public int CalculateWhatRingLayer(int x, int z)
    {
        int xOffestFromСhapter = 0;

        if (x < chapterArea.beginning.x)
            xOffestFromСhapter = chapterArea.beginning.x - x;
        else if (x > chapterArea.end.x)
            xOffestFromСhapter = x - chapterArea.end.x;

        int zOffestFromСhapter = 0;
        if (z < chapterArea.beginning.z)
            zOffestFromСhapter = chapterArea.beginning.z - z;
        else if (z > chapterArea.end.z)
            zOffestFromСhapter = z - chapterArea.end.z;


        return Math.max(xOffestFromСhapter, zOffestFromСhapter);
    }
}
