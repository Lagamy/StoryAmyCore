package net.amyrose.storyamycore.render.vanilla;

import net.amyrose.storyamycore.render.CookieLightPass;
import net.amyrose.storyamycore.render.light.ILightBackend;
import net.amyrose.storyamycore.render.light.Light;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.resource.ResourceFactory;

import java.util.List;

public class VanillaLightBackend implements ILightBackend {
    final CookieLightPass cookiePass = new CookieLightPass();

    @Override
    public void reload(ResourceFactory resourceFactory) {
        cookiePass.reload(resourceFactory);
    }

    @Override
    public void render(List<Light> lights, WorldRenderContext context) {
        for (Light light : lights) {
            cookiePass.draw(light, context);
        }
    }
}
