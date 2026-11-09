#version 150

uniform sampler2D DepthSampler;
uniform sampler2D AlbedoSampler;
uniform sampler2D NormalSampler;
uniform sampler2D CookieSampler;
uniform sampler2D MaskSampler;
uniform mat4 InvProjMat;
uniform mat4 ViewToLightMat;
uniform float Fov;
uniform float IsPoint;
uniform float ReceiversOnly;
uniform float Range;
uniform float Intensity;
uniform float Contrast;
uniform vec3 LightColor;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (depth >= 1.0) {
        discard;
    }

    /* A light with receivers only affects pixels whose nearest surface was drawn by one of its models: the mask holds that surface's
       depth (see storyamycore_receiver_mask.fsh), and -1 where nothing was marked. */
    if (ReceiversOnly > 0.5 && abs(texture(MaskSampler, texCoord).r - depth) > 5e-7) {
        discard;
    }

    /* Emissive texels of a receiver (albedo alpha 0, see storyamycore_receiver_mask.fsh) get their own colour added at full strength,
       so they stay bright whatever vanilla lighting did to them, and independent of the light's range. */
    if (ReceiversOnly > 0.5) {
        vec4 emissiveAlbedo = texture(AlbedoSampler, texCoord);
        if (emissiveAlbedo.a < 0.5) {
            fragColor = vec4(emissiveAlbedo.rgb, 1.0);
            return;
        }
    }

    vec4 ndc = vec4(texCoord * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    vec4 viewPos = InvProjMat * ndc;
    viewPos /= viewPos.w;

    vec4 lightPos = ViewToLightMat * vec4(viewPos.xyz, 1.0);
    float distanceToLight = length(lightPos.xyz);
    float falloff = clamp(1.0 - distanceToLight / Range, 0.0, 1.0);
    if (falloff <= 0.0) {
        discard;
    }

    /* The light looks down -Z. Perspective with a square aspect: the cone edge sits where x or y equals depth * tan(fov / 2). */
    vec2 uv = vec2(0.5);
    if (IsPoint < 0.5) {
        if (lightPos.z >= 0.0) {
            discard;
        }
        uv = lightPos.xy / (-lightPos.z * tan(radians(Fov) * 0.5)) * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
            discard;
        }
    }

    /* Surface normal written by the terrain and entity shaders (see NormalBuffer). Its w holds the depth of the fragment that wrote it:
       when that differs from the depth buffer, something without a normal was drawn over it afterwards (a block outline line) or
       nothing wrote at all (sky, water, particles), and the pixel is left unlit. A stored w of 1 marks a translucent surface (water, glass), which cannot keep a depth because
       of blending, so that check is skipped for it. The normal is flipped to face the camera, then
       rotated into light space, so surfaces turned away from the light get less of it. */
    vec4 stored = texture(NormalSampler, texCoord);
    if (stored.w < 1.0 && abs(stored.w - depth) > 5e-7) {
        discard;
    }
    vec3 normal = stored.xyz;
    float normalLength = length(normal);
    if (normalLength < 0.5) {
        discard;
    }
    normal /= normalLength;
    if (dot(normal, viewPos.xyz) > 0.0) {
        normal = -normal;
    }
    vec3 normalInLight = mat3(ViewToLightMat) * normal;
    /* Half-Lambert with an ambient floor (the same curve Veil's area light uses): a surface edge-on to the light still gets
       about 0.64 instead of 0, so flat ground and the vertical quads of plants end up close in brightness. */
    float facing = (dot(normalInLight, -lightPos.xyz / distanceToLight) + 1.0) * 0.5;
    facing = (facing + 0.4) / 1.4;

    /* The light multiplies the surface's unlit albedo (written by the terrain and entity shaders), with a small floor so black
       surfaces still catch some light. Same combine as Veil: albedo * 0.9 + 0.1. It never reads the lit, 8-bit frame, so vanilla
       lighting and its smooth-lighting gradients do not leak into the light. */
    vec3 albedo = texture(AlbedoSampler, texCoord).rgb * 0.9 + 0.1;

    /* Raising the mask to a power above 1 pulls the dark parts down faster than the bright ones, which sharpens the cookie. */
    /* A point light shines in every direction and has no cookie: the mask is just the tint. */
    vec3 mask = LightColor;
    if (IsPoint < 0.5) {
        mask = pow(texture(CookieSampler, vec2(uv.x, 1.0 - uv.y)).rgb, vec3(Contrast)) * LightColor;
    }
    fragColor = vec4(mask * albedo * Intensity * facing * falloff * falloff, 1.0);
}
