package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;

import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.amyrose.storyamycore.core.StoryEngine.*;

public class Chapter {
    public Story parentStory; // reference to parent story.
    public boolean first = false;
    BlockPos2D offsetFromPrevious;
    BlockPos2D size;
    public String name;
    public Zone chapterArea;
    public Zone ringArea;
    public String biomeName;
    public String landscapeName;
    public String environmentName;
    public StructureTemplate landscapeTemplate;
    public StructureTemplate environmentTemplate;

    public int chaptersBaseHeight = 64;
    public int maxEnvironmentHeight;
    public LongOpenHashSet noFoliageMask = new LongOpenHashSet();
    public Long2IntOpenHashMap landscapeMap = new Long2IntOpenHashMap();
    public Long2IntOpenHashMap landscapeBorder = new Long2IntOpenHashMap();
    public Long2IntOpenHashMap ringLayersMap = new Long2IntOpenHashMap();
    public Long2ObjectOpenHashMap<BlockState> environmentMap = new Long2ObjectOpenHashMap<>();
    public Set<String> forbiddenBiomes;

    public RegistryEntry<Biome> biome;


    public Chapter(BlockPos2D size, String landscapeName, String environmentName, String biomeName, Set<String> forbiddenBiomes, int chaptersBaseHeight) {
        this.landscapeName = landscapeName;
        this.environmentName = environmentName;
        this.biomeName = biomeName;
        this.size = size;
        this.chaptersBaseHeight = chaptersBaseHeight;
        this.forbiddenBiomes = forbiddenBiomes;
        first = true;
    }

    public Chapter(BlockPos2D size, BlockPos2D offsetFromPrevious, String landscapeName, String environmentName, String biomeName, Set<String> forbiddenBiomes, int chaptersBaseHeight) {
        this.landscapeName = landscapeName;
        this.environmentName = environmentName;
        this.biomeName = biomeName;
        this.size = size;
        this.offsetFromPrevious = offsetFromPrevious;
        this.chaptersBaseHeight = chaptersBaseHeight;
        this.forbiddenBiomes = forbiddenBiomes;
    }


    public void initializeChapter(Story story, BiomeAccess biomeAccess)
    {
        parentStory = story; // saves reference to story.
        BlockPos2D beginning;

        if(first) {
            beginning = new BlockPos2D(0, 0);
        }
        else {
            Chapter previousChapter = story.chapters.get(story.chapters.size() - 1);
            beginning = new BlockPos2D(previousChapter.chapterArea.end.x, previousChapter.chapterArea.end.z);
            beginning.add(offsetFromPrevious);
        }

        Zone tempChapterArea = new Zone(new BlockPos2D(beginning.x, beginning.z), new BlockPos2D(beginning.x + size.x, beginning.z + size.z));

        boolean foundPlace = tempChapterArea.IsNotInForbiddenBiome(forbiddenBiomes, chaptersBaseHeight, biomeAccess);

        int attempts = 0;
        int maxAttempts = 100; // to avoid infinite loop
        while (!foundPlace && attempts != maxAttempts)
        {
            int newX = beginning.x + (int)(Math.random() * story.baseMaxDistanceRadius * (attempts + 10) / 10);
            int newZ = beginning.z + (int)(Math.random() * story.baseMaxDistanceRadius * (attempts + 10) / 10);

            tempChapterArea.beginning.x = newX;
            tempChapterArea.beginning.z = newZ;
            tempChapterArea.end.x = newX + size.x;
            tempChapterArea.end.z = newZ + size.z;

            foundPlace = tempChapterArea.IsNotInForbiddenBiome(forbiddenBiomes, chaptersBaseHeight, biomeAccess);
        }

        if (attempts == maxAttempts)
        {
            LOGGER.error("Couldn't find suitable place for chapter after: {} attemps. Please change chapter settings", maxAttempts);
            throw new RuntimeException();
        }

        // if I wanted to search ring wide - I would use this.
        Zone tempRingArea = new Zone(
                new BlockPos2D(tempChapterArea.beginning.x - StoryEngine.ringRadius, tempChapterArea.beginning.z - StoryEngine.ringRadius),
                new BlockPos2D(tempChapterArea.end.x + StoryEngine.ringRadius, tempChapterArea.end.z + StoryEngine.ringRadius)
        );
        chapterArea = tempChapterArea;
        ringArea = tempRingArea;
    }



