package net.amyrose.storyamycore.mixin.WorldgenerationPipeline;

import net.amyrose.storyamycore.core.Chapter;
import net.amyrose.storyamycore.core.Story;
import net.amyrose.storyamycore.core.StoryEngine;
import net.minecraft.util.math.BlockBox;
import net.minecraft.structure.StructureStart;
import net.minecraft.world.gen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Structure.class)
public class StructureStartMixin {
    @Inject(method = "createStructureStart", at = @At("RETURN"), cancellable = true)
    private void preventStructuresInChapters(CallbackInfoReturnable<StructureStart> cir) {
        StructureStart start = cir.getReturnValue();
        if (!start.hasChildren()) {
            return;
        }

        /*
         * Every structure start goes through Structure.createStructureStart during the STRUCTURE_STARTS stage.
         * Returning StructureStart.DEFAULT (no children) makes the generator save no structure for this chunk.
         * This stage runs before populateNoise, so the chunk's present chapters are not filled yet and
         * StoryEngine.stories is read directly (read-only, safe off the main thread).
         * The whole bounding box is checked, not just the start chunk, because a structure
         * that starts outside a ring can still extend into it.
         */
        BlockBox box = start.getBoundingBox();
        for (Story story : StoryEngine.stories.values()) {
            for (Chapter chapter : story.chapters.values()) {
                if (chapter.ringArea != null && chapter.ringArea.CheckCollision(box.getMinX(), box.getMinZ(), box.getMaxX(), box.getMaxZ())) {
                    cir.setReturnValue(StructureStart.DEFAULT);
                    return;
                }
            }
        }
    }
}
