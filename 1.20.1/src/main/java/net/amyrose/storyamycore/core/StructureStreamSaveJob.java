package net.amyrose.storyamycore.core;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.minecraft.SharedConstants;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPOutputStream;

/* Saves a region of any size to a vanilla structure .nbt without ever holding it in memory.
   Phase 1 (server thread, a slice per tick): every column of the region is read from the world and its blocks are appended to a temporary
   file as finished block compounds, while the palette grows in memory. A block only stores a palette index, so the indices stay valid.
   Phase 2 (background thread): the final gzip file is written as size, palette, then the temporary blocks copied over, so the palette
   comes before the blocks as LandscapeNbtReader needs. Air and structure voids are not saved. */
public class StructureStreamSaveJob implements StructureStreamJobs.Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);
    private static final long REPORT_INTERVAL_MILLIS = 5000L;

    private final ServerWorld world;
    private final BlockPos start;
    private final Vec3i size;
    private final Path target;
    private final Path blocksFile;
    private final Path finalTemp;
    private final DataOutputStream blocksOut;
    private final List<BlockState> palette = new ArrayList<>();
    private final Map<BlockState, Integer> paletteIndex = new HashMap<>();
    private int column;
    private int row;
    private int blockCount;
    private long lastReport = System.currentTimeMillis();
    private CompletableFuture<Void> assembly;

    public StructureStreamSaveJob(ServerWorld world, BlockPos start, Vec3i size, Identifier name) throws IOException {
        this.world = world;
        this.start = start;
        this.size = size;
        this.target = world.getStructureTemplateManager().getTemplatePath(name, ".nbt");
        this.blocksFile = target.resolveSibling(target.getFileName() + ".blocks.tmp");
        this.finalTemp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.createDirectories(target.getParent());
        this.blocksOut = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(blocksFile), 1 << 16));
        StructureStreamJobs.tell(world, "Saving " + name + " (" + size.getX() + "x" + size.getY() + "x" + size.getZ() + ") in the background...");
    }

    @Override
    public boolean tick(long deadlineNanos) {
        if (assembly != null) {
            return assemblyFinished();
        }
        try {
            while (column < size.getX()) {
                while (row < size.getZ()) {
                    if (System.nanoTime() > deadlineNanos) {
                        report();
                        return false;
                    }
                    saveColumn();
                    row++;
                }
                row = 0;
                column++;
            }
            blocksOut.close();
        } catch (IOException e) {
            return fail(e);
        }
        assembly = CompletableFuture.runAsync(this::assemble);
        return false;
    }

    private void saveColumn() throws IOException {
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = 0; y < size.getY(); y++) {
            pos.set(start.getX() + column, start.getY() + y, start.getZ() + row);
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID)) {
                continue;
            }

            int index = paletteIndex.computeIfAbsent(state, s -> {
                palette.add(s);
                return palette.size() - 1;
            });
            blocksOut.writeByte(NbtElement.LIST_TYPE);
            blocksOut.writeUTF("pos");
            blocksOut.writeByte(NbtElement.INT_TYPE);
            blocksOut.writeInt(3);
            blocksOut.writeInt(column);
            blocksOut.writeInt(y);
            blocksOut.writeInt(row);
            blocksOut.writeByte(NbtElement.INT_TYPE);
            blocksOut.writeUTF("state");
            blocksOut.writeInt(index);

            BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
            if (blockEntity != null) {
                NbtCompound nbt = blockEntity.createNbtWithId();
                nbt.remove("x");
                nbt.remove("y");
                nbt.remove("z");
                blocksOut.writeByte(NbtElement.COMPOUND_TYPE);
                blocksOut.writeUTF("nbt");
                nbt.write(blocksOut);
            }
            blocksOut.writeByte(NbtElement.END_TYPE);
            blockCount++;
        }
    }

    private void report() {
        long now = System.currentTimeMillis();
        if (now - lastReport >= REPORT_INTERVAL_MILLIS) {
            lastReport = now;
            StructureStreamJobs.tell(world, "Saving " + target.getFileName() + ": " + (column * 100 / Math.max(1, size.getX())) + "%");
        }
    }

    /* Background thread: only touches the temporary file, the palette and the block count, which the server thread no longer changes. */
    private void assemble() {
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(finalTemp), 1 << 16), 1 << 16));
             InputStream blocks = new BufferedInputStream(Files.newInputStream(blocksFile), 1 << 16)) {
            out.writeByte(NbtElement.COMPOUND_TYPE);
            out.writeUTF("");

            out.writeByte(NbtElement.LIST_TYPE);
            out.writeUTF("size");
            out.writeByte(NbtElement.INT_TYPE);
            out.writeInt(3);
            out.writeInt(size.getX());
            out.writeInt(size.getY());
            out.writeInt(size.getZ());

            out.writeByte(NbtElement.LIST_TYPE);
            out.writeUTF("entities");
            out.writeByte(NbtElement.END_TYPE);
            out.writeInt(0);

            out.writeByte(NbtElement.LIST_TYPE);
            out.writeUTF("palette");
            out.writeByte(NbtElement.COMPOUND_TYPE);
            out.writeInt(palette.size());
            for (BlockState state : palette) {
                NbtHelper.fromBlockState(state).write(out);
            }

            out.writeByte(NbtElement.LIST_TYPE);
            out.writeUTF("blocks");
            out.writeByte(NbtElement.COMPOUND_TYPE);
            out.writeInt(blockCount);
            blocks.transferTo(out);

            out.writeByte(NbtElement.INT_TYPE);
            out.writeUTF("DataVersion");
            out.writeInt(SharedConstants.getGameVersion().getSaveVersion().getId());
            out.writeByte(NbtElement.END_TYPE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean assemblyFinished() {
        if (!assembly.isDone()) {
            return false;
        }
        try {
            assembly.join();
            Files.move(finalTemp, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(blocksFile);
            StructureStreamJobs.tell(world, "Saved " + target.getFileName() + ": " + blockCount + " blocks.");
            return true;
        } catch (Exception e) {
            return fail(e);
        }
    }

    private boolean fail(Exception e) {
        LOGGER.error("Streaming structure save failed", e);
        StructureStreamJobs.tell(world, "Saving " + target.getFileName() + " failed: " + e.getMessage());
        try {
            blocksOut.close();
            Files.deleteIfExists(blocksFile);
            Files.deleteIfExists(finalTemp);
        } catch (IOException ignored) {
        }
        return true;
    }
}
