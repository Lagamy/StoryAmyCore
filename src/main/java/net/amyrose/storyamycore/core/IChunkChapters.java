package net.amyrose.storyamycore.core;

import java.util.List;

public interface IChunkChapters {
    List<Chapter> getPresentChapters();
    void addChapter(Chapter chapter);
    void clearPresentChapters();
}
