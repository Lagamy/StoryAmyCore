package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;

/* One internal tile of a chapter's landscape file. A chapter that is not tiled has a single tile covering its whole size. */
public class LandscapeTile {
    public BlockPos2D offset; // from the chapter's corner, before rotation.
    public Zone area; // in world coordinates (rotation applied), both ends inclusive. Set when the landscape map is populated.
    public Long2IntOpenHashMap landscapeMap = new Long2IntOpenHashMap(); // highest solid (non-water) block Y per world column of this tile only.
    public Long2ObjectOpenHashMap<BlockState> surfaceMap = new Long2ObjectOpenHashMap<>(); // the solid block at landscapeMap's Y, per world column.
    public Long2IntOpenHashMap waterMap = new Long2IntOpenHashMap(); // water surface Y, only for columns where water lies above the solid surface.
    public Long2ObjectOpenHashMap<BlockState> environmentMap = new Long2ObjectOpenHashMap<>(); // non-air environment blocks of this tile's columns, keyed by world x, y, z.
    public Long2ObjectOpenHashMap<NbtCompound> environmentBlockEntityNbt = new Long2ObjectOpenHashMap<>(); // block entity data of those blocks, same key.

    public LandscapeTile(BlockPos2D offset) {
        this.offset = offset;
    }
}
