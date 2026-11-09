package net.amyrose.storyamycore.render.light;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.amyrose.storyamycore.render.NormalBuffer;
import net.amyrose.storyamycore.render.RenderRegistry;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class LightManager {
    static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);

    /* Everything here is touched from the render thread only: mods call the API from client code, and the render events run on it. */
    static final Map<String, Light> lights = new LinkedHashMap<>();
    static final List<Light> renderList = new ArrayList<>();
    static final Map<String, WorldShaderEntry> worldShaders = new LinkedHashMap<>();
    static ILightBackend backend;
    static ResourceManager resources;

    static class WorldShaderEntry {
        IWorldShader shader;
        boolean enabled = true;
    }

    public static void registerLight(String modId, String id, Light light) {
        String key = RenderRegistry.key(modId, id);
        if (lights.containsKey(key)) {
            RenderRegistry.fail("Light " + key + " is already registered.");
        }
        light.modId = modId;
        light.id = id;
        lights.put(key, light);
    }

    public static Light getLight(String modId, String id) {
        Light light = lights.get(RenderRegistry.key(modId, id));
        if (light == null) {
            RenderRegistry.fail("Light " + RenderRegistry.key(modId, id) + " is not registered.");
        }
        return light;
    }

    public static void setLightEnabled(String modId, String id, boolean enabled) {
        getLight(modId, id).enabled = enabled;
    }

    public static void removeLight(String modId, String id) {
        getLight(modId, id);
        lights.remove(RenderRegistry.key(modId, id));
    }

    public static void registerWorldShader(String modId, String id, IWorldShader shader) {
        String key = RenderRegistry.key(modId, id);
        if (worldShaders.containsKey(key)) {
            RenderRegistry.fail("World shader " + key + " is already registered.");
        }
        WorldShaderEntry entry = new WorldShaderEntry();
        entry.shader = shader;
        worldShaders.put(key, entry);
        if (resources != null) {
            shader.reload(resources);
        }
    }

    public static void setWorldShaderEnabled(String modId, String id, boolean enabled) {
        WorldShaderEntry entry = worldShaders.get(RenderRegistry.key(modId, id));
        if (entry == null) {
            RenderRegistry.fail("World shader " + RenderRegistry.key(modId, id) + " is not registered.");
        }
        entry.enabled = enabled;
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
                resources = manager;
                backend.reload(manager);
                for (WorldShaderEntry entry : worldShaders.values()) {
                    entry.shader.reload(manager);
                }
            }
        });

        WorldRenderEvents.START.register(context -> NormalBuffer.clear());

        /* Updaters run for every light, even disabled ones, so an updater can switch its own light on. The shared list is reused
           each frame to avoid allocating. */
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            renderList.clear();
            for (Light light : lights.values()) {
                if (light.updater != null) {
                    light.updater.accept(light, context);
                }
                if (light.enabled) {
                    renderList.add(light);
                }
            }
            if (!renderList.isEmpty()) {
                backend.render(renderList, context);
            }
            for (WorldShaderEntry entry : worldShaders.values()) {
                if (entry.enabled) {
                    entry.shader.render(context);
                }
            }
        });
    }
}
