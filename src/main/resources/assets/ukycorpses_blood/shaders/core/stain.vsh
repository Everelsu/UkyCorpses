#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
// How the stain spreads: when its current ramp began, in ticks before the buffer was built (never positive), and how
// many ticks the ramp takes. A negative length ramps linearly, a positive one eases out, zero means it's done.
in ivec2 UV1;
in ivec2 UV2;
// Not a normal: the spread level the ramp starts and ends at (negative for stains that don't spread), and wetness.
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform int FogShape;
// Ticks since this buffer was built.
uniform float BuiltAgo;
// Towards the sun, or the moon at night, in the world's axes.
uniform vec3 SunDirection;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out float level;
out float wetness;
// For wet blood's reflection: where it is and where the sun is, in view space; how open to the sky and how lit by
// blocks it is; and whether it's a drop in the air facing the camera rather than blood on a surface.
out vec3 viewPosition;
out vec3 sunView;
out vec3 upView;
out float skyLight;
out float blockLight;
out float billboard;

void main() {
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * viewPos;

    // Fog wants the position relative to the camera in the world's axes. Stains are drawn section-local with the
    // section's offset in ModelViewMat, so it's the view position turned back by the view's rotation.
    vertexDistance = fog_distance(transpose(mat3(ModelViewMat)) * viewPos.xyz, FogShape);
    vertexColor = Color * minecraft_sample_lightmap(Sampler2, UV2);
    texCoord0 = UV0;

    float span = float(UV1.y);
    float t = span == 0.0 ? 1.0 : clamp((BuiltAgo - float(UV1.x)) / abs(span), 0.0, 1.0);
    if (span > 0.0) t = 1.0 - (1.0 - t) * (1.0 - t);
    level = Normal.y < 0.0 ? -1.0 : mix(Normal.x, Normal.y, t);
    wetness = Normal.z;
    viewPosition = viewPos.xyz;
    // ModelViewMat holds the view's rotation (and a section's offset, which a direction doesn't take).
    sunView = normalize(mat3(ModelViewMat) * SunDirection);
    upView = normalize(mat3(ModelViewMat) * vec3(0.0, 1.0, 0.0));
    skyLight = float(UV2.y) / 240.0;
    blockLight = float(UV2.x) / 240.0;
    billboard = Normal.x < -1.5 ? 1.0 : 0.0;
}
