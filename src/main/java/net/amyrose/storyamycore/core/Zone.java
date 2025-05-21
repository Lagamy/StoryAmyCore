package net.amyrose.storyamycore.core;
import net.minecraft.nbt.NbtCompound;

public class Zone {
    public BlockPos2D beginning;
    public BlockPos2D end;

    public Zone(BlockPos2D beginning, BlockPos2D end){
        this.beginning = beginning;
        this.end = end;
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

    public boolean chunkInBound(int chunkX, int chunkZ) {
        int chunkEndX = chunkX + 15;
        int chunkEndZ = chunkZ + 15;

        return !(chunkEndX < beginning.x || chunkX > end.x || chunkEndZ < beginning.z || chunkZ > end.z);
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
}
