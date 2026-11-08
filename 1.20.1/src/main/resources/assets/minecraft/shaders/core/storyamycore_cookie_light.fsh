#version 150

uniform sampler2D DepthSampler;
uniform sampler2D SceneSampler;
uniform sampler2D CookieSampler;
uniform mat4 InvProjMat;
uniform mat4 ViewToLightMat;
uniform float Fov;
uniform float Range;
uniform float Intensity;
uniform float Contrast;
uniform vec3 LightColor;

in vec2 texCoord;

out vec4 fragColor;

vec3 viewPositionAt(vec2 uv) {
    float depth = texture(DepthSampler, uv).r;
    vec4 position = InvProjMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return position.xyz / position.w;
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    if (depth >= 1.0) {
        discard;
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
    if (lightPos.z >= 0.0) {
        discard;
    }
    vec2 uv = lightPos.xy / (-lightPos.z * tan(radians(Fov) * 0.5)) * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        discard;
    }

    /* Surface normal from the depth buffer. On each axis the neighbouring pixel closest in depth is used, so at silhouette edges
       (a grass blade against the ground behind it) the slope never crosses the jump between two different surfaces, which would
       give a sideways normal and an unlit outline. It is flipped to face the camera, then rotated into light space, so surfaces
       turned away from the light get less of it. */
    vec2 texel = 1.0 / vec2(textureSize(DepthSampler, 0));
    vec3 center = viewPos.xyz;
    vec3 right = viewPositionAt(texCoord + vec2(texel.x, 0.0)) - center;
    vec3 left = center - viewPositionAt(texCoord - vec2(texel.x, 0.0));
    vec3 up = viewPositionAt(texCoord + vec2(0.0, texel.y)) - center;
    vec3 down = center - viewPositionAt(texCoord - vec2(0.0, texel.y));
    vec3 slopeX = abs(right.z) < abs(left.z) ? right : left;
    vec3 slopeY = abs(up.z) < abs(down.z) ? up : down;
    vec3 normal = cross(slopeX, slopeY);
    float normalLength = length(normal);
    if (normalLength < 1e-6) {
        discard;
    }
    normal /= normalLength;
    if (dot(normal, viewPos.xyz) > 0.0) {
        normal = -normal;
    }
    vec3 normalInLight = mat3(ViewToLightMat) * normal;
    float facing = max(dot(normalInLight, -lightPos.xyz / distanceToLight), 0.0);

    /* The block's hue (its colour scaled up to full brightness) tints the light, so grass stays green and stone grey, but a dark
       block is not lit any less than a bright one. */
    vec3 scene = texture(SceneSampler, texCoord).rgb;
    vec3 tint = clamp(scene / max(max(scene.r, max(scene.g, scene.b)), 0.1), 0.0, 1.0);

    /* Raising the mask to a power above 1 pulls the dark parts down faster than the bright ones, which sharpens the cookie. */
    vec3 mask = pow(texture(CookieSampler, vec2(uv.x, 1.0 - uv.y)).rgb, vec3(Contrast)) * LightColor;
    fragColor = vec4(mask * tint * Intensity * facing * falloff * falloff, 1.0);
}
