package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.biome.Biome;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.amyrose.storyamycore.core.ChapterEngine.*;

public class Chapter {
    public String modId;
    public BlockPos2D beginningTemp;
    public String name;
    public Zone chapterArea;
    public Zone ringArea;
    public String biomeName;
    public String landscapeName;
    public String environmentPath;

    public Long2IntOpenHashMap landscape = new Long2IntOpenHashMap();
    public Long2IntOpenHashMap ringLayers = new Long2IntOpenHashMap();
    public Long2IntOpenHashMap landscapeBorder = new Long2IntOpenHashMap();
    public Long2ObjectOpenHashMap<BlockState> environment = new Long2ObjectOpenHashMap<>();


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


    public Chapter(String name, String modId, BlockPos2D beginning, String landscapeName, String environmentPath, String biomeName, BlockPos2D offset) {
        this.name = name;
        this.beginningTemp = beginning;
        if (!ChapterEngine.chapters.isEmpty()) {
            chapterArea.beginning.add(offset);
            chapterArea.end.add(offset);
        }
        this.landscapeName = landscapeName;
        this.environmentPath = environmentPath;
        this.biomeName = biomeName;
        this.modId = modId;
    }

    public int WhatRingLayerIsIt(int x, int z) {
        int encodedValue = ringLayers.getOrDefault(XandZAsLong(x, z), Integer.MIN_VALUE);
        return encodedValue == Integer.MIN_VALUE ? encodedValue : RingLayerFromInt(encodedValue);
    }

    public int GetBorderYPosition(int x, int z){
        int encodedValue = ringLayers.getOrDefault(XandZAsLong(x, z), Integer.MIN_VALUE);
        return encodedValue == Integer.MIN_VALUE ? encodedValue : BorderYFromInt(encodedValue);
    }
//    public boolean isBorderBlock(int x, int z) {
//        long key = XandZAsLong(x, z);
//        return landscapeBorder.contains(key);
//    }


    // Populate landscape map with absolute world coordinates for the highest non-air block in each column
    public void populateLandscapeMap(StructureTemplate landscapeTemplate) {
        if (landscapeTemplate == null) {
            LOGGER.error("Cannot populate landscape map: landscapeTemplate is null for chapter {}", name);
            throw new RuntimeException("landscapeTemplate is null for chapter " + name); // Crash the game
        }
        Vec3i size = landscapeTemplate.getSize();
        chapterArea = new Zone(beginningTemp, new BlockPos2D(beginningTemp.x + size.getX(), beginningTemp.z + size.getZ()));

        ringArea = new Zone(
                new BlockPos2D(chapterArea.beginning.x - ChapterEngine.ringRadius, chapterArea.beginning.z - ChapterEngine.ringRadius),
                new BlockPos2D(chapterArea.end.x + ChapterEngine.ringRadius, chapterArea.end.z + ChapterEngine.ringRadius)
        );

        // Placement origin (use chapterArea.beginning as beginningTemp, adjust if you have a specific field)
        BlockPos origin = new BlockPos(chapterArea.beginning.x, 64, chapterArea.beginning.z); // y=64 for flat terrain

        // Default placement settings (no rotation/mirror, adjust as needed)
        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(BlockRotation.NONE)
                .setPosition(origin);

        // Get transformed block infos
        List<StructureTemplate.StructureBlockInfo> blockInfos = placementData.getRandomBlockInfos(landscapeTemplate.blockInfoLists, origin).getAll();

        // Iterate over blocks, filter non-air, and store only the highest y for each (x, z) column
        for (StructureTemplate.StructureBlockInfo info : blockInfos) {
            if (!info.state().isAir()) {
                BlockPos worldPos = info.pos(); // Absolute world position
                long key = XandZAsLong(worldPos.getX(), worldPos.getZ());
                int y = worldPos.getY();

                // Update the y-value only if it's higher than the current stored value (or if no value exists)
                if (!landscape.containsKey(key) || y > landscape.get(key)) {
                    landscape.put(key, y);
                }
            }
        }

        LOGGER.info("Populated landscape map with {} columns for chapter {}", landscape.size(), name);
        LOGGER.info(landscape.toString());
        computeLandscapeBorder();
        computeRingLayersFromLandscape();

    }

    public void computeLandscapeBorder() {
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        for (long key : landscape.keySet()) {
            int x = LongToX(key);
            int z = LongToZ(key);

            boolean isEdge = false;
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int nz = z + dz[i];
                long neighborKey = XandZAsLong(nx, nz);

                if (!landscape.containsKey(neighborKey)) {
                    landscapeBorder.put(key, landscape.get(key));
                    break;
                }
            }
        }
    }

    record RingStep(int x, int z, int borderY){
    }

    public void computeRingLayersFromLandscape() {
        Queue<RingStep> queue = new ArrayDeque<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        ringLayers = new Long2IntOpenHashMap();

        // Initialize ring layer 1 from landscape border
        for (long key : landscapeBorder.keySet()) {
            int x = LongToX(key);
            int z = LongToZ(key);
            int borderY = landscapeBorder.get(key); // Get the Y-value
            long packedKey = XandZAsLong(x, z);

            queue.add(new RingStep(x, z, borderY));
            ringLayers.put(packedKey, RingLayerandYAsInt(1, borderY)); // Ring layer 1
            visited.add(packedKey);
        }

        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            RingStep current = queue.poll();
            int currentLayer = RingLayerFromInt(ringLayers.get(XandZAsLong(current.x, current.z)));

            if (currentLayer >= ChapterEngine.ringRadius) continue;

            for (int i = 0; i < 4; i++) {
                int nx = current.x + dx[i];
                int nz = current.z + dz[i];

                if (!ringArea.inBounds(nx, nz)) continue;

                long key = XandZAsLong(nx, nz);
                if (visited.contains(key) || landscape.containsKey(key)) continue;

                ringLayers.put(key, RingLayerandYAsInt(currentLayer + 1, current.borderY));
                visited.add(key);
                queue.add(new RingStep(nx, nz, current.borderY));
            }
        }

        LOGGER.info("Calculated ring layers: {}", ringLayers.size());
    }



    public int SquareWhatRingLayer(int x, int z)
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
    // Method to load a StructureTemplate from a path
    public StructureTemplate loadStructureTemplate(MinecraftServer server, String structureName) {
        // Get the StructureTemplateManager
        StructureTemplateManager templateManager = server.getStructureTemplateManager();
        Identifier structureId = new Identifier(modId, structureName);

        // Load the template
        return templateManager.getTemplate(structureId)
                .orElseThrow(() -> new IllegalArgumentException("Structure not found: " + structureId));
    }


}
