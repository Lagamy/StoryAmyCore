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

    public void add(int x, int z){
        this.x += x;
        this.z += z;
    }

    public void setMin(BlockPos2D rightHand){
        this.x = Math.min(this.x, rightHand.x);
        this.z = Math.min(this.z, rightHand.z);
    }

    public void setMax(BlockPos2D rightHand){
        this.x = Math.max(this.x, rightHand.x);
        this.z = Math.max(this.z, rightHand.z);
    }

    public String toString() {
        return new String("x: " + x + " z: " + z);
    }
}
