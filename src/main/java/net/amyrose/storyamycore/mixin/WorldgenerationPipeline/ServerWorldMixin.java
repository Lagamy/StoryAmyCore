package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.WorldGenerationProgressListener;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.random.RandomSequencesState;
import net.minecraft.world.World;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.level.ServerWorldProperties;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import static net.amyrose.storyamycore.core.StoryEngine.stories;
import static net.amyrose.storyamycore.core.StoryEngine.storiesInitialized;

@Mixin(ServerWorld.class)
public class ServerWorldMixin {

    @Inject(method = "<init>", at = @At("TAIL"))
    private void InitializeServerWorld(MinecraftServer server, Executor workerExecutor, LevelStorage.Session session, ServerWorldProperties properties, RegistryKey worldKey, DimensionOptions dimensionOptions, WorldGenerationProgressListener worldGenerationProgressListener, boolean debugWorld, long seed, List spawners, boolean shouldTickTime, RandomSequencesState randomSequencesState, CallbackInfo ci) {
        if (worldKey == World.OVERWORLD) {
            // setChapterBiomes();
            if (!StoryEngine.storiesInitialized) {
                // Access BiomeSource from ChunkGenerator
                ServerWorld world = (ServerWorld)(Object)this;
                BiomeAccess biomeAccess = world.getBiomeAccess();

                Map<String, Story> initializedStories = new HashMap<>();
                for (Story story : stories.values())
                {
                    story.RandomizeStory(initializedStories, biomeAccess);
                    for (Chapter chapter : story.chapters.values())
                    {
                        chapter.populateLandscapeMap();
                        chapter.populateEnvironmentMap();
                    }
                }
                storiesInitialized = true;
            }
        }
    }

//    @Unique
//    void setChapterBiomes() {
//        ServerWorld overworld = (ServerWorld)(Object)this;
//
//        if (overworld != null) {
//            Registry<Biome> biomeRegistry = overworld.getRegistryManager().get(RegistryKeys.BIOME);
//            if (biomeRegistry != null) {
//                for(Story story : StoryEngine.stories.values()) {
//                    for (Chapter chapter : story.chapters.values()) {
//                        Optional<RegistryEntry.Reference<Biome>> biomeOptional = biomeRegistry.getEntry(
//                                RegistryKey.of(RegistryKeys.BIOME, new Identifier(chapter.biomeName))
//                        );
//
//                        if (biomeOptional.isEmpty()) {
//                            LOGGER.error("Non existent biome was passed to biomeName!");
//                            throw new RuntimeException("Critical error: Non existent biome was passed to biomeName"); // Crash the game
//                        } else {
//                            chapter.biome = biomeOptional.get();
//                            LOGGER.info("Biome entry loaded: {}", chapter.biome.getKey().orElse(null));
//                        }
//                    }
//                }
//            }
//            else{
//                LOGGER.error("Biome registry is null!");
//                throw new RuntimeException("Critical error: Biome registry is null!"); // Crash the game
//            }
//        }
//    }

}
