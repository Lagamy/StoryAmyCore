package net.amyrose.storyamycore.render.light;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.resource.ResourceFactory;

/* A full-screen pass that runs in game space after the translucent layers, with the same depth, normal and albedo buffers the lights use. */
public interface IWorldShader {
    void reload(ResourceFactory resourceFactory);

    void render(WorldRenderContext context);
}
