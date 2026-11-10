package net.amyrose.storyamycore.mixin.StructureBlock;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.c2s.play.UpdateStructureBlockC2SPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/* The vanilla packet writes the offset and size as single bytes and clamps them to 48, so a bigger structure cannot be sent from the screen.
   The vanilla bytes are left as they are and the real values are appended after them as var ints, then read back over the clamped fields.
   Both sides have this mixin; a client or server without it would fail to read the packet. */
@Mixin(UpdateStructureBlockC2SPacket.class)
public abstract class UpdateStructureBlockC2SPacketMixin {

    @Mutable
    @Shadow
    @Final
    private BlockPos offset;

    @Mutable
    @Shadow
    @Final
    private Vec3i size;

    @Inject(method = "<init>(Lnet/minecraft/network/PacketByteBuf;)V", at = @At("TAIL"))
    private void readFullSize(PacketByteBuf buf, CallbackInfo ci) {
        offset = new BlockPos(buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
        size = new Vec3i(Math.max(0, buf.readVarInt()), Math.max(0, buf.readVarInt()), Math.max(0, buf.readVarInt()));
    }

    @Inject(method = "write", at = @At("TAIL"))
    private void writeFullSize(PacketByteBuf buf, CallbackInfo ci) {
        buf.writeVarInt(offset.getX());
        buf.writeVarInt(offset.getY());
        buf.writeVarInt(offset.getZ());
        buf.writeVarInt(size.getX());
        buf.writeVarInt(size.getY());
        buf.writeVarInt(size.getZ());
    }
}
