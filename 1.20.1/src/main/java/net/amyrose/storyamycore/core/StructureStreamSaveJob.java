package net.amyrose.storyamycore.core;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/* Saves a region of any size to a .chap file without ever holding it in memory.
   The region is read from the world on the server thread, a slice of columns per tick, and handed to a ChapWriter region by region: a region is
   32 x 32 columns, so only that many columns of spans are in memory, plus the palette. A region is compressed when it is complete, which is a few
   milliseconds. The file is written under a temporary name and moved into place at the end, so a failed save never replaces a good file.
   Air and structure voids are not saved. */
public class StructureStreamSaveJob implements StructureStreamJobs.Job {

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);
    private static final long REPORT_INTERVAL_MILLIS = 5000L;

    private final ServerWorld world;
    private final BlockPos start;
    private final Vec3i size;
    private final Path target;
    private final Path temp;
    private final ChapWriter writer;
    private final int regionsX;
    private final int regionsZ;
    private int regionX;
    private int regionZ;
    private int localX;
    private int localZ;
    private boolean regionOpen;
    private int blockCount;
    private long lastReport = System.currentTimeMillis();

    public StructureStreamSaveJob(ServerWorld world, BlockPos start, Vec3i size, Identifier name) throws IOException {
        this.world = world;
        this.start = start;
        this.size = size;
        this.target = StructureStreamJobs.chapterPath(world.getServer(), name);
        this.temp = target.resolveSibling(target.getFileName() + ".tmp");
        this.regionsX = ChapFormat.regionCount(size.getX());
        this.regionsZ = ChapFormat.regionCount(size.getZ());
        Files.createDirectories(target.getParent());
        this.writer = new ChapWriter(Files.newOutputStream(temp), size.getX(), size.getY(), size.getZ());
        StructureStreamJobs.tell(world, "Saving " + name + " (" + size.getX() + "x" + size.getY() + "x" + size.getZ() + ")...");
    }

    @Override
    public boolean tick(long deadlineNanos) {
        try {
            while (regionX < regionsX) {
                while (regionZ < regionsZ) {
                    if (!regionOpen) {
                        writer.beginRegion();
                        regionOpen = true;
                    }
                    int width = Math.min(ChapFormat.REGION_SIZE, size.getX() - regionX * ChapFormat.REGION_SIZE);
                    int depth = Math.min(ChapFormat.REGION_SIZE, size.getZ() - regionZ * ChapFormat.REGION_SIZE);
                    while (localX < width) {
                        while (localZ < depth) {
                            if (System.nanoTime() > deadlineNanos) {
                                report();
                                return false;
                            }
                            saveColumn();
                            localZ++;
                        }
                        localZ = 0;
                        localX++;
                    }
                    writer.endRegion();
                    regionOpen = false;
                    localX = 0;
                    regionZ++;
                }
                regionZ = 0;
                regionX++;
            }
            writer.finish();
            writer.close();
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            StructureStreamJobs.tell(world, "Saved " + target.getFileName() + ": " + blockCount + " blocks.");
            return true;
        } catch (IOException e) {
            return fail(e);
        }
    }

    private void saveColumn() throws IOException {
        int column = regionX * ChapFormat.REGION_SIZE + localX;
        int row = regionZ * ChapFormat.REGION_SIZE + localZ;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int y = 0; y < size.getY(); y++) {
            pos.set(start.getX() + column, start.getY() + y, start.getZ() + row);
            BlockState state = world.getBlockState(pos);
            if (state.isAir() || state.isOf(Blocks.STRUCTURE_VOID)) {
                continue;
            }
            writer.addBlock(y, state);
            blockCount++;

            BlockEntity blockEntity = state.hasBlockEntity() ? world.getBlockEntity(pos) : null;
            if (blockEntity != null) {
                NbtCompound nbt = blockEntity.createNbtWithId();
                nbt.remove("x");
                nbt.remove("y");
                nbt.remove("z");
                writer.addBlockEntity(localX, y, localZ, nbt);
            }
        }
        writer.endColumn();
    }

    private void report() {
        long now = System.currentTimeMillis();
        if (now - lastReport >= REPORT_INTERVAL_MILLIS) {
            lastReport = now;
            StructureStreamJobs.tell(world, "Saving " + target.getFileName() + ": " + (regionX * 100 / Math.max(1, regionsX)) + "%");
        }
    }

    private boolean fail(Exception e) {
        LOGGER.error("Streaming structure save failed", e);
        StructureStreamJobs.tell(world, "Saving " + target.getFileName() + " failed: " + e.getMessage());
        try {
            writer.close();
            Files.deleteIfExists(temp);
        } catch (IOException ignored) {
        }
        return true;
    }
}
