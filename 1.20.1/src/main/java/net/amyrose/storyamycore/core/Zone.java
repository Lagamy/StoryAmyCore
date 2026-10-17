package net.amyrose.storyamycore.core;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.source.BiomeAccess;

import java.util.Set;

public class Zone {
    static final int BIOME_SAMPLE_STEP = 16;

    public BlockPos2D beginning;
    public BlockPos2D end;

    public Zone(BlockPos2D beginning, BlockPos2D end){
        this.beginning = new BlockPos2D(beginning.x, beginning.z);
        this.end = new BlockPos2D(end.x, end.z);
    }

    public Zone(int beginningX, int beginningZ, int endX, int endZ){
        this.beginning = new BlockPos2D(beginningX, beginningZ);
        this.end = new BlockPos2D(endX, endZ);
    }

    public Zone(int xBeginning, int zBeginning){ // To add end later on
        this.beginning = new BlockPos2D(xBeginning, zBeginning);
    }

    public Zone() {}

    public boolean inBounds(int x, int z) {
        return x >= beginning.x && x <= end.x && z >= beginning.z && z <= end.z;
    }


    public boolean inXBound(int x){
        return x >= beginning.x && x <= end.x;
    }

    public boolean inZBound(int z){
        return z >= beginning.z && z <= end.z;
    }

    /* Takes the chunk's first BLOCK coordinates (ChunkPos.getStartX()/getStartZ()), not chunk indices (ChunkPos.x/z). */
    public boolean chunkInBound(int chunkStartX, int chunkStartZ) {
        int chunkEndX = chunkStartX + 15;
        int chunkEndZ = chunkStartZ + 15;

        return !(chunkEndX < beginning.x || chunkStartX > end.x || chunkEndZ < beginning.z || chunkStartZ > end.z);
    }

    public NbtCompound SaveToNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putLong("beginning", beginning.toLong());
        nbt.putLong("end", end.toLong());
        return nbt;
    }

    public static Zone LoadFromNbt(NbtCompound nbt) {
        Zone zone = new Zone();
        zone.beginning = BlockPos2D.fromLong(nbt.getLong("beginning"));
        zone.end = BlockPos2D.fromLong(nbt.getLong("end"));
        return zone;
    }

    public boolean CheckCollision(Zone anotherZone) { // true = Collision. false = No Collision
        return !(anotherZone.end.x < this.beginning.x ||   // another is left of this
                anotherZone.beginning.x > this.end.x ||   // another is right of this
                anotherZone.end.z < this.beginning.z ||   // another is above this
                anotherZone.beginning.z > this.end.z);    // another is below this
    }

    public boolean CheckCollision(int beginningX, int beginningZ, int endX, int endZ) { // true = Collision. false = No Collision
        return !(endX < this.beginning.x ||   // another is left of this
                beginningX > this.end.x ||   // another is right of this
                endZ < this.beginning.z ||   // another is above this
                beginningZ > this.end.z);    // another is below this
    }


    public boolean IsInAllowedBiome(Set<String> allowedBiomes, int y, BiomeAccess biomeAccess) {
        /* Biomes are sampled on a grid with BIOME_SAMPLE_STEP spacing from beginning to end, and the end row/column is always sampled,
           so a forbidden biome in the middle of a large zone is found too, not only at the corners and the center.
           The position is a local Mutable (not a field) and the first forbidden biome ends the check early. */
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = beginning.x; ; x = Math.min(x + BIOME_SAMPLE_STEP, end.x)) {
            for (int z = beginning.z; ; z = Math.min(z + BIOME_SAMPLE_STEP, end.z)) {
                pos.set(x, y, z);
                if (!allowedBiomes.contains(getBiomeKey(pos, biomeAccess))) {
                    return false;
                }
                if (z == end.z) {
                    break;
                }
            }
            if (x == end.x) {
                break;
            }
        }
        return true;
    }

    public String getBiomeKey(BlockPos pos, BiomeAccess biomeAccess) {
        return biomeAccess.getBiome(pos).getKey()
                .map(k -> k.getValue().toString())
                .orElse("unknown_biome");
    }
}
