package net.amyrose.storyamycore.core;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;

import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;

import static com.mojang.text2speech.Narrator.LOGGER;

public class Zone {
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


    public boolean IsNotInForbiddenBiome(Set<String> forbiddenBiomes, int y, BiomeAccess biomeAccess) {
//        return (
//            !(forbiddenBiomes.contains(getBiomeKey(new BlockPos(beginning.x, y, beginning.z), biomeAccess)) ||
//            forbiddenBiomes.contains(getBiomeKey(new BlockPos(end.x, y, beginning.z), biomeAccess)) ||
//            forbiddenBiomes.contains(getBiomeKey(new BlockPos(beginning.x, y, end.z), biomeAccess)) ||
//            forbiddenBiomes.contains(getBiomeKey(new BlockPos(end.x, y, end.z), biomeAccess))));

        // Debug version:
        BlockPos t1 = new BlockPos(beginning.x, y, beginning.z);
        BlockPos t2 = new BlockPos(end.x, y, beginning.z);
        BlockPos t3 = new BlockPos(beginning.x, y, end.z);
        BlockPos t4 = new BlockPos(end.x, y, end.z);

        if(forbiddenBiomes.contains(getBiomeKey(t1, biomeAccess)))
        {
            LOGGER.info("forbidden biome was at beginning.x and beginning.z: {}", getBiomeKey(t1, biomeAccess));
            return false;
        }

        if(forbiddenBiomes.contains(getBiomeKey(t2, biomeAccess)))
        {
            LOGGER.info("forbidden biome was at end.x and beginning.z: {}", getBiomeKey(t2, biomeAccess));
            return false;
        }

        if(forbiddenBiomes.contains(getBiomeKey(t3, biomeAccess)))
        {
            LOGGER.info("forbidden biome was at beginning.x and end.z: {}", getBiomeKey(t3, biomeAccess));
            return false;
        }

        if(forbiddenBiomes.contains(getBiomeKey(t4, biomeAccess)))
        {
            LOGGER.info("forbidden biome was at end.x and end.z: {}", getBiomeKey(t4, biomeAccess));
            return false;
        }
        return true;
    }

    public String getBiomeKey(BlockPos pos, BiomeAccess biomeAccess) {
        return biomeAccess.getBiome(pos).getKey()
                .map(k -> k.getValue().toString())
                .orElse("unknown_biome");
    }
}
