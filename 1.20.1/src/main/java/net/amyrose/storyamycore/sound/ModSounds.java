package net.amyrose.storyamycore.sound;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class ModSounds {
    public static final SoundEvent FLASHLIGHT_CLICK = register("flashlight_click");

    private static SoundEvent register(String name) {
        Identifier id = new Identifier(StoryAmyCoreMod.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerSounds() {}
}
