package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructurePlacementData;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.structure.StructureTemplateManager;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;

import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.amyrose.storyamycore.core.StoryEngine.*;

public class Chapter {
    private static final int SEARCH_STEP = 32;
    private static final int MAX_RANGE_FACTOR = 4;

    public Story parentStory; // reference to parent story.
    public boolean first = false;
    public boolean wasVisited = false;
    public Chapter previousChapter;
    BlockPos2D offsetFromPrevious;
    BlockPos2D size;
    public String name;
    public ChapterRotation rotation = ChapterRotation.NONE;
    public Zone chapterArea;
    public Zone ringArea;
    public String biomeName; // optional "biome" JSON field: when set, the chapter area is generated as this biome.
    public RegistryEntry<Biome> biome; // resolved from biomeName at server start, read by BiomeSourceMixin.
    public String landscapeName;
    public String environmentName;
    public StructureTemplate landscapeTemplate;
    public int landscapeHighestPoint = Integer.MIN_VALUE;
    public StructureTemplate environmentTemplate;
    public int environmentHighestPoint = Integer.MIN_VALUE;
    public StructureTemplate noFoliageTemplate;

    public int chaptersBaseHeight = 64;
    public int maxEnvironmentHeight;
    public LongOpenHashSet noFoliageMask = new LongOpenHashSet();
    public Long2IntOpenHashMap landscapeMap = new Long2IntOpenHashMap();
    public Long2IntOpenHashMap landscapeBorder = new Long2IntOpenHashMap();
    public Long2IntOpenHashMap ringLayersMap = new Long2IntOpenHashMap();
    public Long2ObjectOpenHashMap<BlockState> environmentMap = new Long2ObjectOpenHashMap<>();
    public Long2ObjectOpenHashMap<NbtCompound> environmentBlockEntityNbt = new Long2ObjectOpenHashMap<>();
    public Set<String> allowedBiomes;
    public double minBiomeShare = 1.0; // share of sampled points that must be in allowedBiomes, set from the optional "minBiomeShare" JSON field.
    public BlockState landscapeBlockState;
    public Set<BlockState> forbiddenLandscapeBlocks;

    public Chapter(BlockPos2D size, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight) {
        this.landscapeName = landscapeName;
        this.landscapeBlockState = landscapeBlockState;
        this.forbiddenLandscapeBlocks = forbiddenLandscapeBlocks;
        this.environmentName = environmentName;
        this.size = size;
        this.chaptersBaseHeight = chaptersBaseHeight;
        this.allowedBiomes = allowedBiomes;
        first = true;
    }

    public Chapter(BlockPos2D size, BlockPos2D offsetFromPrevious, String landscapeName, BlockState landscapeBlockState, Set<BlockState> forbiddenLandscapeBlocks, String environmentName, Set<String> allowedBiomes, int chaptersBaseHeight) {
        this.landscapeName = landscapeName;
        this.landscapeBlockState = landscapeBlockState;
        this.forbiddenLandscapeBlocks = forbiddenLandscapeBlocks;
        this.environmentName = environmentName;
        this.size = size;
        this.offsetFromPrevious = offsetFromPrevious;
        this.chaptersBaseHeight = chaptersBaseHeight;
        this.allowedBiomes = allowedBiomes;
    }


