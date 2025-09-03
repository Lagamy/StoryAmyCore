package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;

import java.util.ArrayList;
import java.util.List;

public class PublicThreadMemory {

    private static final ThreadLocal<IntArrayList> storiesInThatChunk = ThreadLocal.withInitial(IntArrayList::new);
}
