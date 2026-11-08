package net.amyrose.storyamycore.render.light;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.amyrose.storyamycore.render.sodium.SodiumLightBackend;
import net.amyrose.storyamycore.render.vanilla.VanillaLightBackend;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class LightManager {
    static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);

    static final List<Light> lights = new ArrayList<>();
    static ILightBackend backend;

    public static void addLight(Light light) {
        lights.add(light);
    }

    public static void removeLight(Light light) {
        lights.remove(light);
    }

    /* Client only. The backend is chosen once. SodiumLightBackend is only instantiated when Sodium is loaded, so its
       class never loads without it. WorldRenderEvents run on the render thread, which GL calls require. */
    public static void init() {
        if (FabricLoader.getInstance().isModLoaded("sodium")) {
            backend = new SodiumLightBackend();
            LOGGER.info("Light renderer: Sodium backend");
        } else {
            backend = new VanillaLightBackend();
            LOGGER.info("Light renderer: vanilla backend");
        }

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier(StoryAmyCoreMod.MOD_ID, "light_shaders");
            }

            @Override
            public void reload(ResourceManager manager) {
                backend.reload(manager);
            }
        });

        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (!lights.isEmpty()) {
                backend.render(lights, context);
            }
        });
    }
}
