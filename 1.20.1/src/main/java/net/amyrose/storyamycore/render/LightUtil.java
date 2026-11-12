package net.amyrose.storyamycore.render;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.render.LightmapTextureManager;

@Environment(EnvType.CLIENT)
public final class LightUtil {
    private static final int MAX_LEVEL = 15;

    private LightUtil() {}

    /* Vanilla packs light as block level (0-15) and sky level (0-15) in one int, and the lightmap texture is only that big, so a level above 15
       would read outside it. The minimums are clamped to 0-15 and each component only ever goes up, never down. */
    public static int withMinLight(int packedLight, int minBlock, int minSky) {
        int block = Math.max(LightmapTextureManager.getBlockLightCoordinates(packedLight), Math.max(0, Math.min(MAX_LEVEL, minBlock)));
        int sky = Math.max(LightmapTextureManager.getSkyLightCoordinates(packedLight), Math.max(0, Math.min(MAX_LEVEL, minSky)));
        return LightmapTextureManager.pack(block, sky);
    }
}