    public void initializeChapter(Story story, BiomeAccess biomeAccess)
    {
        parentStory = story; // saves reference to story.
        BlockPos2D beginning;

        if(first) {
            beginning = new BlockPos2D(0, 0);
        }
        else {
            beginning = new BlockPos2D(previousChapter.chapterArea.end.x, previousChapter.chapterArea.end.z);
            beginning.add(offsetFromPrevious);
        }

        /* The rotation is not searched, it follows from where the chapter lies relative to the player's reference point (see facePlayer),
           so the chapter always faces the player. The intended spot is tried first, with the rotation turning around the intended corner (so that
           corner stays a corner of the chapter, and a player standing at it is at the chapter's edge, not in its middle). If it does not fit, the range
           around it (baseMaxDistanceRadius * MAX_RANGE_FACTOR) is scanned with one biome lookup per SEARCH_STEP grid point and every point inside an
           allowed biome becomes a candidate (a locate-biome call would return only one). Candidates are sorted by distance from the intended spot, and
           the chapter zone centered on each one is tested until one passes, so the nearest suitable spot wins. No randomness: the same seed always
           gives the same result. */
        int referenceX = 0;
        int referenceZ = 0;
        if (!first) {
            referenceX = (previousChapter.chapterArea.beginning.x + previousChapter.chapterArea.end.x) / 2;
            referenceZ = (previousChapter.chapterArea.beginning.z + previousChapter.chapterArea.end.z) / 2;
        }

        ChapterRotation intendedRotation = facePlayer(beginning.x + size.x / 2, beginning.z + size.z / 2, referenceX, referenceZ);
        Zone tempChapterArea = rotatedZone(intendedRotation, beginning.x, beginning.z, false);
        if (tempChapterArea.IsInAllowedBiome(allowedBiomes, chaptersBaseHeight, biomeAccess, minBiomeShare)) {
            rotation = intendedRotation;
        } else {
            tempChapterArea = null;
            int range = story.baseMaxDistanceRadius * MAX_RANGE_FACTOR;
            List<int[]> candidates = new ArrayList<>();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            for (int dx = -range; dx <= range; dx += SEARCH_STEP) {
                for (int dz = -range; dz <= range; dz += SEARCH_STEP) {
                    pos.set(beginning.x + dx, chaptersBaseHeight, beginning.z + dz);
                    if (allowedBiomes.contains(getBiomeKey(pos, biomeAccess))) {
                        candidates.add(new int[]{dx, dz});
                    }
                }
            }
            candidates.sort(Comparator.<int[]>comparingLong(c -> (long) c[0] * c[0] + (long) c[1] * c[1]).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));

            for (int i = 0; i < candidates.size(); i++) {
                int[] candidate = candidates.get(i);
                int centerX = beginning.x + candidate[0];
                int centerZ = beginning.z + candidate[1];
                ChapterRotation candidateRotation = facePlayer(centerX, centerZ, referenceX, referenceZ);
                Zone zone = rotatedZone(candidateRotation, centerX, centerZ, true);
                if (zone.IsInAllowedBiome(allowedBiomes, chaptersBaseHeight, biomeAccess, minBiomeShare)) {
                    tempChapterArea = zone;
                    rotation = candidateRotation;
                    LOGGER.info("Chapter placed {} blocks from the intended spot (candidate {} of {})", (int) Math.hypot(candidate[0], candidate[1]), i + 1, candidates.size());
                    break;
                }
            }
        }

        if (tempChapterArea == null)
        {
            LOGGER.error("Couldn't find suitable place for chapter within {} blocks. Please change chapter settings", story.baseMaxDistanceRadius * MAX_RANGE_FACTOR);
            throw new ChapterPlacementException("Couldn't find a suitable place for chapter '" + name + "' of story '" + story.name + "'. Try another world seed or change the chapter settings.");
        }

        // if I wanted to search ring wide - I would use this.
        Zone tempRingArea = new Zone(
                new BlockPos2D(tempChapterArea.beginning.x - StoryEngine.ringRadius, tempChapterArea.beginning.z - StoryEngine.ringRadius),
                new BlockPos2D(tempChapterArea.end.x + StoryEngine.ringRadius, tempChapterArea.end.z + StoryEngine.ringRadius)
        );
        chapterArea = tempChapterArea;
        ringArea = tempRingArea;
        LOGGER.info("Final coords (rotation {}): beginning.x = {}, beginning.z = {}, end.x = {}, end.z = {}", rotation, chapterArea.beginning.x, chapterArea.beginning.z, chapterArea.end.x, chapterArea.end.z);
        //if(first) story.area.beginning = ringArea.beginning;
    }



    /* Turns the chapter towards the player. The player looks south (+Z, the default spawn facing) and the unrotated chapter's front is its north side.
       Whichever axis the chapter is farther along decides: ahead of the player (south) is NONE, behind (north) is BACK, and to the side east/west
       is RIGHT/LEFT (a 90 degree turn of the chapter, not the side of the player it is on). */
    private ChapterRotation facePlayer(int centerX, int centerZ, int playerX, int playerZ) {
        int toChapterX = centerX - playerX;
        int toChapterZ = centerZ - playerZ;
        if (Math.abs(toChapterZ) >= Math.abs(toChapterX)) {
            return toChapterZ >= 0 ? ChapterRotation.NONE : ChapterRotation.BACK;
        }
        return toChapterX > 0 ? ChapterRotation.RIGHT : ChapterRotation.LEFT;
    }

    /* The chapter zone for a rotation: with centered it is centered on (x, z), otherwise (x, z) stays a corner of the zone.
       RIGHT/LEFT swap the X and Z size. */
    private Zone rotatedZone(ChapterRotation rotation, int x, int z, boolean centered) {
        int sizeX = rotation.swapsAxes() ? size.z : size.x;
        int sizeZ = rotation.swapsAxes() ? size.x : size.z;
        int beginX;
        int beginZ;
        if (centered) {
            beginX = x - sizeX / 2;
            beginZ = z - sizeZ / 2;
        } else {
            beginX = (rotation == ChapterRotation.RIGHT || rotation == ChapterRotation.BACK) ? x - sizeX : x;
            beginZ = (rotation == ChapterRotation.BACK || rotation == ChapterRotation.LEFT) ? z - sizeZ : z;
        }
        return new Zone(new BlockPos2D(beginX, beginZ), new BlockPos2D(beginX + sizeX, beginZ + sizeZ));
    }

    /* Templates are rotated around their own (0, 0) corner, which moves their blocks to the negative side for RIGHT/LEFT/BACK.
       The origin is shifted back so the rotated blocks fill chapterArea exactly (size - 1 because a template of N blocks spans 0..N-1). */
    private BlockPos templateOrigin() {
        int offsetX = switch (rotation) {
            case RIGHT -> size.z - 1;
            case BACK -> size.x - 1;
            default -> 0;
        };
        int offsetZ = switch (rotation) {
            case LEFT -> size.x - 1;
            case BACK -> size.z - 1;
            default -> 0;
        };
        return new BlockPos(chapterArea.beginning.x + offsetX, 0, chapterArea.beginning.z + offsetZ);
    }

    /* getRandomBlockInfos does not apply the rotation to positions, so it is applied here (and to block states where it is needed). */
    private BlockPos rotatePos(BlockPos pos) {
        return StructureTemplate.transformAround(pos, BlockMirror.NONE, rotation.blockRotation, BlockPos.ORIGIN);
    }

    public String getBiomeKey(BlockPos pos, BiomeAccess biomeAccess) {
        return biomeAccess.getBiome(pos).getKey()
                .map(k -> k.getValue().toString())
                .orElse("unknown_biome");
    }