    public void addStoryCoords() {
        // Shift chapterArea relative to the story’s world position
        BlockPos2D storyBeginningMinusRingRadius = new BlockPos2D(parentStory.area.beginning.x + StoryEngine.ringRadius, parentStory.area.beginning.z + StoryEngine.ringRadius);
        chapterArea.beginning.add(storyBeginningMinusRingRadius);
        chapterArea.end.add(storyBeginningMinusRingRadius);

        // ringArea follows the same world offset
        ringArea.beginning.add(storyBeginningMinusRingRadius);
        ringArea.end.add(storyBeginningMinusRingRadius);
    }

    public int WhatRingLayerIsIt(int x, int z) {
        int encodedValue = ringLayersMap.getOrDefault(XandZAsLong(x, z), Integer.MIN_VALUE);
        return encodedValue == Integer.MIN_VALUE ? encodedValue : RingLayerFromInt(encodedValue);
    }

    public int GetBorderYPosition(int x, int z){
        int encodedValue = ringLayersMap.getOrDefault(XandZAsLong(x, z), Integer.MIN_VALUE);
        return encodedValue == Integer.MIN_VALUE ? encodedValue : BorderYFromInt(encodedValue);
    }

    // Populate landscape map with absolute world coordinates for the highest non-air block in each column
    public void populateLandscapeMap() {
        if (landscapeTemplate == null) {
            LOGGER.error("Cannot populate landscape map: landscapeTemplate is null for chapter {}", name);
            throw new RuntimeException("landscapeTemplate is null for chapter " + name);
        }

        // Placement origin (use chapterArea.beginning as beginningTemp, adjust if you have a specific field)
        BlockPos origin = new BlockPos(chapterArea.beginning.x, 0, chapterArea.beginning.z); // y=64 for flat terrain

        BlockPos placeholder = new BlockPos(0, 0, 0);
        LOGGER.info("{}. Chapter Area start: {}. Chapter Area end: {}",this.parentStory.name, this.chapterArea.beginning, this.chapterArea.end);

        // Default placement settings (no rotation/mirror, adjust as needed)
        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(BlockRotation.NONE)
                .setPosition(placeholder);

        // Get transformed block infos
        List<StructureTemplate.StructureBlockInfo> blockInfos = placementData.getRandomBlockInfos(landscapeTemplate.blockInfoLists, placeholder).getAll();

        // Iterate over blocks, filter non-air, and store only the highest y for each (x, z) column
        for (StructureTemplate.StructureBlockInfo info : blockInfos) {
            if (!info.state().isAir()) {
                BlockPos worldPos = info.pos().add(origin); // Absolute world position
                long key = XandZAsLong(worldPos.getX(), worldPos.getZ());
                int y = worldPos.getY() + chaptersBaseHeight;

                // Update the y-value only if it's higher than the current stored value (or if no value exists)
                if (!landscapeMap.containsKey(key) || y > landscapeMap.get(key)) {
                    landscapeMap.put(key, y);
                }
            }
        }

        //LOGGER.info("Populated landscape map with {} columns for chapter {}", landscapeMap.size(), name);
        //LOGGER.info(landscapeMap.toString());
        computeLandscapeBorder();
        computeRingLayersFromLandscape();

    }



