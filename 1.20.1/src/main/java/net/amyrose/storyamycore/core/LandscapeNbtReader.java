package net.amyrose.storyamycore.core;

import net.minecraft.block.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtTagSizeTracker;
import net.minecraft.nbt.NbtTypes;
import net.minecraft.registry.Registries;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

/* Streaming reader for a vanilla structure .nbt file, made for landscapes that are too big to load as a StructureTemplate.
   NbtIo.readCompressed builds the whole tag tree and StructureTemplate keeps every block, so a big landscape would sit in memory
   as millions of objects. This reader walks the gzip stream tag by tag and hands each block to a visitor, then forgets it. */
public class LandscapeNbtReader {

    public interface BlockVisitor {
        void visit(int x, int y, int z, BlockState state);
    }

    /* Same as BlockVisitor, plus the block entity data of the block (null when the block has none). */
    public interface BlockEntityVisitor {
        void visit(int x, int y, int z, BlockState state, NbtCompound blockEntity);
    }

    public static void read(InputStream stream, BlockVisitor visitor) throws IOException {
        read(stream, (x, y, z, state, blockEntity) -> visitor.visit(x, y, z, state), false);
    }

    public static void read(InputStream stream, BlockEntityVisitor visitor) throws IOException {
        read(stream, visitor, true);
    }

    /* Reads only as far as the blocks and returns the structure size, or null when the file cannot be streamed: the size and the palette
       must both come before the blocks, because nothing is buffered. Files written by the chapter block and the generator are in that order;
       vanilla files often are not, so the caller falls back to the vanilla loader for those. */
    public static int[] readStreamableSize(InputStream stream) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream), 1 << 16))) {
            if (in.readByte() != NbtElement.COMPOUND_TYPE) {
                return null;
            }
            in.readUTF();

            int[] size = null;
            boolean paletteSeen = false;
            byte type;
            while ((type = in.readByte()) != NbtElement.END_TYPE) {
                String key = in.readUTF();
                if (key.equals("size")) {
                    in.readByte();
                    in.readInt();
                    size = new int[]{in.readInt(), in.readInt(), in.readInt()};
                } else if (key.equals("blocks")) {
                    return paletteSeen ? size : null;
                } else {
                    paletteSeen |= key.equals("palette");
                    NbtTypes.byId(type).skip(in);
                }
            }
            return null;
        }
    }

    /* The palette must come before the blocks in the file, because a block only stores a palette index and nothing is buffered.
       Files written by the generator have it in that order; a file that does not fails loudly. */
    private static void read(InputStream stream, BlockEntityVisitor visitor, boolean withBlockEntities) throws IOException {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(stream), 1 << 16))) {
            if (in.readByte() != NbtElement.COMPOUND_TYPE) {
                throw new IOException("Landscape file root is not a compound.");
            }
            in.readUTF();

            BlockState[] palette = null;
            byte type;
            while ((type = in.readByte()) != NbtElement.END_TYPE) {
                String key = in.readUTF();
                if (key.equals("palette")) {
                    palette = readPalette(in);
                } else if (key.equals("blocks")) {
                    if (palette == null) {
                        throw new IOException("Landscape file has its blocks before its palette.");
                    }
                    readBlocks(in, palette, visitor, withBlockEntities);
                } else {
                    NbtTypes.byId(type).skip(in);
                }
            }
        }
    }

    private static BlockState[] readPalette(DataInputStream in) throws IOException {
        in.readByte();
        BlockState[] palette = new BlockState[in.readInt()];
        for (int i = 0; i < palette.length; i++) {
            NbtCompound entry = (NbtCompound) NbtTypes.byId(NbtElement.COMPOUND_TYPE).read(in, 0, NbtTagSizeTracker.EMPTY);
            palette[i] = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), entry);
        }
        return palette;
    }

    private static void readBlocks(DataInputStream in, BlockState[] palette, BlockEntityVisitor visitor, boolean withBlockEntities) throws IOException {
        in.readByte();
        int count = in.readInt();
        for (int i = 0; i < count; i++) {
            int x = 0;
            int y = 0;
            int z = 0;
            int state = 0;
            NbtCompound blockEntity = null;
            byte type;
            while ((type = in.readByte()) != NbtElement.END_TYPE) {
                String key = in.readUTF();
                if (key.equals("pos")) {
                    in.readByte();
                    in.readInt();
                    x = in.readInt();
                    y = in.readInt();
                    z = in.readInt();
                } else if (key.equals("state")) {
                    state = in.readInt();
                } else if (key.equals("nbt") && withBlockEntities) {
                    blockEntity = (NbtCompound) NbtTypes.byId(NbtElement.COMPOUND_TYPE).read(in, 0, NbtTagSizeTracker.EMPTY);
                } else {
                    NbtTypes.byId(type).skip(in);
                }
            }
            visitor.visit(x, y, z, palette[state], blockEntity);
        }
    }
}
