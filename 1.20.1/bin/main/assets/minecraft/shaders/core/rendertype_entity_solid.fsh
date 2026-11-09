#version 330

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 normal;
in vec4 baseColor;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 normalOut;
layout(location = 2) out vec4 hueOut;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    normalOut = vec4(normalize(normal.xyz), gl_FragCoord.z);
    vec3 albedo = texture(Sampler0, texCoord0).rgb * baseColor.rgb;
    float peak = max(albedo.r, max(albedo.g, albedo.b));
    hueOut = vec4(peak > 0.001 ? albedo / peak : vec3(1.0), 1.0);
}
