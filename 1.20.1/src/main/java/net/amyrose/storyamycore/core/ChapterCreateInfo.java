package net.amyrose.storyamycore.core;

import net.minecraft.block.BlockState;

import java.util.HashSet;
import java.util.Set;

/* Everything StoryAmyApi.addFirstChapter / addChapter needs to describe a chapter, filled in field by field (like a Vulkan create-info struct):
   set the required fields, set an optional field only when the chapter needs it, and leave the rest alone, because every optional field starts at its default.

       ChapterCreateInfo info = new ChapterCreateInfo();
       info.sizeX = 281;
       info.sizeZ = 188;
       info.landscapeName = "prologue-landscape";
       info.landscapeBlockState = Blocks.GRASS_BLOCK.getDefaultState();
       info.environmentName = "prologue-environment";
       info.allowedBiomes = Set.of("minecraft:forest");
       info.ringRadius = 128;
       StoryAmyApi.addFirstChapter(MOD_ID, "story", "prologue", info);

   The values are written to the story's JSON file only when its chapter entry is missing (see StoryAmyApi), and only the optional fields that differ from their default. */
public class ChapterCreateInfo {

    /* Required. */
    public int sizeX;
    public int sizeZ;
    public String landscapeName;
    public BlockState landscapeBlockState;
    public String environmentName;
    public Set<String> allowedBiomes;

    /* Required for every chapter but the first (the offset from the end of the previous chapter); must stay null for the first one. */
    public BlockPos2D offsetFromPrevious;

    /* Optional, with the default each one starts at. */
    public Set<BlockState> forbiddenLandscapeBlocks = new HashSet<>(); // landscape surface blocks the chapter may not take from the terrain.
    public int baseElevation = 64; // only used for the biome check at placement.
    public BlockPos2D landscapeTileSize; // null = the landscape is one tile; otherwise it must divide the size evenly.
    public String biome; // null = no biome override; otherwise e.g. "minecraft:forest".
    public double minBiomeShare = 1.0; // 1.0 = every sampled point must be in an allowed biome; above 0 and at most 1.
    public int ringRadius = StoryEngine.ringRadius; // how many blocks the blend ring around the landscape spans.
    public boolean litterGrass = false; // true = ground cover (grass, flower and mushroom patches) is still scattered inside the no-features mask; false = nothing is.
    public boolean adaptiveRing = false; // true = the ring may grow beyond ringRadius where the terrain is far from the landscape's edge.
    public double steepnessAdaptationFactor = 1.0; // only used with adaptiveRing: ring blocks per block of worst height difference (the ring needed is ceil(worst difference x this), never below ringRadius), above 0.
}
