package net.amyrose.storyamycore;

import net.amyrose.storyamycore.client.CompassClient;
import net.amyrose.storyamycore.client.FlashlightClient;
import net.amyrose.storyamycore.render.light.LightManager;
import net.amyrose.storyamycore.render.post.PostEffectManager;
import net.fabricmc.api.ClientModInitializer;

public class StoryAmyCoreClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        LightManager.init();
        PostEffectManager.init();
        FlashlightClient.init();
        CompassClient.init();
    }
}
