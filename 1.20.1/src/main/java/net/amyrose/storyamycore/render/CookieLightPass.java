package net.amyrose.storyamycore.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.amyrose.storyamycore.render.light.Light;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceFactory;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

public class CookieLightPass {
    static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);

    ShaderProgram program;
    Framebuffer sceneCopy;
    static final String[] SETTINGS = {"Fov", "Range", "Intensity", "Contrast", "LightColor"};
    final Map<String, float[]> jsonDefaults = new HashMap<>();

    public void reload(ResourceFactory resourceFactory) {
        if (program != null) {
            program.close();
        }
        try {
            program = new ShaderProgram(resourceFactory, "storyamycore_cookie_light", VertexFormats.POSITION);
        } catch (IOException e) {
            LOGGER.error("Failed to load cookie light shader", e);
            throw new RuntimeException("Failed to load cookie light shader", e);
        }

        /* The json values are copied right after loading, before any light writes into the uniforms. */
        jsonDefaults.clear();
        for (String name : SETTINGS) {
            GlUniform uniform = program.getUniform(name);
            FloatBuffer data = uniform.getFloatData();
            float[] values = new float[uniform.getCount()];
            for (int i = 0; i < values.length; i++) {
                values[i] = data.get(i);
            }
            jsonDefaults.put(name, values);
        }
    }

    /* The program is shared by all lights and keeps the last value written. A light that leaves a setting null (or the next frame)
       must get the json value back, not what the previous light or frame wrote. */
    void applySetting(String name, float... lightValues) {
        float[] values = lightValues != null ? lightValues : jsonDefaults.get(name);
        GlUniform uniform = program.getUniform(name);
        if (values.length == 1) {
            uniform.set(values[0]);
        } else {
            uniform.set(values[0], values[1], values[2]);
        }
    }

    /* Rule: the light is rebuilt from the depth buffer. Screen depth is turned back into a view-space position with the inverse
       camera projection, moved into the light's own space, and projected with the light's perspective to get cookie UVs.
       Colour and depth are blitted into a scratch framebuffer and sampled from there, so the texture being read is never also
       attached to the framebuffer being drawn to (that read/write loop is undefined in OpenGL). */
    public void draw(Light light, WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        Framebuffer framebuffer = client.getFramebuffer();
        int width = framebuffer.textureWidth;
        int height = framebuffer.textureHeight;

        if (sceneCopy == null || sceneCopy.textureWidth != width || sceneCopy.textureHeight != height) {
            if (sceneCopy != null) {
                sceneCopy.delete();
            }
            sceneCopy = new SimpleFramebuffer(width, height, true, MinecraftClient.IS_SYSTEM_MAC);
            sceneCopy.setTexFilter(GL11.GL_NEAREST);
        }
        /* Both buffers are copied in one blit (colour and depth sizes and formats match). It leaves the copy bound for drawing,
           so the main framebuffer is bound again afterwards. */
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, framebuffer.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.fbo);
        GlStateManager._glBlitFrameBuffer(0, 0, width, height, 0, 0, width, height, GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, GL11.GL_NEAREST);
        framebuffer.beginWrite(true);

        /* The world position matrix is camera-relative and contains view bobbing and hurt tilt. It is undone here, and the light is
           placed in camera-relative world space instead, so a light attached to the camera does not wobble with the bob. The light
           orientation uses the same pitch/yaw rotation GameRenderer applies before bobbing. */
        Vec3d cameraPosition = context.camera().getPos();
        Vec3d lightPosition = light.followCamera ? cameraPosition : light.position;
        float lightYaw = light.followCamera ? context.camera().getYaw() : light.yaw;
        float lightPitch = light.followCamera ? context.camera().getPitch() : light.pitch;
        Vec3d offset = lightPosition.subtract(cameraPosition);

        Matrix4f inverseProjection = new Matrix4f(context.projectionMatrix()).invert();
        Matrix4f worldToLight = new Matrix4f()
                .rotateX((float) Math.toRadians(lightPitch))
                .rotateY((float) Math.toRadians(lightYaw + 180.0f))
                .translate((float) -offset.x, (float) -offset.y, (float) -offset.z);
        Matrix4f viewToLight = worldToLight.mul(new Matrix4f(context.matrixStack().peek().getPositionMatrix()).invert());

        client.getTextureManager().bindTexture(light.cookie);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        RenderSystem.texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        program.addSampler("DepthSampler", sceneCopy.getDepthAttachment());
        program.addSampler("SceneSampler", sceneCopy.getColorAttachment());
        program.addSampler("CookieSampler", client.getTextureManager().getTexture(light.cookie).getGlId());
        program.getUniform("InvProjMat").set(inverseProjection);
        program.getUniform("ViewToLightMat").set(viewToLight);
        /* A null setting on the light uses the value written in the shader json. */
        applySetting("Fov", light.fovDegrees == null ? null : new float[]{light.fovDegrees});
        applySetting("Range", light.range == null ? null : new float[]{light.range});
        applySetting("Intensity", light.intensity == null ? null : new float[]{light.intensity});
        applySetting("Contrast", light.contrast == null ? null : new float[]{light.contrast});
        applySetting("LightColor", light.color == null ? null : new float[]{light.color.x, light.color.y, light.color.z});

        /* Blending is set here and not only in the shader json: the shader's blend state is cached in a static field and skipped
           when it looks unchanged, while this pass turns blending off again afterwards, so the json alone gets lost after the first frame.
           The light is added on top of the scene (ONE, ONE). Its colour is already tinted by the block's hue in the shader, so it does
           not depend on how bright the surroundings are, and bright surfaces simply saturate like they do with real light. */
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SrcFactor.ONE, GlStateManager.DstFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(() -> program);

        BufferBuilder buffer = Tessellator.getInstance().getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION);
        buffer.vertex(-1.0, -1.0, 0.0).next();
        buffer.vertex(1.0, -1.0, 0.0).next();
        buffer.vertex(1.0, 1.0, 0.0).next();
        buffer.vertex(-1.0, 1.0, 0.0).next();
        BufferRenderer.drawWithGlobalProgram(buffer.end());

        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }
}
