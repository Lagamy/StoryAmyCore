package net.amyrose.storyamycore.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.ShaderProgram;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.util.HashSet;
import java.util.Set;

/* Three extra colour attachments on the main framebuffer. Attachment 1: xyz is the view-space surface normal, w is the fragment depth that wrote it.
   Attachment 2: the unlit albedo (texture * vertex colour, without the lightmap), so the light multiplies the surface's own colour
   and never has to be derived from the lit, 8-bit frame.
   Attachment 3: the receiver mask, the depth of models that a receivers-only light affects (see ReceiverMask), -1 elsewhere.
   Only the solid terrain and entity shaders (overridden in assets/minecraft/shaders/core and assets/sodium/shaders/blocks) write it,
   plus the entity shaders other mods registered with receivesLights (addShader).
   Rule: a framebuffer only writes to the attachments listed by glDrawBuffers. It stays at attachment 0 only, and is widened to
   all three attachments just while a layer with one of those shaders is drawn, so sky, water, particles and everything else leave
   the normals untouched. The stored depth lets the light shader notice pixels that were drawn over afterwards (block outlines).
   Translucent layers (water, glass) are blended, and a blend would mix the stored values with what was there. They write w = 1 instead:
   with a source alpha of exactly 1 the blend replaces the value, and the light shader reads w = 1 as "translucent, skip the depth check". */
public class NormalBuffer {
    static final Set<String> SHADERS = new HashSet<>(Set.of(
            "rendertype_solid", "rendertype_translucent", "rendertype_cutout", "rendertype_cutout_mipped",
            "rendertype_entity_solid", "rendertype_entity_cutout", "rendertype_entity_cutout_no_cull",
            "rendertype_entity_cutout_no_cull_z_offset", "rendertype_armor_cutout_no_cull"));

    static int texture = -1;
    static int albedoTexture = -1;
    static int maskTexture = -1;
    static final String MASK_SHADER = "storyamycore_receiver_mask";
    static boolean active;

    /* Render thread only. The shader must write the two extra outputs like the overridden rendertype_entity_* shaders do. */
    public static void addShader(String programName) {
        SHADERS.add(programName);
    }

    public static int getTexture() {
        return texture;
    }

    public static int getAlbedoTexture() {
        return albedoTexture;
    }

    public static int getMaskTexture() {
        return maskTexture;
    }

    static int makeTexture(int internalFormat, int format, int type, int width, int height) {
        int id = GlStateManager._genTexture();
        GlStateManager._bindTexture(id);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL11.GL_TEXTURE_2D, 0, internalFormat, width, height, 0, format, type, null);
        return id;
    }

    /* Called when the main framebuffer is (re)created, with its fbo bound to nothing else: the texture is made at its size. */
    public static void attach(int fbo, int width, int height) {
        release();
        texture = makeTexture(GL30.GL_RGBA32F, GL11.GL_RGBA, GL11.GL_FLOAT, width, height);
        albedoTexture = makeTexture(GL11.GL_RGBA8, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, width, height);
        maskTexture = makeTexture(GL30.GL_R32F, GL11.GL_RED, GL11.GL_FLOAT, width, height);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT1, GL11.GL_TEXTURE_2D, texture, 0);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT2, GL11.GL_TEXTURE_2D, albedoTexture, 0);
        GlStateManager._glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT3, GL11.GL_TEXTURE_2D, maskTexture, 0);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
        GlStateManager._bindTexture(0);
    }

    public static void release() {
        if (texture >= 0) {
            GlStateManager._deleteTexture(texture);
            texture = -1;
        }
        if (albedoTexture >= 0) {
            GlStateManager._deleteTexture(albedoTexture);
            albedoTexture = -1;
        }
        if (maskTexture >= 0) {
            GlStateManager._deleteTexture(maskTexture);
            maskTexture = -1;
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
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2, GL30.GL_COLOR_ATTACHMENT3});
        GL30.glClearBufferfv(GL30.GL_COLOR, 1, new float[]{0.0f, 0.0f, 0.0f, -1.0f});
        GL30.glClearBufferfv(GL30.GL_COLOR, 3, new float[]{-1.0f, 0.0f, 0.0f, 0.0f});
        GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
    }

    /* Called right after a render layer set up its state, so the active shader is known. Layers drawn into another framebuffer
       (fabulous graphics) are skipped because that one has no second attachment. */
    public static void begin() {
        ShaderProgram shader = RenderSystem.getShader();
        if (shader == null) {
            return;
        }
        if (shader.getName().equals(MASK_SHADER)) {
            beginMask();
        } else if (SHADERS.contains(shader.getName())) {
            beginAlways();
        }
    }

    /* Same as begin, for code that draws with several shaders in a row (particles) and sets the shader itself. */
    public static void beginAlways() {
        if (texture < 0 || active) {
            return;
        }
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        if (GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != main.fbo) {
            return;
        }
        GL20.glDrawBuffers(new int[]{GL30.GL_COLOR_ATTACHMENT0, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2});
        active = true;
    }

    /* The receiver mask shader writes attachments 1 to 3 and leaves the colour attachment alone (GL_NONE), so drawing a model again
       with it changes no visible pixel. */
    static void beginMask() {
        if (texture < 0 || maskTexture < 0 || active) {
            return;
        }
        Framebuffer main = MinecraftClient.getInstance().getFramebuffer();
        if (GlStateManager._getInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) != main.fbo) {
            return;
        }
        GL20.glDrawBuffers(new int[]{GL11.GL_NONE, GL30.GL_COLOR_ATTACHMENT1, GL30.GL_COLOR_ATTACHMENT2, GL30.GL_COLOR_ATTACHMENT3});
        active = true;
    }

    public static void end() {
        if (active) {
            active = false;
            GL20.glDrawBuffers(GL30.GL_COLOR_ATTACHMENT0);
        }
    }
}
