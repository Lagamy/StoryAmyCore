package net.amyrose.storyamycore.core;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.BlockMirror;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/* Places a structure of any size without ever holding it in memory.
   A background thread reads the .chap file with ChapReader and hands vertical spans over in batches through a small bounded queue
   (so it waits when the server is slower); the server thread places a slice of them every tick, because the world may only be changed from
   the server thread. Entities are not loaded and integrity is ignored. */
public class StructureStreamLoadJob implements StructureStreamJobs.Job {

    /* A run of length blocks of one state going up from y, or, when length is 0, only the block entity data of the block at x, y, z. */
    private record Placement(int x, int y, int z, int length, BlockState state, NbtCompound blockEntity) {
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);
    private static final int BATCH_SIZE = 4096;
    private static final List<Placement> END = new ArrayList<>();

    private final ServerWorld world;
    private final BlockPos origin;
    private final BlockMirror mirror;
    private final BlockRotation rotation;
    private final String name;
    private final BlockingQueue<List<Placement>> queue = new ArrayBlockingQueue<>(8);
    private volatile IOException error;
    private List<Placement> batch = new ArrayList<>(BATCH_SIZE);
    private List<Placement> current;
    private int currentIndex;
    private int placed;

    public StructureStreamLoadJob(ServerWorld world, InputStream stream, BlockPos origin, BlockMirror mirror, BlockRotation rotation, Identifier name) {
        this.world = world;
        this.origin = origin;
        this.mirror = mirror;
        this.rotation = rotation;
        this.name = name.toString();
        Thread reader = new Thread(() -> readFile(stream), "Structure stream reader");
        reader.setDaemon(true);
        reader.start();
        StructureStreamJobs.tell(world, "Loading " + this.name + " in the background...");
    }

    private void readFile(InputStream stream) {
        try {
            ChapReader.read(stream, new ChapReader.Visitor() {
                @Override
                public void visitSpan(int x, int z, int y, int length, BlockState state) {
                    add(new Placement(x, y, z, length, state, null));
                }

                @Override
                public void visitBlockEntity(int x, int y, int z, NbtCompound nbt) {
                    add(new Placement(x, y, z, 0, null, nbt));
                }
            });
            handOver(batch);
        } catch (IOException e) {
            error = e;
        } catch (RuntimeException ignored) {
            return;
        }
        try {
            queue.put(END);
        } catch (InterruptedException ignored) {
        }
    }

    private void add(Placement placement) {
        batch.add(placement);
        if (batch.size() >= BATCH_SIZE) {
            handOver(batch);
            batch = new ArrayList<>(BATCH_SIZE);
        }
    }

    /* The only runtime exception here is the interruption of a waiting put, i.e. the server is shutting down. */
    private void handOver(List<Placement> blocks) {
        try {
            queue.put(blocks);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean tick(long deadlineNanos) {
        while (true) {
            if (current == null || currentIndex >= current.size()) {
                current = queue.poll();
                currentIndex = 0;
                if (current == null) {
                    return false;
                }
                if (current == END) {
                    return finish();
                }
            }
            Placement placement = current.get(currentIndex++);
            place(placement);
            if (System.nanoTime() > deadlineNanos) {
                return false;
            }
        }
    }

    /* The block entity of a span-less placement belongs to a block placed earlier: the reader emits a region's block entities after its spans. */
    private void place(Placement placement) {
        if (placement.length() == 0) {
            BlockPos pos = transform(placement.x(), placement.y(), placement.z());
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                NbtCompound nbt = placement.blockEntity().copy();
                nbt.putInt("x", pos.getX());
                nbt.putInt("y", pos.getY());
                nbt.putInt("z", pos.getZ());
                blockEntity.readNbt(nbt);
                blockEntity.markDirty();
            }
            return;
        }
        if (placement.state().isOf(Blocks.STRUCTURE_VOID)) {
            return;
        }
        BlockState state = placement.state().mirror(mirror).rotate(rotation);
        for (int y = placement.y(); y < placement.y() + placement.length(); y++) {
            world.setBlockState(transform(placement.x(), y, placement.z()), state, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
            placed++;
        }
    }

    private BlockPos transform(int x, int y, int z) {
        return StructureTemplate.transformAround(new BlockPos(x, y, z), mirror, rotation, BlockPos.ORIGIN).add(origin);
    }

    private boolean finish() {
        if (error != null) {
            LOGGER.error("Streaming structure load failed", error);
            StructureStreamJobs.tell(world, "Loading " + name + " failed: " + error.getMessage());
        } else {
            StructureStreamJobs.tell(world, "Loaded " + name + ": " + placed + " blocks.");
        }
        return true;
    }
}
