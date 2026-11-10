package net.amyrose.storyamycore.core;

import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

/* Runs the chapter block's streaming saves and loads a slice of every server tick, so a structure of any size never freezes the server
   (the watchdog stops a server whose tick takes over 60 s). Jobs are only touched from the server thread. */
public class StructureStreamJobs {

    public interface Job {
        /* Works until the deadline (System.nanoTime) and returns true when the job is finished. */
        boolean tick(long deadlineNanos);
    }

    private static final long TICK_BUDGET_NANOS = 25_000_000L;
    private static final List<Job> jobs = new ArrayList<>();

    public static void add(Job job) {
        jobs.add(job);
    }

    public static void clear() {
        jobs.clear();
    }

    public static void tick(MinecraftServer server) {
        if (jobs.isEmpty()) {
            return;
        }
        long deadline = System.nanoTime() + TICK_BUDGET_NANOS;
        Iterator<Job> iterator = jobs.iterator();
        while (iterator.hasNext() && System.nanoTime() < deadline) {
            if (iterator.next().tick(deadline)) {
                iterator.remove();
            }
        }
    }

    public static void tell(ServerWorld world, String message) {
        world.getServer().getPlayerManager().broadcast(Text.literal(message), false);
    }

    /* Saved structures live in the world's generated folder; anything else is looked up in the resources (data packs and mods),
       the same two places the vanilla structure block reads from. Returns null when the structure does not exist. */
    public static InputStream open(ServerWorld world, Identifier name) throws IOException {
        Path generated = world.getStructureTemplateManager().getTemplatePath(name, ".nbt");
        if (Files.exists(generated)) {
            return Files.newInputStream(generated);
        }
        Optional<Resource> resource = world.getServer().getResourceManager().getResource(new Identifier(name.getNamespace(), "structures/" + name.getPath() + ".nbt"));
        return resource.isPresent() ? resource.get().getInputStream() : null;
    }
}
