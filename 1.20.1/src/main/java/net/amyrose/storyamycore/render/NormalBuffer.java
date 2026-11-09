package net.amyrose.storyamycore.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.Set;

/* Second colour attachment on the main framebuffer: xyz is the view-space surface normal, w is the fragment depth that wrote it.
   Only the solid terrain and entity shaders (overridden in assets/minecraft/shaders/core and assets/sodium/shaders/blocks) write it.
   Rule: a framebuffer only writes to the attachments listed by glDrawBuffers. It stays at attachment 0 only, and is widened to
   both attachments just while a layer with one of those shaders is drawn, so sky, water, particles and everything else leave
   the normals untouched. The stored depth lets the light shader notice pixels that were drawn over afterwards (block outlines). */
public class NormalBuffer {
    static final Set<String> SHADERS = Set.of(
            "rendertype_solid", "rendertype_cutout", "rendertype_cutout_mipped",
            "rendertype_entity_solid", "rendertype_entity_cutout", "rendertype_entity_cutout_no_cull",
            "rendertype_entity_cutout_no_cull_z_offset", "rendertype_armor_cutout_no_cull");

    static int texture = -1;
    static boolean active;

    public static int getTexture() {
        return texture;
    }

    /* Called when the main framebuffer is (re)created, with its fbo bound to nothing else: the texture is made at its size. */
    public static void attach(int fbo, int width, int height) {
        release();
        texture = GlStateManager._genTexture();
        GlStateManager._bindTexture(texture);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, width, height, 0, GL11.GL_RGBA, GL11.GL_FLOAT, null);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, texture, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GlStateManager._bindTexture(0);
    }

    public static void release() {
        if (texture >= 0) {
            GlStateManager._deleteTexture(texture);
            texture = -1;
        }
        active = false;
    }

    /* Run at the start of a frame: the stored depth -1 never matches a real depth, so untouched pixels count as "no normal".
       A clear of attachment 1 only works while a draw buffer points at it. */
    public static void clear() {
        if (texture < 0) {
            return;
        }
        MinecraftClient.getInstance().getFramebuffer().beginWrite(false);
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1});
        GL30.glClearBufferfv(GL30.GL_COLOR, 1, new float[]{0.0f, 0.0f, 0.0f, -1.0f});
        GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
    }

    /* Called right after a render layer set up its state, so the active shader is known. Layers drawn into another framebuffer
       (fabulous graphics) are skipped because that one has no second attachment. */
    public static void begin() {
        if (texture < 0 || active) {
            return;
        }
        ShaderProgram shader = RenderSystem.getShader();
        if (shader == null || !SHADERS.contains(shader.getName())) {
            return;
        }
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        if (GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != main.fbo) {
            return;
        }
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1});
        active = true;
    }

    public static void end() {
        if (active) {
            active = false;
            GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        }
    }
}
