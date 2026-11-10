package net.amyrose.storyamycore.core;

import net.minecraft.util.BlockRotation;

/* How a chapter's templates are turned when placed. RIGHT/LEFT are 90 degrees clockwise/counterclockwise, so they swap the chapter's X and Z size. */
public enum ChapterRotation {
    NONE(BlockRotation.NONE),
    RIGHT(BlockRotation.CLOCKWISE_90),
    LEFT(BlockRotation.COUNTERCLOCKWISE_90),
    BACK(BlockRotation.CLOCKWISE_180);

    public final BlockRotation blockRotation;

    ChapterRotation(BlockRotation blockRotation) {
        this.blockRotation = blockRotation;
    }

    public ChapterRotation opposite() {
        return switch (this) {
            case NONE -> BACK;
            case BACK -> NONE;
            case RIGHT -> LEFT;
            case LEFT -> RIGHT;
        };
    }

    public boolean swapsAxes() {
        return this == RIGHT || this == LEFT;
    }
}
