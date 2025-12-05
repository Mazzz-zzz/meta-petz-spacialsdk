#version 430
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <data/shaders/common.glsl>
#include <data/shaders/app2vertex.glsl>

layout(location = 0) out struct {
  vec4 color;
  vec2 albedoCoord;
  vec3 worldNormal;
  vec3 worldPosition;
} vertexOut;

// Custom material uniform - vec4s are in set 3, binding 0
layout (std140, set = 3, binding = 0) uniform MaterialUniform {
  vec4 customColor;
} g_MaterialUniform;

void main() {
  App2VertexUnpacked app = getApp2VertexUnpacked();

  vec4 wPos4 = g_PrimitiveUniform.worldFromObject * vec4(app.position, 1.0f);
  vertexOut.color = vec4(app.linearColor, 1.0f);
  vertexOut.albedoCoord = app.uv;
  vertexOut.worldPosition = wPos4.xyz;
  vertexOut.worldNormal = normalize((transpose(g_PrimitiveUniform.objectFromWorld) * vec4(app.normal, 0.0f)).xyz);

  gl_Position = getClipFromWorld() * wPos4;
  postprocessPosition(gl_Position);
}
