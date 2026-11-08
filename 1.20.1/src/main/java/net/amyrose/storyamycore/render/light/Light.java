package net.amyrose.storyamycore.render.light;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/* A spotlight. The cookie is the mask: its brightness is the light's brightness inside the cone. */
public class Light {
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

    public Light(Identifier cookie) {
        this.cookie = cookie;
    }
}