//    public void addStoryCoords() {
//        // Shift chapterArea relative to the story’s world position
//        BlockPos2D storyBeginningMinusRingRadius = new BlockPos2D(parentStory.area.beginning.x + StoryEngine.ringRadius, parentStory.area.beginning.z + StoryEngine.ringRadius);
//        chapterArea.beginning.add(storyBeginningMinusRingRadius);
//        chapterArea.end.add(storyBeginningMinusRingRadius);
//
//        // ringArea follows the same world offset
//        ringArea.beginning.add(storyBeginningMinusRingRadius);
//        ringArea.end.add(storyBeginningMinusRingRadius);
//    }

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
        BlockPos origin = templateOrigin();

        BlockPos placeholder = new BlockPos(0, 0, 0);
        LOGGER.info("{}. Chapter Area start: {}. Chapter Area end: {}",this.parentStory.name, this.chapterArea.beginning, this.chapterArea.end);

        // Default placement settings (no rotation/mirror, adjust as needed)
        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(rotation.blockRotation)
                .setPosition(placeholder);

        // Get transformed block infos
        List<StructureTemplate.StructureBlockInfo> blockInfos = placementData.getRandomBlockInfos(landscapeTemplate.blockInfoLists, placeholder).getAll();

        // Iterate over blocks, filter non-air, and store only the highest y for each (x, z) column
        for (StructureTemplate.StructureBlockInfo info : blockInfos) {
            if (!info.state().isAir()) {
                BlockPos worldPos = rotatePos(info.pos()).add(origin); // Absolute world position
                long key = XandZAsLong(worldPos.getX(), worldPos.getZ());
                int y = worldPos.getY() + chaptersBaseHeight;

                // Update the y-value only if it's higher than the current stored value (or if no value exists)
                if (!landscapeMap.containsKey(key) || y > landscapeMap.get(key)) {
                    if(landscapeHighestPoint < y) {
                        landscapeHighestPoint = y;
                    }
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
        BlockPos origin = templateOrigin();
        BlockPos placeholder = new BlockPos(0, 0, 0);

        // Default placement settings (no rotation/mirror, adjust as needed)
        StructurePlacementData placementData = new StructurePlacementData()
                .setMirror(BlockMirror.NONE)
                .setRotation(rotation.blockRotation)
                .setPosition(placeholder);

        // Get transformed block infos
        List<StructureTemplate.StructureBlockInfo> blockInfos = placementData.getRandomBlockInfos(environmentTemplate.blockInfoLists, placeholder).getAll();

        // Iterate over blocks, filter non-air, and store only the highest y for each (x, z) column
        for (StructureTemplate.StructureBlockInfo info : blockInfos) {
            BlockState blockState = info.state().rotate(rotation.blockRotation);
            //if (!blockState.isAir()) {

                BlockPos worldPos = rotatePos(info.pos()).add(origin); // Absolute world position
                //System.out.println(worldPos);
                int currentY = worldPos.getY() + chaptersBaseHeight;
                long key = XYZasLong(worldPos.getX(), currentY, worldPos.getZ());

                /* Air is not stored: the stamp treats a missing entry as air, and a template can hold millions of air blocks. */
                if (!blockState.isAir()) {
                    if(environmentHighestPoint < currentY) {
                        environmentHighestPoint = currentY;
                    }
                    environmentMap.put(key, blockState);
                    if (info.nbt() != null) {
                        environmentBlockEntityNbt.put(key, info.nbt());
                    }
                }
            //}
            //}
        }

        //LOGGER.info("Populated environment map with {} columns for chapter {}", environmentMap.size(), name);
        //LOGGER.info(environmentMap.toString());

        /* The no-foliage template is placed at the same origin as the environment (same size in X and Z, checked at load), so columns map 1:1.
           Every block in it excludes its x/z column from foliage, except red wool, which only pins the template's corners to the environment's size. */
        if (noFoliageTemplate != null) {
            for (StructureTemplate.StructureBlockInfo info : placementData.getRandomBlockInfos(noFoliageTemplate.blockInfoLists, placeholder).getAll()) {
                if (info.state().isOf(Blocks.RED_WOOL)) continue;
                BlockPos worldPos = rotatePos(info.pos()).add(origin);
                noFoliageMask.add(XandZAsLong(worldPos.getX(), worldPos.getZ()));
            }
        }
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

        /* The no-foliage template is optional and named after the environment with "-environment" swapped for "-no-foliage"
           ("prologue-environment" -> "prologue-no-foliage"); a name without that suffix just gets "-no-foliage" appended.
           Without the file the chapter simply has no mask. */
        String noFoliageName = environmentName.endsWith("-environment")
                ? environmentName.substring(0, environmentName.length() - "-environment".length()) + "-no-foliage"
                : environmentName + "-no-foliage";
        noFoliageTemplate = server.getStructureTemplateManager()
                .getTemplate(new Identifier(parentStory.ModId, noFoliageName))
                .orElse(null);
        if (noFoliageTemplate != null) {
            Vec3i envSize = environmentTemplate.getSize();
            Vec3i maskSize = noFoliageTemplate.getSize();
            if (envSize.getX() != maskSize.getX() || envSize.getZ() != maskSize.getZ()) {
                LOGGER.error("No-foliage template {} is {}x{} (X by Z) but environment {} is {}x{}. They must match so they map 1:1: put red wool in the corners.", noFoliageName, maskSize.getX(), maskSize.getZ(), environmentName, envSize.getX(), envSize.getZ());
                throw new RuntimeException("No-foliage template size differs from environment for chapter " + name);
            }
        }
    }

    /* Resolved here on the server thread so worldgen threads only read the finished field. An unknown biome id fails loudly instead of silently generating the normal biome. */
    public void LoadBiome(MinecraftServer server) {
        if (biomeName == null) {
            biome = null;
            return;
        }
        Identifier id = Identifier.tryParse(biomeName);
        biome = id == null ? null : server.getRegistryManager().get(RegistryKeys.BIOME).getEntry(RegistryKey.of(RegistryKeys.BIOME, id)).orElse(null);
        if (biome == null) {
            LOGGER.error("Chapter '{}' of story '{}' has unknown biome '{}'", name, parentStory.name, biomeName);
            throw new RuntimeException("Unknown biome '" + biomeName + "' for chapter of story '" + parentStory.name + "'.");
        }
    }

    public void LoadLandscapeTemplate(MinecraftServer server) {
        landscapeTemplate = loadStructureTemplate(server, landscapeName, parentStory.ModId);
    }

    /* A chapter has no placement when its story was registered after the world was created: story_data.dat has no entry for it. */
    public boolean isPlaced() {
        return ringArea != null && chapterArea != null;
    }

    public NbtCompound SaveToNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.put("ringArea", ringArea.SaveToNbt());
        nbt.put("chapterArea", chapterArea.SaveToNbt());
        nbt.putString("rotation", rotation.name());
        nbt.putBoolean("wasVisited", wasVisited);
        return nbt;
    }

    public void LoadFromNbt(NbtCompound nbt) {
        if (nbt.contains("ringArea")) {
            this.ringArea = Zone.LoadFromNbt(nbt.getCompound("ringArea"));
        }
        if (nbt.contains("chapterArea")) {
            chapterArea = Zone.LoadFromNbt(nbt.getCompound("chapterArea"));
        }
        /* Saves from before rotation existed have no entry: they were placed unrotated. */
        rotation = nbt.contains("rotation") ? ChapterRotation.valueOf(nbt.getString("rotation")) : ChapterRotation.NONE;
        wasVisited = nbt.getBoolean("wasVisited");
    }
}
