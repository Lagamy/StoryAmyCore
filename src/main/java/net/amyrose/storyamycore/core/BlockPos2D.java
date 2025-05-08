package net.amyrose.storyamycore.core;

public class BlockPos2D {
    public int x;
    public int z;

    public BlockPos2D(int x, int z){
        this.x = x;
        this.z = z;
    }

    public void add(BlockPos2D addend){
        this.x += addend.x;
        this.z += addend.z;
    }
}
