package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import com.mojang.datafixers.DataFixer;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.amyrose.storyamycore.core.StoryJson;
import net.amyrose.storyamycore.nbt.ServerDataManager;
import net.amyrose.storyamycore.nbt.worldDataNBT;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.SaveLoader;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.server.network.SpawnLocating;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.level.ServerWorldProperties;
import net.amyrose.storyamycore.core.BlockPos2D;
import net.minecraft.util.ApiServices;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.net.Proxy;

import static com.mojang.text2speech.Narrator.LOGGER;
import static net.amyrose.storyamycore.core.StoryEngine.storiesInitialized;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    private ServerDataManager serverDataManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    void initializeChapters(Thread serverThread, LevelStorage.Session session, ResourcePackManager dataPackManager, SaveLoader saveLoader, Proxy proxy, DataFixer dataFixer, ApiServices apiServices, WorldGenerationProgressListenerFactory worldGenerationProgressListenerFactory, CallbackInfo ci) {
        /* Stories are read by worldgen threads and matched against story_data.dat, so registration is only allowed during mod initialization. */
        StoryEngine.registrationClosed = true;
        StoryEngine.placementFailure = null;
        /* Chapters are built from the story JSON files here (not at registration), so hand-added chapters are included and edits are re-read on every world load. */
        for (Story story : StoryEngine.stories.values()) {
            StoryJson.loadStory(story);
        }
        File saveDir = session.getDirectory(WorldSavePath.ROOT).toFile(); // You may need to adjust to your Minecraft version
        MinecraftServer server = (MinecraftServer) (Object) this;
        serverDataManager = new ServerDataManager(server, saveDir);


        serverDataManager.load();

        for (Story story : StoryEngine.stories.values()) {
            //LOGGER.info("Populate Maps");
            for (Chapter chapter : story.chapters.values())
            {
                chapter.LoadBiome(server);
                chapter.PrepareLandscapeTiles();
                chapter.LoadEnvironmentTemplate(server);
                if(storiesInitialized && chapter.isPlaced())
                {
                    chapter.populateLandscapeMap(server);
                    chapter.populateEnvironmentMap(server);
                }
            }
        }
    }

    /* setupSpawn runs once per new world, on the server thread, after the overworld (and so the chapter placement) exists. Vanilla picks the spawn
       from the biome noise, which knows nothing about chapters, so a spawn that landed deep inside a chapter is moved just outside it. The spawn y comes
       from SpawnLocating (a safe surface in that chunk), falling back to the surface height. */
    @Inject(method = "setupSpawn", at = @At("TAIL"))
    private static void keepSpawnOutsideChapters(ServerWorld world, ServerWorldProperties worldProperties, boolean bonusChest, boolean debugWorld, CallbackInfo ci) {
        if (!StoryEngine.storiesInitialized) {
            return;
        }
        BlockPos spawn = world.getSpawnPos();
        BlockPos2D target = StoryEngine.findSpawnOutsideChapters(spawn.getX(), spawn.getZ());
        if (target == null) {
            return;
        }
        BlockPos safe = SpawnLocating.findServerSpawnPoint(world, new ChunkPos(new BlockPos(target.x, 0, target.z)));
        if (safe == null) {
            safe = new BlockPos(target.x, world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES, target.x, target.z), target.z);
        }
        LOGGER.info("World spawn {} was inside a chapter, moved to {}", spawn, safe);
        world.setSpawnPos(safe, 0.0F);
    }

    /* MinecraftServer.save runs on the server thread for autosave, /save-all and shutdown, so story_data.dat is written together with the world and a crash loses at most one autosave interval. */
    @Inject(method = "save", at = @At("HEAD"))
    private void onSave(boolean suppressLogs, boolean flush, boolean force, CallbackInfoReturnable<Boolean> cir) {
        if (serverDataManager != null) {
            serverDataManager.save();
        }
    }

    // Add a method to save, for example when server is stopping or periodically
    @Inject(method = "shutdown", at = @At("HEAD"))
    private void onShutdown(CallbackInfo ci) {
        if (serverDataManager != null) {
            serverDataManager.save();
        }
    }
}
