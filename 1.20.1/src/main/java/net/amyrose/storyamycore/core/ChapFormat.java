package net.amyrose.storyamycore.core;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/* The .chap chapter file: a block volume stored as vertical spans per column, made for how chapters are read (per column, never per block).
   All numbers are big-endian.
   Header: magic int, version byte, size X/Y/Z ints, DataVersion int (informational), region size byte.
   Then one deflated block per region of REGION_SIZE x REGION_SIZE columns (regions ordered X outer, Z inner), each decodable on its own.
   Footer: palette, then per region its offset (long) and compressed length (int), then the footer offset as the last long. The footer comes last so a
   writer can stream regions without knowing the palette up front.
   Region (before deflate): for each column (local X outer, local Z inner) a var int span count and per span a var int gap from the end of the previous
   span, a var int length and a var int palette index (a vertical run of one block state; air is never stored). After the columns, the block entities of
   the region: a var int count, then per entry var int local x, var int y, var int local z and the block entity compound (NbtIo, the only NBT left). */
public class ChapFormat {

    public static final int MAGIC = 0x43485031;
    public static final int VERSION = 1;
    public static final int REGION_SIZE = 32;
    public static final int HEADER_SIZE = 4 + 1 + 12 + 4 + 1;

    public static int regionCount(int blocks) {
        return (blocks + REGION_SIZE - 1) / REGION_SIZE;
    }

    public static void writeVarInt(DataOutput out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    public static int readVarInt(DataInput in) throws IOException {
        int value = 0;
        int shift = 0;
        byte read;
        do {
            read = in.readByte();
            value |= (read & 0x7F) << shift;
            shift += 7;
        } while ((read & 0x80) != 0);
        return value;
    }

    /* A palette entry is the block id, then its properties as name/value strings. */
    public static void writePalette(DataOutput out, List<BlockState> palette) throws IOException {
        writeVarInt(out, palette.size());
        for (BlockState state : palette) {
            out.writeUTF(Registries.BLOCK.getId(state.getBlock()).toString());
            writeVarInt(out, state.getEntries().size());
            for (Map.Entry<Property<?>, Comparable<?>> entry : state.getEntries().entrySet()) {
                out.writeUTF(entry.getKey().getName());
                out.writeUTF(propertyValueName(entry.getKey(), entry.getValue()));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> String propertyValueName(Property<T> property, Comparable<?> value) {
        return property.name((T) value);
    }

    public static BlockState[] readPalette(DataInput in) throws IOException {
        BlockState[] palette = new BlockState[readVarInt(in)];
        for (int i = 0; i < palette.length; i++) {
            Identifier id = new Identifier(in.readUTF());
            if (!Registries.BLOCK.containsId(id)) {
                throw new IOException("Unknown block " + id + " in chapter file.");
            }
            Block block = Registries.BLOCK.get(id);
            BlockState state = block.getDefaultState();
            int properties = readVarInt(in);
            for (int p = 0; p < properties; p++) {
                String name = in.readUTF();
                String value = in.readUTF();
                Property<?> property = block.getStateManager().getProperty(name);
                if (property == null) {
                    throw new IOException("Block " + id + " has no property " + name + ".");
                }
                state = withProperty(state, property, value, id);
            }
            palette[i] = state;
        }
        return palette;
    }

    private static <T extends Comparable<T>> BlockState withProperty(BlockState state, Property<T> property, String value, Identifier id) throws IOException {
        T parsed = property.parse(value).orElse(null);
        if (parsed == null) {
            throw new IOException("Block " + id + " has no value " + value + " for property " + property.getName() + ".");
        }
        return state.with(property, parsed);
    }

    /* The position of a column in the order the file stores them (regions first, then columns inside the region), used to sort loose blocks. */
    public static long columnOrder(int x, int z, int regionsZ) {
        int region = (x / REGION_SIZE) * regionsZ + z / REGION_SIZE;
        return (long) region * REGION_SIZE * REGION_SIZE + (x % REGION_SIZE) * REGION_SIZE + z % REGION_SIZE;
    }
}
