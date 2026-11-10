package net.amyrose.storyamycore.core;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.zip.InflaterInputStream;

/* Reads a .chap file (see ChapFormat). The file is read into memory compressed (a few MB, and a jar resource cannot seek), then one region at a
   time is inflated, so only the span data of a single region is ever decoded at once. */
public class ChapReader {

    public interface Visitor {
        /* A vertical run of one block state in the column x, z (file coordinates), from y for length blocks. */
        void visitSpan(int x, int z, int y, int length, BlockState state);

        /* Block entity data (with its id, without x, y and z) of the block at x, y, z. Comes after the spans of its region. */
        default void visitBlockEntity(int x, int y, int z, NbtCompound nbt) {
        }
    }

    /* Reads only the header and returns the size X, Y, Z. */
    public static int[] readSize(InputStream stream) throws IOException {
        DataInputStream in = new DataInputStream(stream);
        checkHeader(in.readInt(), in.readByte());
        return new int[]{in.readInt(), in.readInt(), in.readInt()};
    }

    public static void read(InputStream stream, Visitor visitor) throws IOException {
        byte[] bytes = stream.readAllBytes();
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        checkHeader(buffer.getInt(), buffer.get());
        int sizeX = buffer.getInt();
        buffer.getInt();
        int sizeZ = buffer.getInt();
        buffer.getInt();
        int regionSize = buffer.get();

        buffer.position((int) buffer.getLong(bytes.length - 8));
        BlockState[] palette = ChapFormat.readPalette(new DataInputStream(new ByteBufferInputStream(buffer)));

        int regionsX = ChapFormat.regionCount(sizeX);
        int regionsZ = ChapFormat.regionCount(sizeZ);
        for (int regionX = 0; regionX < regionsX; regionX++) {
            for (int regionZ = 0; regionZ < regionsZ; regionZ++) {
                long offset = buffer.getLong();
                int length = buffer.getInt();
                int width = Math.min(regionSize, sizeX - regionX * regionSize);
                int depth = Math.min(regionSize, sizeZ - regionZ * regionSize);
                try (DataInputStream in = new DataInputStream(new BufferedInputStream(new InflaterInputStream(new ByteArrayInputStream(bytes, (int) offset, length)), 1 << 16))) {
                    readRegion(in, palette, visitor, regionX * regionSize, regionZ * regionSize, width, depth);
                }
            }
        }
    }

    private static void readRegion(DataInputStream in, BlockState[] palette, Visitor visitor, int baseX, int baseZ, int width, int depth) throws IOException {
        for (int localX = 0; localX < width; localX++) {
            for (int localZ = 0; localZ < depth; localZ++) {
                int spans = ChapFormat.readVarInt(in);
                int y = 0;
                for (int i = 0; i < spans; i++) {
                    y += ChapFormat.readVarInt(in);
                    int length = ChapFormat.readVarInt(in);
                    visitor.visitSpan(baseX + localX, baseZ + localZ, y, length, palette[ChapFormat.readVarInt(in)]);
                    y += length;
                }
            }
        }
        int blockEntities = ChapFormat.readVarInt(in);
        for (int i = 0; i < blockEntities; i++) {
            int localX = ChapFormat.readVarInt(in);
            int y = ChapFormat.readVarInt(in);
            int localZ = ChapFormat.readVarInt(in);
            visitor.visitBlockEntity(baseX + localX, y, baseZ + localZ, NbtIo.read(in));
        }
    }

    private static void checkHeader(int magic, int version) throws IOException {
        if (magic != ChapFormat.MAGIC) {
            throw new IOException("Not a chapter .chap file.");
        }
        if (version != ChapFormat.VERSION) {
            throw new IOException("Chapter file version " + version + " is not supported, expected " + ChapFormat.VERSION + ".");
        }
    }

    private static class ByteBufferInputStream extends InputStream {
        private final ByteBuffer buffer;

        ByteBufferInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public int read() {
            return buffer.hasRemaining() ? buffer.get() & 0xFF : -1;
        }

        @Override
        public int read(byte[] target, int offset, int length) {
            if (!buffer.hasRemaining()) {
                return -1;
            }
            int count = Math.min(length, buffer.remaining());
            buffer.get(target, offset, count);
            return count;
        }
    }
}