    // Populate environment map with absolute world coordinates
    public void populateEnvironmentMap() {
        if (environmentTemplate == null) {
            LOGGER.error("Cannot populate landscape map: environmentTemplate is null for chapter {}", name);
            throw new RuntimeException("environmentTemplate is null for chapter " + name);
        }
        maxEnvironmentHeight = environmentTemplate.getSize().getY() + chaptersBaseHeight;

        // Placement origin (use chapterArea.beginning as beginningTemp, adjust if you have a specific field)
        BlockPos origin = new BlockPos(chapterArea.beginning.x, 0, chapterArea.beginning.z); // y=64 for flat terrain
        BlockPos placeholder = new BlockPos(0, 0, 0);

        // Default placement settings (no rotation/mirror, adjust as needed)
        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(BlockRotation.NONE)
                .setPosition(placeholder);

        // Get transformed block infos
        List<StructureTemplate.StructureBlockInfo> blockInfos = placementData.getRandomBlockInfos(environmentTemplate.blockInfoLists, placeholder).getAll();

        // Iterate over blocks, filter non-air, and store only the highest y for each (x, z) column
        for (StructureTemplate.StructureBlockInfo info : blockInfos) {
            BlockState blockState = info.state();
            //if (!blockState.isAir()) {

                BlockPos worldPos = info.pos().add(origin); // Absolute world position
                //System.out.println(worldPos);
                long key = XYZasLong(worldPos.getX(), worldPos.getY() + chaptersBaseHeight, worldPos.getZ());
                noFoliageMask.add(XandZAsLong(worldPos.getX(), worldPos.getZ()));
                if (!(blockState == Blocks.PINK_WOOL.getDefaultState())) {
                    environmentMap.put(key, blockState);
                }
            //}
        }

        //LOGGER.info("Populated environment map with {} columns for chapter {}", environmentMap.size(), name);
        //LOGGER.info(environmentMap.toString());
    }

    public void computeLandscapeBorder() {
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        for (long key : landscapeMap.keySet()) {
            int x = LongToX(key);
            int z = LongToZ(key);

            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int nz = z + dz[i];
                long neighborKey = XandZAsLong(nx, nz);

                if (!landscapeMap.containsKey(neighborKey)) {
                    landscapeBorder.put(key, landscapeMap.get(key));
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
        ringLayersMap = new Long2IntOpenHashMap();

        // Initialize ring layer 1 from landscape border
        for (long key : landscapeBorder.keySet()) {
            int x = LongToX(key);
            int z = LongToZ(key);
            int borderY = landscapeBorder.get(key); // Get the Y-value
            long packedKey = XandZAsLong(x, z);

            queue.add(new RingStep(x, z, borderY));
            ringLayersMap.put(packedKey, RingLayerandYAsInt(1, borderY)); // Ring layer 1
            visited.add(packedKey);
        }

        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        while (!queue.isEmpty()) {
            RingStep current = queue.poll();
            int currentLayer = RingLayerFromInt(ringLayersMap.get(XandZAsLong(current.x, current.z)));

            if (currentLayer >= StoryEngine.ringRadius) continue;

            for (int i = 0; i < 4; i++) {
                int nx = current.x + dx[i];
                int nz = current.z + dz[i];

                if (!ringArea.inBounds(nx, nz)) continue;

                long key = XandZAsLong(nx, nz);
                if (visited.contains(key) || landscapeMap.containsKey(key)) continue;

                ringLayersMap.put(key, RingLayerandYAsInt(currentLayer + 1, current.borderY));
                visited.add(key);
                queue.add(new RingStep(nx, nz, current.borderY));
            }
        }

        //LOGGER.info("Calculated ring layers: {}", ringLayersMap.size());
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
    public StructureTemplate loadStructureTemplate(MinecraftServer server, String structureName, String modId) {
        // Get the StructureTemplateManager
        StructureTemplateManager templateManager = server.getStructureTemplateManager();
        Identifier structureId = new Identifier(modId, structureName);

        // Load the template
        return templateManager.getTemplate(structureId)
                .orElseThrow(() -> new IllegalArgumentException("Structure not found: " + structureId));
    }

    public void LoadEnvironmentTemplate(MinecraftServer server) {
        environmentTemplate = loadStructureTemplate(server, environmentName, parentStory.ModId);
    }

    public void LoadLandscapeTemplate(MinecraftServer server) {
        landscapeTemplate = loadStructureTemplate(server, landscapeName, parentStory.ModId);
    }

    public NbtCompound SaveToNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.put("ringArea", ringArea.SaveToNbt());
        nbt.put("chapterArea", chapterArea.SaveToNbt());
        return nbt;
    }

    public void LoadFromNbt(NbtCompound nbt) {
        if (nbt.contains("ringArea")) {
            this.ringArea = Zone.LoadFromNbt(nbt.getCompound("ringArea"));
        }
        if (nbt.contains("chapterArea")) {
            chapterArea = Zone.LoadFromNbt(nbt.getCompound("chapterArea"));
        }
    }
}
