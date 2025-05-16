package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import com.mojang.datafixers.DataFixer;
import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.SaveLoader;
import net.minecraft.server.WorldGenerationProgressListenerFactory;
import net.minecraft.structure.StructureTemplate;
import net.minecraft.util.ApiServices;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.net.Proxy;

@Mixin(MinecraftServer.class)
public class MinecraftServerMixin {
    @Inject(method = "<init>", at = @At("TAIL"))
    void initializeChapters(Thread serverThread, LevelStorage.Session session, ResourcePackManager dataPackManager, SaveLoader saveLoader, Proxy proxy, DataFixer dataFixer, ApiServices apiServices, WorldGenerationProgressListenerFactory worldGenerationProgressListenerFactory, CallbackInfo ci) {
        for (Chapter chapter : StoryEngine.chapters) {
            StructureTemplate landscapeTemplate = chapter.loadStructureTemplate((MinecraftServer)(Object)this, chapter.landscapeName);
            StructureTemplate environmentTemplate = chapter.loadStructureTemplate((MinecraftServer)(Object)this, chapter.environmentName);
            chapter.populateLandscapeMap(landscapeTemplate);
            chapter.populateEnvironmentMap(environmentTemplate);
        }
    }
}
