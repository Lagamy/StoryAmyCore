package net.amyrose.storyamycore.core;

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
}
