package net.amyrose.storyamycore.render;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Shared by the light, world shader, entity shader and post effect registries: every entry is addressed by (modId, id). */
public final class RenderRegistry {
    public static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);

    private RenderRegistry() {}

    public static String key(String modId, String id) {
        return modId + ":" + id;
    }

    public static RuntimeException fail(String message) {
        LOGGER.error(message);
        throw new RuntimeException(message);
    }
}
