package net.amyrose.storyamycore.api;

import net.amyrose.storyamycore.render.entity.EntityShaderManager;
import net.amyrose.storyamycore.render.light.IWorldShader;
import net.amyrose.storyamycore.render.light.Light;
import net.amyrose.storyamycore.render.light.LightManager;
import net.amyrose.storyamycore.render.post.PostEffectManager;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;

import java.util.function.Predicate;
import java.util.function.Supplier;

/* The client-side counterpart of StoryAmyApi: the only class other mods should use for lights and shaders. Everything is addressed by
   (modId, id). Call it from your ClientModInitializer and from client code only; it must run on the render/client thread, and it never
   loads on a dedicated server. Registering twice or using an unknown id throws. Nothing here is synced over the network.
   Four kinds of effect:
   - lights: a spotlight with a cookie texture, or a point light (Light.point), optionally limited to chosen entities, items and block entities (Light.affect*). Mutate the Light you get back (public fields) to change any attribute, or set Light.updater to drive it every frame.
   - world shaders: your own full-screen pass in game space (IWorldShader), same depth/normal/albedo buffers the lights use.
   - entity shaders: swaps the shader program of every render layer of the entities your predicate matches.
   - post effects: a vanilla post effect json (assets/<modid>/shaders/post/<name>.json) run over the whole screen, under the HUD. */
@Environment(EnvType.CLIENT)
public final class StoryAmyClientApi {
    private StoryAmyClientApi() {}

    public static void registerLight(String modId, String id, Light light) {
        LightManager.registerLight(modId, id, light);
    }

    public static Light getLight(String modId, String id) {
        return LightManager.getLight(modId, id);
    }

    public static void setLightEnabled(String modId, String id, boolean enabled) {
        LightManager.setLightEnabled(modId, id, enabled);
    }

    public static void removeLight(String modId, String id) {
        LightManager.removeLight(modId, id);
    }

    public static void registerWorldShader(String modId, String id, IWorldShader shader) {
        LightManager.registerWorldShader(modId, id, shader);
    }

    public static void setWorldShaderEnabled(String modId, String id, boolean enabled) {
        LightManager.setWorldShaderEnabled(modId, id, enabled);
    }

    /* program: get it from CoreShaderRegistrationCallback (Fabric API), which loads shaders from your own namespace, e.g. assets/<modid>/shaders/core/<name>.json.
       receivesLights: true lets the cookie lights light these entities, but your shader must then write the two extra outputs
       (normalOut, albedoOut) like assets/minecraft/shaders/core/rendertype_entity_cutout.fsh. Uniforms are shared by the whole batch of entities drawn with the same layer. */
    public static void registerEntityShader(String modId, String id, Predicate<Entity> appliesTo, Supplier<ShaderProgram> program, boolean receivesLights) {
        EntityShaderManager.register(modId, id, appliesTo, program, receivesLights);
    }

    public static void setEntityShaderEnabled(String modId, String id, boolean enabled) {
        EntityShaderManager.setEnabled(modId, id, enabled);
    }

    public static void registerPostEffect(String modId, String id, Identifier postEffectJson) {
        PostEffectManager.register(modId, id, postEffectJson);
    }

    public static void setPostEffectEnabled(String modId, String id, boolean enabled) {
        PostEffectManager.setEnabled(modId, id, enabled);
    }

    /* Sets a float uniform by name in every pass of the effect that declares it, e.g. the strength of a fisheye. */
    public static void setPostUniform(String modId, String id, String uniform, float value) {
        PostEffectManager.setUniform(modId, id, uniform, value);
    }
}
