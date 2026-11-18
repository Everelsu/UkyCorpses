#version 120
// Original SurfaceBlood vertex data adapted to Forge 1.7's fixed Tessellator layout.
uniform sampler2D Sampler2;
uniform vec3 SunDirection;
varying float vertexDistance;
varying vec4 vertexColor;
varying vec2 texCoord0;
varying float level;
varying float wetness;
varying vec3 viewPosition;
varying vec3 sunView;
varying vec3 upView;
varying float skyLight;
varying float blockLight;
varying float billboard;
void main() {
    vec4 position = gl_ModelViewMatrix * gl_Vertex;
    gl_Position = gl_ProjectionMatrix * position;
    vertexDistance = length(position.xyz);
    vertexColor = gl_Color * texture2D(Sampler2, (gl_MultiTexCoord1.xy + vec2(8.0)) / 256.0);
    texCoord0 = gl_MultiTexCoord0.xy;
    level = gl_Normal.x < -0.5 ? -1.0 : gl_Normal.y;
    wetness = gl_Normal.z;
    viewPosition = position.xyz;
    sunView = normalize(mat3(gl_ModelViewMatrix) * SunDirection);
    upView = normalize(mat3(gl_ModelViewMatrix) * vec3(0.0, 1.0, 0.0));
    skyLight = gl_MultiTexCoord1.y / 240.0;
    blockLight = gl_MultiTexCoord1.x / 240.0;
    billboard = gl_Normal.x < -0.5 ? 1.0 : 0.0;
}
