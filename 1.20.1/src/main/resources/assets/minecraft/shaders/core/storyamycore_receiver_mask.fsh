#version 330

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec4 baseColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec4 normal;

layout(location = 1) out vec4 normalOut;
layout(location = 2) out vec4 albedoOut;
layout(location = 3) out vec4 maskOut;

/* Draws a model a second time into the extra buffers only (the colour attachment is switched off by NormalBuffer): normal and albedo
   like the entity shaders write them, plus the mask, which holds the fragment depth. The light shader compares that depth with the
   depth buffer, so a pixel counts as a receiver only while this model is the nearest thing drawn there. Texels with alpha 253/255 are
   emissive: their albedo alpha is written as 0, and the light shader adds their colour back regardless of vanilla lighting. */
void main() {
    vec4 color = texture(Sampler0, texCoord0);
    if (color.a < 0.1) {
        discard;
    }
    float emissive = color.a > 0.98 && color.a < 0.995 ? 0.0 : 1.0;
    albedoOut = vec4(mix(overlayColor.rgb, color.rgb * baseColor.rgb * ColorModulator.rgb, overlayColor.a), emissive);
    normalOut = vec4(normalize(normal.xyz), gl_FragCoord.z);
    maskOut = vec4(gl_FragCoord.z, 0.0, 0.0, 1.0);
}
