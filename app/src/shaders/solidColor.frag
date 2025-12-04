#version 400

#include <data/shaders/metaSpatialSdkFragmentBase.glsl>

layout(set = 3, binding = 0) uniform MaterialUniform {
    vec4 color;
} uMaterial;

void main() {
    oColor = uMaterial.color;
}
