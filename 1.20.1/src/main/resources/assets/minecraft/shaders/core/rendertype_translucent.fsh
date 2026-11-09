#version 330

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec4 baseColor;
in vec2 texCoord0;
in vec3 viewPos;

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 normalOut;
layout(location = 2) out vec4 albedoOut;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
    albedoOut = vec4(texture(Sampler0, texCoord0).rgb * baseColor.rgb * ColorModulator.rgb, 1.0);
    normalOut = vec4(normalize(cross(dFdx(viewPos), dFdy(viewPos))), 1.0);
}
