package net.amyrose.storyamycore.render.light;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.amyrose.storyamycore.render.NormalBuffer;
import net.amyrose.storyamycore.render.ReceiverMask;
import net.amyrose.storyamycore.render.RenderRegistry;
import net.amyrose.storyamycore.render.sodium.SodiumLightBackend;
import net.amyrose.storyamycore.render.vanilla.VanillaLightBackend;
import net.fabricmc.api.EnvType;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import org.joml.Matrix4f;
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
    static final List<Light> handList = new ArrayList<>();
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

    /* True when an enabled light with receivers matches the model: the model is then drawn a second time into the receiver mask
       (see ReceiverMask). Called for every drawn entity, item and block entity, so it only loops over the few registered lights. */
    public static boolean receivesEntity(Entity entity) {
        for (Light light : lights.values()) {
            if (light.enabled && light.entityReceivers != null && light.entityReceivers.test(entity)) {
                return true;
            }
        }
        return false;
    }

    public static boolean receivesItem(ItemStack stack) {
        for (Light light : lights.values()) {
            if (light.enabled && light.itemReceivers != null && light.itemReceivers.test(stack)) {
                return true;
            }
        }
        return false;
    }

    public static boolean receivesBlockEntity(BlockEntity blockEntity) {
        for (Light light : lights.values()) {
            if (light.enabled && light.blockEntityReceivers != null && light.blockEntityReceivers.test(blockEntity)) {
                return true;
            }
        }
        return false;
    }

    /* Rule: the first-person hand is drawn by GameRenderer after the whole world (after AFTER_TRANSLUCENT), with its own projection
       and a cleared depth buffer, so the world pass never sees it. This second pass, run right after the hand is drawn, draws only
       the lights with receivers, which is how a held item gets lit. The lights were already updated this frame. */
    public static void renderHand(Camera camera, Matrix4f projection) {
        handList.clear();
        for (Light light : lights.values()) {
            if (light.enabled && light.hasReceivers()) {
                handList.add(light);
            }
        }
        if (backend != null && !handList.isEmpty()) {
            backend.renderHand(handList, camera, projection);
        }
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
                ReceiverMask.reload(manager);
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
