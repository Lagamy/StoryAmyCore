package net.amyrose.storyamycore.render.light;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.resource.ResourceFactory;

import java.util.List;

public interface ILightBackend {
    void reload(ResourceFactory resourceFactory);

    void render(List<Light> lights, WorldRenderContext context);
}
