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
   A background thread streams the file through LandscapeNbtReader and hands blocks over in batches through a small bounded queue
   (so it waits when the server is slower); the server thread places a slice of them every tick, because the world may only be changed from
   the server thread. Entities are not loaded and integrity is ignored. */
public class StructureStreamLoadJob implements StructureStreamJobs.Job {

    private record Placement(int x, int y, int z, BlockState state, NbtCompound blockEntity) {
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
            LandscapeNbtReader.read(stream, (x, y, z, state, blockEntity) -> {
                batch.add(new Placement(x, y, z, state, blockEntity));
                if (batch.size() >= BATCH_SIZE) {
                    handOver(batch);
                    batch = new ArrayList<>(BATCH_SIZE);
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
            place(current.get(currentIndex++));
            if ((++placed & 255) == 0 && System.nanoTime() > deadlineNanos) {
                return false;
            }
        }
    }

    private void place(Placement placement) {
        if (placement.state().isOf(Blocks.STRUCTURE_VOID)) {
            return;
        }
        BlockPos pos = StructureTemplate.transformAround(new BlockPos(placement.x(), placement.y(), placement.z()), mirror, rotation, BlockPos.ORIGIN).add(origin);
        world.setBlockState(pos, placement.state().mirror(mirror).rotate(rotation), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);

        if (placement.blockEntity() != null) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity != null) {
                NbtCompound nbt = placement.blockEntity().copy();
                nbt.putInt("x", pos.getX());
                nbt.putInt("y", pos.getY());
                nbt.putInt("z", pos.getZ());
                blockEntity.readNbt(nbt);
                blockEntity.markDirty();
            }
        }
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
