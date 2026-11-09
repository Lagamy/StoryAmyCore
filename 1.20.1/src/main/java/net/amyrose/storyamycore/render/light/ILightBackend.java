package net.amyrose.storyamycore.render.light;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.render.Camera;
import net.minecraft.resource.ResourceFactory;
import org.joml.Matrix4f;

import java.util.List;

public interface ILightBackend {
    void reload(ResourceFactory resourceFactory);

    void render(List<Light> lights, WorldRenderContext context);

    void renderHand(List<Light> lights, Camera camera, Matrix4f projection);
}
