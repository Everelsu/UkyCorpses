#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
// Texels a spreading sprite reaches at level 1, and whether its edge is soft (smooth style) or a hard pixel rim.
uniform float Spread;
uniform float Soft;
// Wet blood's reflection, 0 for none; how bright the sun's (or moon's) glint is just now; the sky's colour overhead.
uniform float Specular;
uniform float SunLight;
uniform vec3 SkyColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in float level;
in float wetness;
in vec3 viewPosition;
in vec3 sunView;
in vec3 upView;
in float skyLight;
in float blockLight;
in float billboard;

// Fresh blood is a film of liquid over whatever it's on: it reflects like water, about 2% looking straight down and
// nearly all of it at a grazing angle (Schlick's Fresnel), mirroring the sky and the sun or moon. The film isn't quite
// flat, so each texel tilts a little and the glint breaks up over it. Drops in the air have their glint drawn in.
// alongX, alongY: dFdx and dFdy of viewPosition, taken in main, where every fragment takes them.
vec3 reflection(vec3 rgb, float wet, vec3 alongX, vec3 alongY) {
    vec3 n = normalize(cross(alongX, alongY));
    vec3 v = normalize(-viewPosition);
    if (dot(n, v) < 0.0) n = -n;
    vec2 cell = floor(texCoord0 * vec2(textureSize(Sampler0, 0)));
    vec2 tilt = fract(sin(vec2(dot(cell, vec2(12.9898, 78.233)), dot(cell, vec2(39.346, 11.135)))) * 43758.5453) - 0.5;
    vec3 across = normalize(cross(n, abs(n.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    n = normalize(n + (across * tilt.x + cross(n, across) * tilt.y) * 0.12);
    float facing = max(dot(n, v), 0.0);
    float fresnel = (0.02 + 0.98 * pow(1.0 - facing, 5.0)) * wet * Specular;
    // The sky, where there's sky to see: overhead's colour where it reflects steeply, the horizon's (the fog's) low
    // down; and torchlight close by, warmly.
    float up = clamp(dot(reflect(-v, n), upView) * 2.5, 0.0, 1.0);
    vec3 mirrored = mix(FogColor.rgb, SkyColor, up) * skyLight * skyLight + vec3(1.0, 0.78, 0.55) * blockLight * blockLight * 0.35;
    vec3 h = normalize(sunView + v);
    float glint = pow(max(dot(n, h), 0.0), 240.0) * step(0.0, dot(n, sunView)) * SunLight * skyLight * wet * Specular;
    return rgb * (1.0 - fresnel) + mirrored * fresnel + vec3(1.0, 0.96, 0.88) * glint * 4.0;
}

out vec4 fragColor;

// The atlas is grey: R is the shade (x1.6, so highlights can go past the blood colour), G the order a spreading
// sprite reaches each texel in, B the shade plus the gloss fresh blood has. See tools/build_textures.py.
void main() {
    vec3 alongX = dFdx(viewPosition), alongY = dFdy(viewPosition);
    vec4 tex = texture(Sampler0, texCoord0);
    // Drops in the air have their glint drawn into the sprite; it's specular, so only shows with specular on.
    float gloss = max(tex.b - tex.r, 0.0) * (billboard > 0.5 ? Specular : 1.0);
    float wet = wetness;
    float shade = tex.r;
    float alpha = tex.a;
    float crust = 1.0;
    if (level >= 0.0) {
        // Texels in from the spreading edge; below zero the blood hasn't got there yet.
        float inward = (level - tex.g) * Spread;
        if (inward < 0.0) discard;
        // Blood dries from the edge in: as the wet shine goes, a darker crust creeps in from the rim and stays.
        float dryness = 1.0 - wetness;
        float rim = 1.0 + 3.0 * dryness;
        if (Soft > 0.5) {
            alpha *= clamp(inward / 1.5, 0.0, 1.0);
            crust = (0.82 + 0.18 * clamp(inward / 3.0, 0.0, 1.0)) * (1.0 - 0.26 * dryness * (1.0 - smoothstep(0.0, rim + 1.0, inward)));
            wet *= smoothstep(rim * 0.5, rim + 1.5, inward);
        } else {
            vec2 step = 1.0 / vec2(textureSize(Sampler0, 0));
            if (inward < 1.0) {
                // The outline: a little darker where it faces up and left in the texture, a step elsewhere.
                vec4 left = texture(Sampler0, texCoord0 - vec2(step.x, 0.0));
                vec4 up = texture(Sampler0, texCoord0 - vec2(0.0, step.y));
                bool lit = left.a < 0.004 || left.g > level || up.a < 0.004 || up.g > level;
                crust = lit ? 0.9 : 0.8;
                wet = 0.0;
            } else {
                if (inward < 2.0) {
                    // Just inside the lit edge the meniscus catches the light.
                    vec4 left = texture(Sampler0, texCoord0 - vec2(2.0 * step.x, 0.0));
                    vec4 up = texture(Sampler0, texCoord0 - vec2(0.0, 2.0 * step.y));
                    if (left.a < 0.004 || left.g > level || up.a < 0.004 || up.g > level) shade *= 1.06;
                }
                if (inward < floor(rim)) {
                    crust = 1.0 - 0.12 * dryness;
                    wet = 0.0;
                }
            }
        }
    }
    shade = (shade + gloss * wet) * 1.6 * crust;
    vec3 rgb = vertexColor.rgb * shade;
    if (Soft < 0.5) {
        // A slight hue shift, as pixel palettes do: highlights a little warmer, shadows a little cooler.
        float lum = dot(vertexColor.rgb, vec3(0.299, 0.587, 0.114));
        rgb += max(shade - 1.0, 0.0) * lum * vec3(0.5, 0.7, 0.4);
        rgb *= mix(vec3(1.0), vec3(0.98, 0.92, 1.12), clamp(1.0 - shade, 0.0, 1.0));
    }
    if (Specular > 0.0 && wet > 0.0 && billboard < 0.5) rgb = reflection(rgb, wet, alongX, alongY);
    vec4 color = vec4(rgb, vertexColor.a * alpha) * ColorModulator;
    if (color.a < 0.004) discard;
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
