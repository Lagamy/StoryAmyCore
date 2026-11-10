package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.amyrose.storyamycore.core.StoryEngine.*;

public class Chapter {
    private static final int SEARCH_STEP = 32;
    private static final int MAX_RANGE_FACTOR = 4;
    private static final double ZONE_PREFILTER_MARGIN = 0.15;

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
    public BlockPos2D landscapeTileSize; // optional "landscapeTileSize" JSON field: when set, the landscape file is split into internal tiles of this size (one file, <landscapeName>.chap).
    public List<LandscapeTile> landscapeTiles = new ArrayList<>();
    private LandscapeTile[] tileGrid = new LandscapeTile[0];
    private int tileCellsZ;
    private int tileCellWidth = 1;
    private int tileCellDepth = 1;
    public int landscapeHighestPoint = Integer.MIN_VALUE;
    public Vec3i environmentSize;
    private int environmentDropped;
    public int environmentHighestPoint = Integer.MIN_VALUE;
    private Identifier noFoliageFileId; // null when the chapter has no no-foliage file (see LoadEnvironmentTemplate).

    public int chaptersBaseHeight = 64;
    public int maxEnvironmentHeight;
    public LongOpenHashSet noFoliageMask = new LongOpenHashSet();
    public Long2IntOpenHashMap landscapeBorder = new Long2IntOpenHashMap();
    public Long2IntOpenHashMap ringLayersMap = new Long2IntOpenHashMap();
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

