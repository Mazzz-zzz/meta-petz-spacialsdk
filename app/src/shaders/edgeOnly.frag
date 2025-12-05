#version 400
#extension GL_ARB_separate_shader_objects : enable
#extension GL_ARB_shading_language_420pack : enable

#include <data/shaders/common.glsl>

// Custom material uniform - vec4s are in set 3, binding 0
layout (std140, set = 3, binding = 0) uniform MaterialUniform {
  vec4 customColor;      // edge color (RGBA with alpha for transparency)
  vec4 edgeParams;       // x = thickness in meters, y/z/w = reserved
} g_MaterialUniform;

layout(location = 0) in struct {
  vec4 color;
  vec2 albedoCoord;
  vec3 worldNormal;
  vec3 worldPosition;
  vec3 objectPosition;
} vertexOut;

layout (location = 0) out vec4 outColor;

void main() {
  // Use UV coordinates for 2D quad edge detection (UVs go 0 to 1)
  vec2 uv = vertexOut.albedoCoord;

  // Distance from edges (0 at edges, 0.5 at center)
  vec2 edgeDist = min(uv, 1.0 - uv);

  // Thickness in world meters (default 2cm)
  float thicknessMeters = g_MaterialUniform.edgeParams.x;
  if (thicknessMeters <= 0.0) {
    thicknessMeters = 0.02;
  }

  // Convert world thickness to UV space using derivatives
  // fwidth(worldPosition) = world meters per pixel
  // fwidth(uv) = UV units per pixel
  // So: UV units per meter = fwidth(uv) / fwidth(worldPosition)
  vec2 uvRate = fwidth(uv);
  float worldRate = length(fwidth(vertexOut.worldPosition));

  // Thickness in UV space for each axis
  vec2 thicknessUV = thicknessMeters * uvRate / max(worldRate, 0.0001);

  // Check if near edge on either axis
  bool nearEdge = edgeDist.x < thicknessUV.x || edgeDist.y < thicknessUV.y;

  if (!nearEdge) {
    discard;
  }

  outColor = g_MaterialUniform.customColor;
}
