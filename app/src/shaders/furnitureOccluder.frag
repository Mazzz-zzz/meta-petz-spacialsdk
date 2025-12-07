#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <data/shaders/common.glsl>

// Custom material uniform
layout (std140, set = 3, binding = 0) uniform MaterialUniform {
  vec4 occluderColor;    // base color with alpha (e.g., dark gray with 0.1 alpha)
  vec4 occluderParams;   // x = edge boost (0-1), y = unused, z = darken (0-1), w = reserved
} g_MaterialUniform;

layout(location = 0) in struct {
  vec4 color;
  vec2 albedoCoord;
  vec3 worldNormal;
  vec3 worldPosition;
} vertexOut;

layout (location = 0) out vec4 outColor;

void main() {
  // Get parameters
  float edgeBoost = g_MaterialUniform.occluderParams.x;      // 0 = no boost, 1 = max boost
  float darkenAmount = g_MaterialUniform.occluderParams.z;   // 0 = no darken, 1 = full darken

  // Use UV coordinates for edge detection (like edgeOnly shader)
  vec2 uv = vertexOut.albedoCoord;
  vec2 edgeDist = min(uv, 1.0 - uv);

  // Distance from nearest edge (0 at edge, 0.5 at center)
  float minEdgeDist = min(edgeDist.x, edgeDist.y);

  // Calculate edge factor (1.0 at edges, 0.0 at center)
  float edgeFactor = 1.0 - smoothstep(0.0, 0.15, minEdgeDist);

  // Base color from material
  vec4 baseColor = g_MaterialUniform.occluderColor;

  // Apply edge boost - edges are more visible
  float finalAlpha = baseColor.a + (edgeFactor * edgeBoost * 0.3);
  finalAlpha = clamp(finalAlpha, 0.0, 0.5);  // cap to keep subtle

  // Darken effect - mix toward darker color
  vec3 darkenedColor = mix(baseColor.rgb, vec3(0.0, 0.0, 0.0), darkenAmount * 0.5);

  // Output with calculated alpha
  outColor = vec4(darkenedColor, finalAlpha);
}
