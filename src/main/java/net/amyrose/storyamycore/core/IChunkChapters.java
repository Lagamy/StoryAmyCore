package net.amyrose.storyamycore.core;

import java.util.List;

public interface IChunkChapters {
    boolean isOverworld(); // to avoid any dimensions.(Biome based)
    List<Chapter> getPresentChapters();
    void addChapter(Chapter chapter);
    void clearPresentChapters();
    boolean isChaptersPresentEmpty();
    int getSize();
}
