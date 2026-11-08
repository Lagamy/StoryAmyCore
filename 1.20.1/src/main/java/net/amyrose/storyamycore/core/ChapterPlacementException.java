package net.amyrose.storyamycore.core;

/* Thrown when a chapter has no suitable place in the world. It is caught while the world loads, so the player is
   disconnected with a message instead of the game crashing. */
public class ChapterPlacementException extends RuntimeException {
    public ChapterPlacementException(String message) {
        super(message);
    }
}
