#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <data/shaders/common.glsl>

// Custom material uniform - vec4s are in set 3, binding 0
layout (std140, set = 3, binding = 0) uniform MaterialUniform {
  vec4 customColor;
} g_MaterialUniform;

layout(location = 0) in struct {
  vec4 color;
  vec2 albedoCoord;
  vec3 worldNormal;
  vec3 worldPosition;
} vertexOut;

layout (location = 0) out vec4 outColor;

void main() {
  // Use the custom color from the material attribute
  outColor = g_MaterialUniform.customColor;
}