        /* The rotation is not free: only the rotations from rotationsToTry are allowed for a spot, in that order (see there). The intended spot is
           tried first, with the rotation turning around the intended corner (so that corner stays a corner of the chapter, and a player standing at it
           is at the chapter's edge, not in its middle). If it does not fit, the range around it (baseMaxDistanceRadius * MAX_RANGE_FACTOR) is scanned
           with one biome lookup per SEARCH_STEP grid point and every point inside an allowed biome becomes a candidate (a locate-biome call would
           return only one). Candidates are sorted by distance from the intended spot, and the chapter zone centered on each one is tested with each
           allowed rotation until one passes, so the nearest suitable spot wins. No randomness: the same seed always gives the same result. */
        Zone tempChapterArea = null;
        for (ChapterRotation intendedRotation : rotationsToTry(beginning.x, beginning.z)) {
            Zone zone = rotatedZone(intendedRotation, beginning.x, beginning.z, false);
            if (zone.IsInAllowedBiome(allowedBiomes, chaptersBaseHeight, biomeAccess, minBiomeShare)) {
                tempChapterArea = zone;
                rotation = intendedRotation;
                break;
            }
        }
        if (tempChapterArea == null) {
            int range = story.baseMaxDistanceRadius * MAX_RANGE_FACTOR;
            List<int[]> candidates = new ArrayList<>();
            BlockPos.Mutable pos = new BlockPos.Mutable();
            /* Every biome lookup is done once, here. allowedBefore[i][j] counts the allowed grid points in the rectangle [0, i) x [0, j), so how many of
               them lie inside any zone is four array reads later. Testing each candidate zone with real biome lookups took minutes on a 1000 block chapter. */
            int gridSize = 2 * range / SEARCH_STEP + 1;
            int[][] allowedBefore = new int[gridSize + 1][gridSize + 1];
            for (int i = 0; i < gridSize; i++) {
                for (int j = 0; j < gridSize; j++) {
                    int dx = i * SEARCH_STEP - range;
                    int dz = j * SEARCH_STEP - range;
                    pos.set(beginning.x + dx, chaptersBaseHeight, beginning.z + dz);
                    boolean allowed = allowedBiomes.contains(getBiomeKey(pos, biomeAccess));
                    if (allowed) {
                        candidates.add(new int[]{dx, dz});
                    }
                    allowedBefore[i + 1][j + 1] = allowedBefore[i][j + 1] + allowedBefore[i + 1][j] - allowedBefore[i][j] + (allowed ? 1 : 0);
                }
            }
            candidates.sort(Comparator.<int[]>comparingLong(c -> (long) c[0] * c[0] + (long) c[1] * c[1]).thenComparingInt(c -> c[0]).thenComparingInt(c -> c[1]));

            for (int i = 0; i < candidates.size() && tempChapterArea == null; i++) {
                int[] candidate = candidates.get(i);
                int centerX = beginning.x + candidate[0];
                int centerZ = beginning.z + candidate[1];
                for (ChapterRotation candidateRotation : rotationsToTry(centerX - size.x / 2, centerZ - size.z / 2)) {
                    Zone zone = rotatedZone(candidateRotation, centerX, centerZ, true);
                    /* The grid is coarser than the real check, so it only rejects zones that are clearly short of the share (with a margin); the real check decides. */
                    int firstI = Math.floorDiv(zone.beginning.x - beginning.x + range + SEARCH_STEP - 1, SEARCH_STEP);
                    int lastI = Math.floorDiv(zone.end.x - beginning.x + range, SEARCH_STEP);
                    int firstJ = Math.floorDiv(zone.beginning.z - beginning.z + range + SEARCH_STEP - 1, SEARCH_STEP);
                    int lastJ = Math.floorDiv(zone.end.z - beginning.z + range, SEARCH_STEP);
                    if (firstI < 0 || firstJ < 0 || lastI >= gridSize || lastJ >= gridSize || lastI < firstI || lastJ < firstJ) {
                        continue;
                    }
                    int allowedInZone = allowedBefore[lastI + 1][lastJ + 1] - allowedBefore[firstI][lastJ + 1] - allowedBefore[lastI + 1][firstJ] + allowedBefore[firstI][firstJ];
                    int gridPointsInZone = (lastI - firstI + 1) * (lastJ - firstJ + 1);
                    if (allowedInZone < (minBiomeShare - ZONE_PREFILTER_MARGIN) * gridPointsInZone) {
                        continue;
                    }
                    if (zone.IsInAllowedBiome(allowedBiomes, chaptersBaseHeight, biomeAccess, minBiomeShare)) {
                        tempChapterArea = zone;
                        rotation = candidateRotation;
                        LOGGER.info("Chapter placed {} blocks from the intended spot (candidate {} of {})", (int) Math.hypot(candidate[0], candidate[1]), i + 1, candidates.size());
                        break;
                    }
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



    /* The rotations allowed for a chapter whose start corner (its most negative x and z, as the unrotated chapter) is (startX, startZ), in the order they are tested.
       The chapter is judged by where that corner lies relative to a reference: the player's start (0, 0) for the first chapter, and for the others the point
       of the previous chapter's area (not its ring) nearest to the corner. A chapter is never judged by its center, so one that starts north of the reference
       is behind it even when it is centered on it.
       The player looks south (+Z, the default spawn facing) and the unrotated chapter's front is its north side. Ahead of the reference only NONE
       is allowed (behind it only BACK), and a chapter to the east/west also may turn RIGHT/LEFT (a 90 degree turn of the chapter, not the side of
       the reference it is on). When it lies farther to the side than ahead, the side turn is tested first, otherwise the straight one.
       The rotation opposite to the previous chapter's (NONE/BACK, RIGHT/LEFT) is never allowed, so the story does not fold back on itself. */
    private List<ChapterRotation> rotationsToTry(int startX, int startZ) {
        int referenceX = 0;
        int referenceZ = 0;
        if (!first) {
            referenceX = Math.max(previousChapter.chapterArea.beginning.x, Math.min(startX, previousChapter.chapterArea.end.x));
            referenceZ = Math.max(previousChapter.chapterArea.beginning.z, Math.min(startZ, previousChapter.chapterArea.end.z));
        }
        int toChapterX = startX - referenceX;
        int toChapterZ = startZ - referenceZ;
        ChapterRotation straight = toChapterZ >= 0 ? ChapterRotation.NONE : ChapterRotation.BACK;
        List<ChapterRotation> rotations = new ArrayList<>();
        if (toChapterX == 0) {
            rotations.add(straight);
        } else {
            ChapterRotation side = toChapterX > 0 ? ChapterRotation.RIGHT : ChapterRotation.LEFT;
            if (Math.abs(toChapterX) > Math.abs(toChapterZ)) {
                rotations.add(side);
                rotations.add(straight);
            } else {
                rotations.add(straight);
                rotations.add(side);
            }
        }
        if (!first) {
            rotations.remove(previousChapter.rotation.opposite());
        }
        return rotations;
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

    /* The tile that owns a world column, or null outside the chapter. A tile's world area is a fixed-size cell of the chapter, so the tile is found by
       integer division from the chapter corner, with no hash lookup. The chapter's blocks fill chapterArea.beginning up to size - 1 (see templateOrigin). */
    private LandscapeTile tileAt(int x, int z) {
        int cellX = Math.floorDiv(x - chapterArea.beginning.x, tileCellWidth);
        int cellZ = Math.floorDiv(z - chapterArea.beginning.z, tileCellDepth);
        if (cellX < 0 || cellZ < 0 || cellZ >= tileCellsZ || cellX * tileCellsZ + cellZ >= tileGrid.length) {
            return null;
        }
        return tileGrid[cellX * tileCellsZ + cellZ];
    }

    /* Highest landscape block Y of a world column, or Integer.MIN_VALUE when the column is not part of the landscape. */
    public int getLandscapeY(int x, int z) {
        LandscapeTile tile = tileAt(x, z);
        if (tile == null) {
            return Integer.MIN_VALUE;
        }
        return tile.landscapeMap.getOrDefault(XandZAsLong(x, z), Integer.MIN_VALUE);
    }

    /* The solid surface block of a world column from the landscape template, or null when the column is not part of the landscape. */
    public BlockState getLandscapeSurface(int x, int z) {
        LandscapeTile tile = tileAt(x, z);
        if (tile == null) {
            return null;
        }
        return tile.surfaceMap.get(XandZAsLong(x, z));
    }

    /* Water surface Y of a world column, or Integer.MIN_VALUE when no water lies above the landscape surface there. */
    public int getWaterY(int x, int z) {
        LandscapeTile tile = tileAt(x, z);
        if (tile == null) {
            return Integer.MIN_VALUE;
        }
        return tile.waterMap.getOrDefault(XandZAsLong(x, z), Integer.MIN_VALUE);
    }

    /* Splits the chapter into internal landscape tiles. Without landscapeTileSize there is one tile covering the whole size.
       All tiles are read from the one landscapeName file; tiles are listed column by column along X, then row by row along Z, before rotation. */
    public void PrepareLandscapeTiles() {
        landscapeTiles.clear();
        BlockPos2D tileSize = landscapeTileSize == null ? size : landscapeTileSize;
        if (tileSize.x <= 0 || tileSize.z <= 0 || size.x % tileSize.x != 0 || size.z % tileSize.z != 0) {
            LOGGER.error("Chapter {} has size {}x{} which landscapeTileSize {}x{} does not divide evenly.", name, size.x, size.z, tileSize.x, tileSize.z);
            throw new RuntimeException("landscapeTileSize does not divide the size of chapter " + name);
        }
        for (int column = 0; column < size.x / tileSize.x; column++) {
            for (int row = 0; row < size.z / tileSize.z; row++) {
                landscapeTiles.add(new LandscapeTile(new BlockPos2D(column * tileSize.x, row * tileSize.z)));
            }
        }
    }

    /* Populate landscape maps with absolute world coordinates for the highest non-air block in each column.
       The landscape .chap file is read once by ChapReader and every vertical span goes straight into the column maps of the internal
       tile that owns it, so the blocks of a big landscape are never in memory, only the per-tile column maps stay. */
    public void populateLandscapeMap(MinecraftServer server) {
        if (landscapeTiles.isEmpty()) {
            LOGGER.error("Cannot populate landscape map: no landscape tiles for chapter {}", name);
            throw new RuntimeException("no landscape tiles for chapter " + name);
        }

        // Placement origin (use chapterArea.beginning as beginningTemp, adjust if you have a specific field)
        BlockPos origin = templateOrigin();
        LOGGER.info("{}. Chapter Area start: {}. Chapter Area end: {}",this.parentStory.name, this.chapterArea.beginning, this.chapterArea.end);

        BlockPos2D tileSize = landscapeTileSize == null ? size : landscapeTileSize;
        tileCellWidth = rotation.swapsAxes() ? tileSize.z : tileSize.x;
        tileCellDepth = rotation.swapsAxes() ? tileSize.x : tileSize.z;
        int cellsX = (rotation.swapsAxes() ? size.z : size.x) / tileCellWidth;
        tileCellsZ = (rotation.swapsAxes() ? size.x : size.z) / tileCellDepth;
        tileGrid = new LandscapeTile[cellsX * tileCellsZ];
        landscapeHighestPoint = Integer.MIN_VALUE;

        for (LandscapeTile tile : landscapeTiles) {
            tile.landscapeMap = new Long2IntOpenHashMap();
            tile.surfaceMap = new Long2ObjectOpenHashMap<>();
            tile.waterMap = new Long2IntOpenHashMap();
            BlockPos cornerA = rotatePos(new BlockPos(tile.offset.x, 0, tile.offset.z)).add(origin);
            BlockPos cornerB = rotatePos(new BlockPos(tile.offset.x + tileSize.x - 1, 0, tile.offset.z + tileSize.z - 1)).add(origin);
            tile.area = new Zone(Math.min(cornerA.getX(), cornerB.getX()), Math.min(cornerA.getZ(), cornerB.getZ()), Math.max(cornerA.getX(), cornerB.getX()), Math.max(cornerA.getZ(), cornerB.getZ()));
            int cellX = (tile.area.beginning.x - chapterArea.beginning.x) / tileCellWidth;
            int cellZ = (tile.area.beginning.z - chapterArea.beginning.z) / tileCellDepth;
            tileGrid[cellX * tileCellsZ + cellZ] = tile;
        }

        /* Per column, keep the highest solid block (its Y and its state) and, separately, the highest water block.
           Water is not a surface: the river bed stays the landscape Y and the water is filled above it when stamping.
           Tiles are listed column by column along X, so a block's tile is its X cell times the row count plus its Z cell.
           The server resource manager is used because the mods' data packs (and so their chapters folder) are part of it. */
        int rows = size.z / tileSize.z;
        Identifier fileId = new Identifier(parentStory.ModId, "chapters/" + landscapeName + ".chap");
        ChapReader.Visitor visitor = (x, z, y, length, state) -> {
            if (state.isAir() || x < 0 || z < 0 || x >= size.x || z >= size.z) {
                return;
            }
            LandscapeTile tile = landscapeTiles.get((x / tileSize.x) * rows + z / tileSize.z);
            BlockPos worldPos = rotatePos(new BlockPos(x, y + length - 1, z)).add(origin); // Absolute world position of the top of the span
            long key = XandZAsLong(worldPos.getX(), worldPos.getZ());
            int worldY = worldPos.getY() + chaptersBaseHeight;

            if (state.isOf(Blocks.WATER)) {
                if (!tile.waterMap.containsKey(key) || worldY > tile.waterMap.get(key)) {
                    tile.waterMap.put(key, worldY);
                }
            } else if (!tile.landscapeMap.containsKey(key) || worldY > tile.landscapeMap.get(key)) {
                if (landscapeHighestPoint < worldY) {
                    landscapeHighestPoint = worldY;
                }
                tile.landscapeMap.put(key, worldY);
                tile.surfaceMap.put(key, state);
            }
        };

        try (InputStream stream = server.getResourceManager().open(fileId)) {
            ChapReader.read(stream, visitor);
        } catch (IOException e) {
            LOGGER.error("Cannot read landscape file {} of chapter {}: {}", fileId, name, e.getMessage());
            throw new RuntimeException("cannot read landscape file " + fileId, e);
        }

        /* Water that is not above the solid surface of its column is dropped. */
        for (LandscapeTile tile : landscapeTiles) {
            tile.waterMap.long2IntEntrySet().removeIf(entry -> entry.getIntValue() <= tile.landscapeMap.getOrDefault(entry.getLongKey(), Integer.MAX_VALUE));
            for (int waterY : tile.waterMap.values()) {
                if (landscapeHighestPoint < waterY) {
                    landscapeHighestPoint = waterY;
                }
            }
        }

        computeLandscapeBorder();
        computeRingLayersFromLandscape();

    }



    /* The environment block at a world position, or air when there is none (or the column is outside the chapter). */
    public BlockState getEnvironmentState(int x, int y, int z) {
        LandscapeTile tile = tileAt(x, z);
        if (tile == null) {
            return Blocks.AIR.getDefaultState();
        }
        return tile.environmentMap.getOrDefault(XYZasLong(x, y, z), Blocks.AIR.getDefaultState());
    }

    /* The saved block entity data of the environment block at a world position, or null. */
    public NbtCompound getEnvironmentBlockEntityNbt(int x, int y, int z) {
        LandscapeTile tile = tileAt(x, z);
        if (tile == null) {
            return null;
        }
        return tile.environmentBlockEntityNbt.get(XYZasLong(x, y, z));
    }

    /* Puts one vertical span of environment blocks, given in template coordinates, into the tile that owns its world column. Air is not stored: the stamp
       treats a missing entry as air. The span is in one column, so it is rotated once. */
    private void addEnvironmentSpan(BlockPos origin, int x, int z, int y, int length, BlockState state) {
        BlockState rotated = state.rotate(rotation.blockRotation);
        if (rotated.isAir()) {
            return;
        }
        BlockPos column = rotatePos(new BlockPos(x, 0, z)).add(origin);
        LandscapeTile tile = tileAt(column.getX(), column.getZ());
        if (tile == null) {
            environmentDropped += length;
            return;
        }
        for (int currentY = y + chaptersBaseHeight; currentY < y + length + chaptersBaseHeight; currentY++) {
            tile.environmentMap.put(XYZasLong(column.getX(), currentY, column.getZ()), rotated);
        }
        int topY = y + length - 1 + chaptersBaseHeight;
        if (environmentHighestPoint < topY) {
            environmentHighestPoint = topY;
        }
    }

    private void addEnvironmentBlockEntity(BlockPos origin, int x, int y, int z, NbtCompound blockEntityNbt) {
        BlockPos column = rotatePos(new BlockPos(x, 0, z)).add(origin);
        LandscapeTile tile = tileAt(column.getX(), column.getZ());
        if (tile != null) {
            tile.environmentBlockEntityNbt.put(XYZasLong(column.getX(), y + chaptersBaseHeight, column.getZ()), blockEntityNbt);
        }
    }

    /* Populate the environment maps of the internal tiles with absolute world coordinates. Needs the tiles of populateLandscapeMap.
       The environment .chap file is read region by region and every span goes straight into the tile that owns its column, so no list of
       all blocks is ever in memory. */
    public void populateEnvironmentMap(MinecraftServer server) {
        if (environmentSize == null) {
            LOGGER.error("Cannot populate environment map: environment not loaded for chapter {}", name);
            throw new RuntimeException("environment not loaded for chapter " + name);
        }
        maxEnvironmentHeight = environmentSize.getY() + chaptersBaseHeight;

        BlockPos origin = templateOrigin();
        environmentDropped = 0;
        for (LandscapeTile tile : landscapeTiles) {
            tile.environmentMap = new Long2ObjectOpenHashMap<>();
            tile.environmentBlockEntityNbt = new Long2ObjectOpenHashMap<>();
        }

        Identifier fileId = new Identifier(parentStory.ModId, "chapters/" + environmentName + ".chap");
        try (InputStream stream = server.getResourceManager().open(fileId)) {
            ChapReader.read(stream, new ChapReader.Visitor() {
                @Override
                public void visitSpan(int x, int z, int y, int length, BlockState state) {
                    addEnvironmentSpan(origin, x, z, y, length, state);
                }

                @Override
                public void visitBlockEntity(int x, int y, int z, NbtCompound nbt) {
                    addEnvironmentBlockEntity(origin, x, y, z, nbt);
                }
            });
        } catch (IOException e) {
            LOGGER.error("Cannot read environment file {} of chapter {}: {}", fileId, name, e.getMessage());
            throw new RuntimeException("cannot read environment file " + fileId, e);
        }
        if (environmentDropped > 0) {
            LOGGER.warn("{} environment blocks of chapter {} lie outside its {}x{} area and were dropped.", environmentDropped, name, size.x, size.z);
        }

        /* The no-foliage file is placed at the same origin as the environment (same size in X and Z, checked at load), so columns map 1:1.
           Every block in it excludes its x/z column from foliage, except red wool, which only pins the file's corners to the environment's size. */
        if (noFoliageFileId != null) {
            try (InputStream stream = server.getResourceManager().open(noFoliageFileId)) {
                ChapReader.read(stream, (x, z, y, length, state) -> {
                    if (state.isOf(Blocks.RED_WOOL)) {
                        return;
                    }
                    BlockPos worldPos = rotatePos(new BlockPos(x, 0, z)).add(origin);
                    noFoliageMask.add(XandZAsLong(worldPos.getX(), worldPos.getZ()));
                });
            } catch (IOException e) {
                LOGGER.error("Cannot read no-foliage file {} of chapter {}: {}", noFoliageFileId, name, e.getMessage());
                throw new RuntimeException("cannot read no-foliage file " + noFoliageFileId, e);
            }
        }
    }

    public void computeLandscapeBorder() {
        int[] dx = {1, -1, 0, 0};
        int[] dz = {0, 0, 1, -1};

        /* A neighbour in another tile counts as landscape too, so only the outer edge of the whole landscape is a border. */
        for (LandscapeTile tile : landscapeTiles) {
            for (long key : tile.landscapeMap.keySet()) {
                int x = LongToX(key);
                int z = LongToZ(key);

                for (int i = 0; i < 4; i++) {
                    int nx = x + dx[i];
                    int nz = z + dz[i];

                    if (getLandscapeY(nx, nz) == Integer.MIN_VALUE) {
                        landscapeBorder.put(key, tile.landscapeMap.get(key));
                        break;
                    }
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
                if (visited.contains(key) || getLandscapeY(nx, nz) != Integer.MIN_VALUE) continue;

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
    /* Reads the size of the environment file, which is all that is needed before the chapter is placed. */
    public void LoadEnvironmentTemplate(MinecraftServer server) {
        Identifier environmentId = new Identifier(parentStory.ModId, "chapters/" + environmentName + ".chap");
        try (InputStream stream = server.getResourceManager().open(environmentId)) {
            int[] fileSize = ChapReader.readSize(stream);
            environmentSize = new Vec3i(fileSize[0], fileSize[1], fileSize[2]);
        } catch (IOException e) {
            LOGGER.error("Cannot read environment file {} of chapter {}: {}", environmentId, name, e.getMessage());
            throw new RuntimeException("cannot read environment file " + environmentId, e);
        }

        /* The no-foliage file is optional and named after the environment with "-environment" swapped for "-no-foliage"
           ("prologue-environment" -> "prologue-no-foliage"); a name without that suffix just gets "-no-foliage" appended.
           Without the file the chapter simply has no mask. */
        String noFoliageName = environmentName.endsWith("-environment")
                ? environmentName.substring(0, environmentName.length() - "-environment".length()) + "-no-foliage"
                : environmentName + "-no-foliage";
        Identifier noFoliageId = new Identifier(parentStory.ModId, "chapters/" + noFoliageName + ".chap");
        noFoliageFileId = null;
        if (server.getResourceManager().getResource(noFoliageId).isEmpty()) {
            return;
        }
        int[] maskSize;
        try (InputStream stream = server.getResourceManager().open(noFoliageId)) {
            maskSize = ChapReader.readSize(stream);
        } catch (IOException e) {
            LOGGER.error("Cannot read no-foliage file {} of chapter {}: {}", noFoliageId, name, e.getMessage());
            throw new RuntimeException("cannot read no-foliage file " + noFoliageId, e);
        }
        if (environmentSize.getX() != maskSize[0] || environmentSize.getZ() != maskSize[2]) {
            LOGGER.error("No-foliage file {} is {}x{} (X by Z) but environment {} is {}x{}. They must match so they map 1:1: put red wool in the corners.", noFoliageName, maskSize[0], maskSize[2], environmentName, environmentSize.getX(), environmentSize.getZ());
            throw new RuntimeException("No-foliage file size differs from environment for chapter " + name);
        }
        noFoliageFileId = noFoliageId;
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
        nbt.putInt("baseHeight", chaptersBaseHeight);
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
        /* The base height is chosen from the ground at placement time and must not change afterwards. Saves without it keep the JSON baseElevation. */
        if (nbt.contains("baseHeight")) {
            chaptersBaseHeight = nbt.getInt("baseHeight");
        }
    }
}
