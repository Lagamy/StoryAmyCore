package net.amyrose.storyamycore.render.light;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

/* A spotlight. The cookie is the mask: its brightness is the light's brightness inside the cone. */
public class Light {
    /* Set by the registration call, read-only afterwards. */
    public String modId;
    public String id;
    /* A disabled light is skipped by the renderer but stays registered, so a mod can switch it back on. */
    public boolean enabled = true;
    /* Optional. Called every frame before the light is drawn (also while disabled), so a mod can move the light, attach it to an
       entity or hand, fade it, or turn it on and off. The context gives the camera and tickDelta. */
    public BiConsumer<Light, WorldRenderContext> updater;
    public Identifier cookie;
    /* The five settings below are null by default, which means the value from storyamycore_cookie_light.json is used.
       Set one in code only to override the json for this light. */
    public Float intensity;
    /* Tint of the light, multiplied with the cookie. White leaves the cookie's own colours. */
    public Vector3f color;
    /* Exponent applied to the cookie brightness: 1 is unchanged, above 1 darkens the dim parts more than the bright ones. */
    public Float contrast;
    /* Cone width: the angle of the beam. The cookie is stretched over it, so a larger angle gives a larger spot at any distance. */
    public Float fovDegrees;
    /* Distance in blocks at which the light has faded out completely. */
    public Float range;
    /* World position and direction (Minecraft yaw/pitch in degrees). Ignored while followCamera is true. */
    public Vec3d position = Vec3d.ZERO;
    public float yaw;
    public float pitch;
    /* True makes the light sit at the camera and look where it looks (a flashlight held at the head). */
    public boolean followCamera;
    /* True makes the light shine in every direction from its position, like a bulb. The cookie, yaw, pitch and fovDegrees are ignored;
       only the position, range, intensity and color are used. */
    public boolean point;
    /* Receivers: when any of these is set, the light affects only the models they match (normally combined with point = true). Drawn
       models are matched every frame, so they may change with the state of the game. A light with receivers marks, for the whole
       frame, every matching model, and every receivers-only light then lights all marked models, not only its own. Terrain blocks
       are not supported. */
    public Predicate<Entity> entityReceivers;
    public Predicate<ItemStack> itemReceivers;
    public Predicate<BlockEntity> blockEntityReceivers;

    public Light(Identifier cookie) {
        this.cookie = cookie;
    }

    public boolean hasReceivers() {
        return entityReceivers != null || itemReceivers != null || blockEntityReceivers != null;
    }

    /* Each affect* call adds to what the light already affects (a model matching any of them is affected). */
    public Light affectEntities(Predicate<Entity> predicate) {
        entityReceivers = entityReceivers == null ? predicate : entityReceivers.or(predicate);
        return this;
    }

    public Light affectEntity(EntityType<?> type) {
        return affectEntities(entity -> entity.getType() == type);
    }

    /* Items held in hands (both views), dropped, or in item frames. The inventory/GUI is not affected. */
    public Light affectItems(Predicate<ItemStack> predicate) {
        itemReceivers = itemReceivers == null ? predicate : itemReceivers.or(predicate);
        return this;
    }

    public Light affectItem(Item item) {
        return affectItems(stack -> stack.isOf(item));
    }

    public Light affectBlockEntities(Predicate<BlockEntity> predicate) {
        blockEntityReceivers = blockEntityReceivers == null ? predicate : blockEntityReceivers.or(predicate);
        return this;
    }

    public Light affectBlockEntity(BlockEntityType<?> type) {
        return affectBlockEntities(blockEntity -> blockEntity.getType() == type);
    }
}
