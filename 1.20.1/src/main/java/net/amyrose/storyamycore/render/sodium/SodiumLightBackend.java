package net.amyrose.storyamycore.render.sodium;

import net.amyrose.storyamycore.render.vanilla.VanillaLightBackend;

/* Sodium draws terrain with its own shaders but into the same main framebuffer, so the depth-based cookie pass works
   unchanged. This class is the seam where Sodium-specific light injection (chunk shader interface) goes later. */
public class SodiumLightBackend extends VanillaLightBackend {
}
