package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import com.mojang.datafixers.DataFixer;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.amyrose.storyamycore.nbt.ServerDataManager;
import net.amyrose.storyamycore.nbt.worldDataNBT;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.SaveLoader;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.ApiServices;
import net.minecraft.util.WorldSavePath;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.net.Proxy;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    private ServerDataManager serverDataManager;

    @Inject(method = "<init>", at = @At("TAIL"))
    void initializeChapters(Thread serverThread, LevelStorage.Session session, ResourcePackManager dataPackManager, SaveLoader saveLoader, Proxy proxy, DataFixer dataFixer, ApiServices apiServices, WorldGenerationProgressListenerFactory worldGenerationProgressListenerFactory, CallbackInfo ci) {
        for (Story story : StoryEngine.stories.values()) {
            for (Chapter chapter : story.chapters.values()) {
                StructureTemplate landscapeTemplate = chapter.loadStructureTemplate((MinecraftServer) (Object) this, chapter.landscapeName, story.ModId);
                StructureTemplate environmentTemplate = chapter.loadStructureTemplate((MinecraftServer) (Object) this, chapter.environmentName, story.ModId);
                File saveDir = session.getDirectory(WorldSavePath.ROOT).toFile(); // You may need to adjust to your Minecraft version
                serverDataManager = new ServerDataManager((MinecraftServer) (Object) this, saveDir);
                serverDataManager.load();
                chapter.populateLandscapeMap(landscapeTemplate);
                chapter.populateEnvironmentMap(environmentTemplate);
            }
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
