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
flat in float rounding;

out vec4 fragColor;

float roundBox(vec2 p, vec2 halfSize, float radius) {
    float r = min(radius, min(halfSize.x, halfSize.y));
    vec2 q = abs(p) - halfSize + r;
    return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;
}

// Signed distance to triangle (0, b, c) (Inigo Quilez).
float triangle(vec2 p, vec2 b, vec2 c) {
    vec2 e0 = b, e1 = c - b, e2 = -c;
    vec2 v0 = p, v1 = p - b, v2 = p - c;
    vec2 pq0 = v0 - e0 * clamp(dot(v0, e0) / dot(e0, e0), 0.0, 1.0);
    vec2 pq1 = v1 - e1 * clamp(dot(v1, e1) / dot(e1, e1), 0.0, 1.0);
    vec2 pq2 = v2 - e2 * clamp(dot(v2, e2) / dot(e2, e2), 0.0, 1.0);
    float s = sign(e0.x * e2.y - e0.y * e2.x);
    vec2 d = min(min(vec2(dot(pq0, pq0), s * (v0.x * e0.y - v0.y * e0.x)),
                     vec2(dot(pq1, pq1), s * (v1.x * e1.y - v1.y * e1.x))),
                     vec2(dot(pq2, pq2), s * (v2.x * e2.y - v2.y * e2.x)));
    return -sqrt(d.x) * sign(d.y);
}

void main() {
    float d;
    bool segment = mode > 0.375 && mode < 0.75;
    bool tri = mode > 0.125 && mode <= 0.375;
    if (tri) {
        d = triangle(localPos, shape.xy, shape.zw) - rounding;
    } else if (segment) {
        vec2 ba = shape.xy;
        float h = clamp(dot(localPos, ba) / max(dot(ba, ba), 1e-6), 0.0, 1.0);
        d = length(localPos - ba * h) - shape.z;
    } else {
        d = roundBox(localPos, shape.xy, shape.z);
    }
    // Analytic anti-aliasing: one screen pixel wide, independent of GUI scale.
    float fw = max(fwidth(d), 1e-4);
    float alpha = clamp(0.5 - d / fw, 0.0, 1.0);
    if (!segment && !tri && shape.w > 0.0) {
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
