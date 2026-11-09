package net.amyrose.storyamycore.block;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.fabricmc.fabric.api.object.builder.v1.block.FabricBlockSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.util.Identifier;

public class ModBlocks {
    public static final Block FLASHLIGHT_BLOCK = Registry.register(Registries.BLOCK, new Identifier(StoryAmyCoreMod.MOD_ID, "flashlight_block"),
            new FlashlightBlock(FabricBlockSettings.copyOf(Blocks.SUGAR_CANE).sounds(BlockSoundGroup.STONE)));

    public static void registerBlocks() {}
}
