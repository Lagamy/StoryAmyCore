package net.amyrose.storyamycore.core;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.Resource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.WorldSavePath;
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

    /* Where the chapter block saves a chapter file. Running from the mod project (dev environment), that is the project's own
       src/main/resources/data/<namespace>/chapters, so the normal build packs it into the jar. Anywhere else it is the world's generated folder,
       because a built jar cannot be written to. */
    public static Path chapterPath(MinecraftServer server, Identifier name) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            Path project = FabricLoader.getInstance().getGameDir().toAbsolutePath().normalize().getParent();
            Path resources = project == null ? null : project.resolve("src/main/resources");
            if (resources != null && Files.isDirectory(resources)) {
                return resources.resolve("data").resolve(name.getNamespace()).resolve("chapters").resolve(name.getPath() + ".chap");
            }
        }
        return server.getSavePath(WorldSavePath.GENERATED).resolve(name.getNamespace()).resolve("chapters").resolve(name.getPath() + ".chap");
    }

    /* The saved file from chapterPath if it exists (so a chapter saved in dev loads without rebuilding), otherwise the chapter in the
       resources (mods and data packs). Returns null when the chapter does not exist. */
    public static InputStream open(ServerWorld world, Identifier name) throws IOException {
        Path saved = chapterPath(world.getServer(), name);
        if (Files.exists(saved)) {
            return Files.newInputStream(saved);
        }
        Optional<Resource> resource = world.getServer().getResourceManager().getResource(new Identifier(name.getNamespace(), "chapters/" + name.getPath() + ".chap"));
        return resource.isPresent() ? resource.get().getInputStream() : null;
    }
}
