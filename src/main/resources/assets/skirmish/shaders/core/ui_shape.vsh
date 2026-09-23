#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection {
    mat4 ProjMat;
};

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

out vec4 vertexColor;
out vec2 localPos;
flat out vec4 shape;
flat out float mode;
flat out float rounding;

// UV0: fragment position in design px, relative to the box center (box) or to point A (segment).
// UV1/UV2: 1/16 px fixed point. Box: UV1 = half size, UV2 = (radius, stroke width; 0 = filled).
// Segment: UV1 = B - A, UV2.x = half thickness. Triangle: UV1 = B - A, UV2 = C - A, Normal.y * 8 = rounding.
// Normal.x selects the mode (0 box, 0.25 triangle, 0.5 segment, 1 inverted box).
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vertexColor = Color;
    localPos = UV0;
    shape = vec4(vec2(UV1) / 16.0, vec2(UV2) / 16.0);
    mode = Normal.x;
    rounding = Normal.y * 8.0;
}
