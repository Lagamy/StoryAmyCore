package net.amyrose.storyamycore.render.entity;

import net.amyrose.storyamycore.render.NormalBuffer;
import net.amyrose.storyamycore.render.RenderRegistry;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderPhase;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.entity.Entity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/* Special rendering for chosen entities: while a matching entity is drawn, every render layer it asks for is replaced by a copy of that
   layer that differs only in its shader program (texture, blending, culling and so on are kept). The first enabled rule that matches wins. Render thread only.
   Rule: entities are drawn in batches per render layer, so the swap has to happen when the layer is requested (EntityRenderDispatcherMixin
   wraps the provider), not when the batch is drawn. For the same reason a shader uniform is shared by the whole batch, not set per entity. */
public class EntityShaderManager {
    static class Rule {
        String key;
        Predicate<Entity> appliesTo;
        Supplier<ShaderProgram> program;
        boolean receivesLights;
        boolean enabled = true;
        final Map<RenderLayer, RenderLayer> swapped = new HashMap<>();
    }

    static final Map<String, Rule> rules = new LinkedHashMap<>();

    public static void register(String modId, String id, Predicate<Entity> appliesTo, Supplier<ShaderProgram> program, boolean receivesLights) {
        String key = RenderRegistry.key(modId, id);
        if (rules.containsKey(key)) {
            RenderRegistry.fail("Entity shader " + key + " is already registered.");
        }
        Rule rule = new Rule();
        rule.key = key;
        rule.appliesTo = appliesTo;
        rule.program = program;
        rule.receivesLights = receivesLights;
        rules.put(key, rule);
    }

    public static void setEnabled(String modId, String id, boolean enabled) {
        Rule rule = rules.get(RenderRegistry.key(modId, id));
        if (rule == null) {
            RenderRegistry.fail("Entity shader " + RenderRegistry.key(modId, id) + " is not registered.");
        }
        rule.enabled = enabled;
    }

    /* Returns the provider unchanged (no allocation) when no rule applies, which is the case for nearly every entity. */
    public static VertexConsumerProvider wrap(Entity entity, VertexConsumerProvider provider) {
        for (Rule rule : rules.values()) {
            if (rule.enabled && rule.appliesTo.test(entity)) {
                return layer -> provider.getBuffer(swap(rule, layer));
            }
        }
        return provider;
    }

    /* Layers that are not built from phases are left alone. The program is looked up here, at first use, because a core shader is
       only loaded once the game has started rendering. */
    static RenderLayer swap(Rule rule, RenderLayer layer) {
        RenderLayer cached = rule.swapped.get(layer);
        if (cached != null) {
            return cached;
        }
        if (!(layer instanceof RenderLayer.MultiPhase multi)) {
            rule.swapped.put(layer, layer);
            return layer;
        }
        if (rule.receivesLights) {
            ShaderProgram program = rule.program.get();
            if (program == null) {
                RenderRegistry.fail("Entity shader " + rule.key + " has no shader program loaded: register it with CoreShaderRegistrationCallback.");
            }
            NormalBuffer.addShader(program.getName());
        }
        var p = multi.getPhases();
        RenderLayer result = RenderLayer.of(multi.name + "_" + rule.key, multi.getVertexFormat(), multi.getDrawMode(), multi.getExpectedBufferSize(),
                multi.hasCrumbling, multi.translucent,
                new RenderLayer.MultiPhaseParameters(p.texture, new RenderPhase.ShaderProgram(rule.program), p.transparency, p.depthTest, p.cull,
                        p.lightmap, p.overlay, p.layering, p.target, p.texturing, p.writeMaskState, p.lineWidth, p.colorLogic, p.outlineMode));
        rule.swapped.put(layer, result);
        return result;
    }
}
