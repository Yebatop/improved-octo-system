#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 localPos;
flat in vec4 shape;
flat in float mode;

out vec4 fragColor;

float roundBox(vec2 p, vec2 halfSize, float radius) {
    float r = min(radius, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

void main() {
    float d;
    bool segment = mode > 0.25 && mode < 0.75;
    if (segment) {
        vec2 ba = shape.xy;
        float h = clamp(dot(localPos, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
        d = length(localPos - ba * h) - shape.z;
    } else {
        d = roundBox(localPos, shape.xy, shape.z);
    }
    // Analytic anti-aliasing: one screen pixel wide, independent of GUI scale.
    float fw = max(fwidth(d), 1e-4);
    float alpha = clamp(0.5 - d / fw, 0.0, 1.0);
    if (!segment && shape.w > 0.0) {
        alpha -= clamp(0.5 - (d + shape.w) / fw, 0.0, 1.0);
    }
    if (mode > 0.75) {
        alpha = 1.0 - alpha;
    }
    vec4 color = vertexColor * ColorModulator;
    color.a *= alpha;
    if (color.a <= 0.0) {
        discard;
    }
    fragColor = color;
}
