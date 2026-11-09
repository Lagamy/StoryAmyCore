package net.amyrose.storyamycore.render;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.resource.ResourceFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/* Marks the models that a light with receivers affects. A marked model is drawn a second time with a copy of each of its render layers
   whose shader is storyamycore_receiver_mask: it writes the normal, the albedo and the mask (attachment 3 of NormalBuffer, holding the
   fragment depth) and nothing visible. The light shader then lights only pixels whose depth equals the mask.
   Rule: a second draw is used, not a copy of the vertices into two layers at once, because non-fixed layers share one fallback buffer
   in VertexConsumerProvider.Immediate: asking for a second layer finishes the first, so two live consumers would write into the same
   builder. Render thread only. */
public class ReceiverMask {
    static final ShaderProgramHolder holder = new ShaderProgramHolder();
    static final Map<RenderLayer, RenderLayer> layers = new HashMap<>();
    static final Set<RenderLayer> maskLayers = new HashSet<>();

    /* True while a model is being drawn for the mask, so hooks that mark models do not mark the second draw again. */
    public static boolean drawing;

    static class ShaderProgramHolder {
        ShaderProgram program;
    }

    public static void reload(ResourceFactory resourceFactory) {
        if (holder.program != null) {
            holder.program.close();
        }
        try {
            holder.program = new ShaderProgram(resourceFactory, NormalBuffer.MASK_SHADER, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);
        } catch (IOException e) {
            RenderRegistry.fail("Failed to load the receiver mask shader: " + e);
        }
    }

    /* The mask copy of a layer: the same texture, culling, depth test and so on, with the mask program and no blending (a blend would
       mix the normals). Only layers in the entity vertex format, which the mask shader reads, have one; for any other layer (text,
       beams, lines) this returns null and the layer is simply not marked. A mask layer maps to itself. */
    public static RenderLayer layerFor(RenderLayer layer) {
        if (maskLayers.contains(layer)) {
            return layer;
        }
        if (layers.containsKey(layer)) {
            return layers.get(layer);
        }
        RenderLayer result = null;
        if (layer instanceof RenderLayer.MultiPhase multi && multi.getVertexFormat() == VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL) {
            var p = multi.getPhases();
            result = RenderLayer.of(multi.name + "_receiver_mask", multi.getVertexFormat(), multi.getDrawMode(), multi.getExpectedBufferSize(),
                    multi.hasCrumbling, false,
                    new RenderLayer.MultiPhaseParameters(p.texture, new RenderPhase.ShaderProgram(() -> holder.program), RenderPhase.NO_TRANSPARENCY,
                            p.depthTest, p.cull, p.lightmap, p.overlay, p.layering, p.target, p.texturing, p.writeMaskState, p.lineWidth,
                            p.colorLogic, p.outlineMode));
            maskLayers.add(result);
        }
        layers.put(layer, result);
        return result;
    }

    /* Wraps a provider so every layer asked for gets its mask copy. Layers without one are given a consumer that throws everything
       away, so the second draw of an entity does not draw its name tag or other non-model layers twice. */
    public static VertexConsumerProvider provider(VertexConsumerProvider base) {
        return layer -> {
            RenderLayer mask = layerFor(layer);
            return mask == null ? Discard.INSTANCE : base.getBuffer(mask);
        };
    }

    static class Discard implements VertexConsumer {
        static final Discard INSTANCE = new Discard();

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer texture(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer overlay(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer light(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            return this;
        }

        @Override
        public void next() {
        }

        @Override
        public void fixedColor(int red, int green, int blue, int alpha) {
        }

        @Override
        public void unfixColor() {
        }
    }
}
