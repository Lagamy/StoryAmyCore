#version 330 core

#import <sodium:include/fog.glsl>

in vec4 v_BaseColor;
in vec4 v_Color; // The interpolated vertex color
in vec3 v_ViewPos;
in vec2 v_TexCoord; // The interpolated block texture coordinates
in float v_FragDistance; // The fragment's distance from the camera

in float v_MaterialMipBias;
in float v_MaterialAlphaCutoff;

uniform sampler2D u_BlockTex; // The block texture

uniform vec4 u_FogColor; // The color of the shader fog
uniform float u_FogStart; // The starting position of the shader fog
uniform float u_FogEnd; // The ending position of the shader fog

layout(location = 0) out vec4 fragColor;
layout(location = 1) out vec4 normalOut;
layout(location = 2) out vec4 albedoOut;

void main() {
    vec4 diffuseColor = texture(u_BlockTex, v_TexCoord, v_MaterialMipBias);

#ifdef USE_FRAGMENT_DISCARD
    if (diffuseColor.a < v_MaterialAlphaCutoff) {
        discard;
    }
#endif

    // Apply per-vertex color
    diffuseColor.rgb *= v_Color.rgb;

    // Apply ambient occlusion "shade"
    diffuseColor.rgb *= v_Color.a;

    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);

    albedoOut = vec4(texture(u_BlockTex, v_TexCoord, v_MaterialMipBias).rgb * v_BaseColor.rgb * v_BaseColor.a, 1.0);
    normalOut = vec4(normalize(cross(dFdx(v_ViewPos), dFdy(v_ViewPos))), diffuseColor.a < 0.999 ? 1.0 : gl_FragCoord.z);
}
