package net.amyrose.storyamycore.mixin.StructureBlock;

import net.amyrose.storyamycore.StoryAmyCoreMod;
import net.amyrose.storyamycore.core.LandscapeNbtReader;
import net.amyrose.storyamycore.core.StructureStreamJobs;
import net.amyrose.storyamycore.core.StructureStreamLoadJob;
import net.amyrose.storyamycore.core.StructureStreamSaveJob;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.StructureBlockBlockEntity;
import net.minecraft.block.enums.StructureBlockMode;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.InvalidIdentifierException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.IOException;
import java.io.InputStream;

/* The chapter block is the vanilla structure block with its limits lifted and its save and load streamed (see StructureStreamSaveJob and
   StructureStreamLoadJob). Structures that cannot be streamed (blocks before the palette, as in many vanilla files) still go through the vanilla code.
   The size and offset limit of 48 lives in three places: readNbt here, and the update packet (UpdateStructureBlockC2SPacketMixin). */
@Mixin(StructureBlockBlockEntity.class)
public abstract class StructureBlockBlockEntityMixin {

    @Unique
    private static final Logger LOGGER = LoggerFactory.getLogger(StoryAmyCoreMod.MOD_ID);

    @Shadow
    private Identifier templateName;

    /* readNbt clamps the saved size to 0..48 and the offset to -48..48. The negative size stays blocked, the upper limits are removed. */
    @Redirect(method = "readNbt", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;clamp(III)I"))
    private static int removeSizeLimit(int value, int min, int max) {
        return min == 0 ? Math.max(0, value) : value;
    }

    /* The vanilla check loads the whole template into memory just to see whether it exists. */
    @Inject(method = "isStructureAvailable", at = @At("HEAD"), cancellable = true)
    private void checkFileExists(CallbackInfoReturnable<Boolean> cir) {
        StructureBlockBlockEntity self = (StructureBlockBlockEntity) (Object) this;
        if (self.getMode() != StructureBlockMode.LOAD || !(self.getWorld() instanceof ServerWorld world) || templateName == null) {
            cir.setReturnValue(false);
            return;
        }
        try (InputStream stream = StructureStreamJobs.open(world, templateName)) {
            cir.setReturnValue(stream != null);
        } catch (IOException | InvalidIdentifierException e) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "saveStructure(Z)Z", at = @At("HEAD"), cancellable = true)
    private void streamSave(boolean write, CallbackInfoReturnable<Boolean> cir) {
        StructureBlockBlockEntity self = (StructureBlockBlockEntity) (Object) this;
        if (!write) {
            return;
        }
        if (self.getMode() != StructureBlockMode.SAVE || !(self.getWorld() instanceof ServerWorld world) || templateName == null) {
            cir.setReturnValue(false);
            return;
        }
        try {
            BlockPos start = self.getPos().add(self.getOffset());
            StructureStreamJobs.add(new StructureStreamSaveJob(world, start, self.getSize(), templateName));
            cir.setReturnValue(true);
        } catch (IOException | InvalidIdentifierException e) {
            LOGGER.error("Could not start streaming structure save", e);
            cir.setReturnValue(false);
        }
    }

    /* Mirrors the vanilla flow: when the stored size differs from the file the size is only updated (and false returned, so the player
       presses load again with the right bounding box); otherwise the blocks are placed. */
    @Inject(method = "loadStructure(Lnet/minecraft/server/world/ServerWorld;Z)Z", at = @At("HEAD"), cancellable = true)
    private void streamLoad(ServerWorld world, boolean strictSize, CallbackInfoReturnable<Boolean> cir) {
        StructureBlockBlockEntity self = (StructureBlockBlockEntity) (Object) this;
        if (self.getMode() != StructureBlockMode.LOAD || templateName == null) {
            return;
        }
        try {
            int[] fileSize;
            try (InputStream stream = StructureStreamJobs.open(world, templateName)) {
                fileSize = stream == null ? null : LandscapeNbtReader.readStreamableSize(stream);
            }
            if (fileSize == null) {
                return;
            }

            Vec3i size = new Vec3i(fileSize[0], fileSize[1], fileSize[2]);
            boolean sameSize = self.getSize().equals(size);
            if (!sameSize) {
                self.setSize(size);
                self.markDirty();
                BlockState state = world.getBlockState(self.getPos());
                world.updateListeners(self.getPos(), state, state, 3);
            }
            if (strictSize && !sameSize) {
                cir.setReturnValue(false);
                return;
            }
            InputStream stream = StructureStreamJobs.open(world, templateName);
            StructureStreamJobs.add(new StructureStreamLoadJob(world, stream, self.getPos().add(self.getOffset()), self.getMirror(), self.getRotation(), templateName));
            cir.setReturnValue(true);
        } catch (IOException | InvalidIdentifierException e) {
            LOGGER.error("Could not start streaming structure load", e);
        }
    }
}
