package net.amyrose.storyamycore.core;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongList;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

/* Writes a .chap file (see ChapFormat) one region at a time, so a volume of any size is never in memory: only the region being built and the palette are.
   Call order: beginRegion, then for every column of the region (local X outer, local Z inner) addBlock for its blocks from the lowest Y up, then endColumn;
   addBlockEntity any time inside the region; endRegion. Regions come X outer, Z inner. finish writes the footer. */
public class ChapWriter implements Closeable {

    private final DataOutputStream out;
    private final List<BlockState> palette = new ArrayList<>();
    private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
    private final LongList regionOffsets = new LongArrayList();
    private final IntList regionLengths = new IntArrayList();
    private final IntList spans = new IntArrayList();
    private ByteArrayOutputStream regionBytes;
    private DataOutputStream region;
    private ByteArrayOutputStream blockEntityBytes;
    private DataOutputStream blockEntities;
    private int blockEntityCount;
    private long position = ChapFormat.HEADER_SIZE;
    private int previousEnd;
    private int runStart;
    private int runLength;
    private int runIndex;

    public ChapWriter(OutputStream stream, int sizeX, int sizeY, int sizeZ) throws IOException {
        out = new DataOutputStream(new BufferedOutputStream(stream, 1 << 16));
        out.writeInt(ChapFormat.MAGIC);
        out.writeByte(ChapFormat.VERSION);
        out.writeInt(sizeX);
        out.writeInt(sizeY);
        out.writeInt(sizeZ);
        out.writeInt(SharedConstants.getGameVersion().getSaveVersion().getId());
        out.writeByte(ChapFormat.REGION_SIZE);
    }

    public void beginRegion() {
        regionBytes = new ByteArrayOutputStream();
        region = new DataOutputStream(regionBytes);
        blockEntityBytes = new ByteArrayOutputStream();
        blockEntities = new DataOutputStream(blockEntityBytes);
        blockEntityCount = 0;
    }

    /* Blocks of one column must come with rising Y. A block that continues the run of the same state just makes the run longer. */
    public void addBlock(int y, BlockState state) {
        int index = paletteIndex.computeIfAbsent(state, s -> {
            palette.add(s);
            return palette.size() - 1;
        });
        if (runLength > 0 && index == runIndex && y == runStart + runLength) {
            runLength++;
            return;
        }
        flushRun();
        runStart = y;
        runLength = 1;
        runIndex = index;
    }

    private void flushRun() {
        if (runLength == 0) {
            return;
        }
        spans.add(runStart - previousEnd);
        spans.add(runLength);
        spans.add(runIndex);
        previousEnd = runStart + runLength;
        runLength = 0;
    }

    public void endColumn() throws IOException {
        flushRun();
        ChapFormat.writeVarInt(region, spans.size() / 3);
        for (int i = 0; i < spans.size(); i++) {
            ChapFormat.writeVarInt(region, spans.getInt(i));
        }
        spans.clear();
        previousEnd = 0;
    }

    /* The compound is the block entity data with its id and without x, y and z. */
    public void addBlockEntity(int localX, int y, int localZ, NbtCompound nbt) throws IOException {
        ChapFormat.writeVarInt(blockEntities, localX);
        ChapFormat.writeVarInt(blockEntities, y);
        ChapFormat.writeVarInt(blockEntities, localZ);
        NbtIo.write(nbt, blockEntities);
        blockEntityCount++;
    }

    public void endRegion() throws IOException {
        ChapFormat.writeVarInt(region, blockEntityCount);
        blockEntityBytes.writeTo(region);

        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            regionBytes.writeTo(deflater);
        }
        compressed.writeTo(out);
        regionOffsets.add(position);
        regionLengths.add(compressed.size());
        position += compressed.size();
        regionBytes = null;
        region = null;
    }

    public void finish() throws IOException {
        long footerOffset = position;
        ChapFormat.writePalette(out, palette);
        for (int i = 0; i < regionOffsets.size(); i++) {
            out.writeLong(regionOffsets.getLong(i));
            out.writeInt(regionLengths.getInt(i));
        }
        out.writeLong(footerOffset);
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
