package net.amyrose.storyamycore.mixin.worldgeneration;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.ChapterEngine;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunkGenerator.class)
public class HeightNoiseMixin {

    @Inject(method = "getHeight", at = @At("HEAD"), cancellable = true)
    private void overrideHeight(int x, int z, Heightmap.Type type, HeightLimitView world, NoiseConfig noiseConfig,
                                CallbackInfoReturnable<Integer> cir) {

        //int flatHeight = 64;
        // Check if the coordinates are within the desert square
        for(Chapter chapter : ChapterEngine.chapters) {
            if (x >= chapter.beginning.x && x <= chapter.end.x && z >= chapter.beginning.z && z <= chapter.end.z) {
                // Set a fixed height for the terrain (e.g., Y=64)
                // Adjust this to your desired Y level
                //System.out.println("Overriding height to " + flatHeight + " at x: " + x + ", z: " + z);
                cir.setReturnValue(64);
            }
        }
    }
}