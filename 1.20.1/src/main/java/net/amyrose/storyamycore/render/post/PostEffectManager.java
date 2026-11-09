package net.amyrose.storyamycore.render.post;

import com.google.gson.JsonSyntaxException;
import com.mojang.blaze3d.systems.RenderSystem;
import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.amyrose.storyamycore.render.RenderRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.PostEffectPass;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/* Screen post-processing. Each effect is a vanilla post effect json (the format of assets/minecraft/shaders/post/*.json) shipped by a mod.
   Effects run in registration order, over the finished world and hand and before the HUD, so the HUD is never distorted. Render thread only. */
public class PostEffectManager {
    static class Effect {
        Identifier json;
        boolean enabled;
        PostEffectProcessor processor;
        int width;
        int height;
        final Map<String, Float> uniforms = new HashMap<>();
    }

    static final Map<String, Effect> effects = new LinkedHashMap<>();

    /* Processors own GPU targets and are tied to the resources they were read from, so a resource reload (F3+T) drops them and
       render() builds them again on demand. */
    public static void init() {
        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public Identifier getFabricId() {
                return new Identifier(StoryAmyCoreMod.MOD_ID, "post_effects");
            }

            @Override
            public void reload(ResourceManager manager) {
                for (Effect effect : effects.values()) {
                    close(effect);
                }
            }
        });
    }

    public static void register(String modId, String id, Identifier json) {
        String key = RenderRegistry.key(modId, id);
        if (effects.containsKey(key)) {
            RenderRegistry.fail("Post effect " + key + " is already registered.");
        }
        Effect effect = new Effect();
        effect.json = json;
        effects.put(key, effect);
    }

    public static void setEnabled(String modId, String id, boolean enabled) {
        Effect effect = get(modId, id);
        effect.enabled = enabled;
        if (!enabled) {
            close(effect);
        }
    }

    /* Remembered here and applied to the processor every frame, so it survives the processor being rebuilt and can be set before the effect is first enabled. */
    public static void setUniform(String modId, String id, String uniform, float value) {
        get(modId, id).uniforms.put(uniform, value);
    }

    static Effect get(String modId, String id) {
        Effect effect = effects.get(RenderRegistry.key(modId, id));
        if (effect == null) {
            RenderRegistry.fail("Post effect " + RenderRegistry.key(modId, id) + " is not registered.");
        }
        return effect;
    }

    static void close(Effect effect) {
        if (effect.processor != null) {
            effect.processor.close();
            effect.processor = null;
        }
    }

    /* Called from GameRendererMixin. The state resets are the same ones vanilla does before its own post processor. */
    public static void render(float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.getWindow().getFramebufferWidth();
        int height = client.getWindow().getFramebufferHeight();
        for (Map.Entry<String, Effect> entry : effects.entrySet()) {
            Effect effect = entry.getValue();
            if (!effect.enabled) {
                continue;
            }
            if (effect.processor == null) {
                try {
                    effect.processor = new PostEffectProcessor(client.getTextureManager(), client.getResourceManager(), client.getFramebuffer(), effect.json);
                } catch (IOException | JsonSyntaxException e) {
                    effect.enabled = false;
                    RenderRegistry.fail("Could not load post effect " + entry.getKey() + " from " + effect.json + ": " + e);
                }
                effect.width = 0;
            }
            if (effect.width != width || effect.height != height) {
                effect.processor.setupDimensions(width, height);
                effect.width = width;
                effect.height = height;
            }
            /* Vanilla's own parseUniform does the same lookup: the uniform is null in passes whose program does not declare it. */
            for (Map.Entry<String, Float> uniform : effect.uniforms.entrySet()) {
                for (PostEffectPass pass : effect.processor.passes) {
                    GlUniform glUniform = pass.getProgram().getUniformByName(uniform.getKey());
                    if (glUniform != null) {
                        glUniform.set(uniform.getValue());
                    }
                }
            }
            RenderSystem.disableBlend();
            RenderSystem.disableDepthTest();
            RenderSystem.resetTextureMatrix();
            effect.processor.render(tickDelta);
        }
    }
}
