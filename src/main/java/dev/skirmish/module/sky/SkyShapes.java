package dev.skirmish.module.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

/**
 * Shapes on the sky sphere (camera-relative, radius {@code r}): the gradient dome, shaded spheres (moons and
 * planets lit from a direction, so phases and terminators come out by themselves), flat rings and disks, soft
 * bands along great circles and streaks. Colours are ARGB; blending comes from the render type they are drawn in.
 */
final class SkyShapes {
    private static final float[] ROWS = {-90, -30, -8, -3, 0, 3, 8, 15, 25, 40, 60, 90};

    private SkyShapes() {
    }

    /** Per-vertex colour of a sphere from its normal in the view frame (x right, y up, z towards the camera). */
    interface SphereShader {
        int argb(float nx, float ny, float nz);
    }

    /** Colour of a ring by angle (radians, 0 = right, π/2 = up) and position across it (0 inner … 1 outer). */
    interface RingShader {
        int argb(double angle, float across);
    }

    /** The whole sky: 48 slices × 11 rows from straight down to the zenith, coloured by the palette. */
    static void dome(VertexConsumer c, PoseStack.Pose p, float r, SkyStyle.Palette palette, int alpha) {
        int segments = 48;
        for (int k = 0; k + 1 < ROWS.length; k++) {
            float e0 = ROWS[k];
            float e1 = ROWS[k + 1];
            int c0 = SkyMath.argb(alpha, SkyStyle.dome(palette, e0));
            int c1 = SkyMath.argb(alpha, SkyStyle.dome(palette, e1));
            for (int i = 0; i < segments; i++) {
                double a0 = Math.PI * 2 * i / segments;
                double a1 = Math.PI * 2 * (i + 1) / segments;
                float[] p00 = SkyMath.dir(a0, Math.toRadians(e0));
                float[] p10 = SkyMath.dir(a1, Math.toRadians(e0));
                float[] p11 = SkyMath.dir(a1, Math.toRadians(e1));
                float[] p01 = SkyMath.dir(a0, Math.toRadians(e1));
                vertex(c, p, p00, r, c0);
                vertex(c, p, p10, r, c0);
                vertex(c, p, p11, r, c1);
                vertex(c, p, p01, r, c1);
            }
        }
    }

    /**
     * A sphere of angular radius {@code angle} (radians) in direction {@code d}: a polar grid on its disc, each
     * vertex coloured by {@code shader} from the sphere's normal there.
     */
    static void sphere(VertexConsumer c, PoseStack.Pose p, float[] d, float r, float angle, int rings, int segments, SphereShader shader) {
        float[][] b = SkyMath.basis(d);
        float radius = r * (float) Math.tan(angle);
        float cx = d[0] * r;
        float cy = d[1] * r;
        float cz = d[2] * r;
        for (int i = 0; i < rings; i++) {
            float t0 = i / (float) rings;
            float t1 = (i + 1) / (float) rings;
            for (int j = 0; j < segments; j++) {
                double a0 = Math.PI * 2 * j / segments;
                double a1 = Math.PI * 2 * (j + 1) / segments;
                sphereVertex(c, p, b, cx, cy, cz, radius, t0, a0, shader);
                sphereVertex(c, p, b, cx, cy, cz, radius, t0, a1, shader);
                sphereVertex(c, p, b, cx, cy, cz, radius, t1, a1, shader);
                sphereVertex(c, p, b, cx, cy, cz, radius, t1, a0, shader);
            }
        }
    }

    private static void sphereVertex(VertexConsumer c, PoseStack.Pose p, float[][] b, float cx, float cy, float cz, float radius,
                                     float t, double a, SphereShader shader) {
        // Radius on the disc: sin-spaced so the rim, where shading changes fastest, gets more rings.
        float rr = (float) Math.sin(t * Math.PI / 2);
        float x = rr * (float) Math.cos(a);
        float y = rr * (float) Math.sin(a);
        float z = (float) Math.sqrt(Math.max(0, 1 - x * x - y * y));
        int color = shader.argb(x, y, z);
        c.addVertex(p, cx + (b[0][0] * x + b[1][0] * y) * radius, cy + (b[0][1] * x + b[1][1] * y) * radius,
                cz + (b[0][2] * x + b[1][2] * y) * radius).setColor(color);
    }

    /**
     * A flat ring around {@code centre} (an absolute position) in the plane of {@code ax} and {@code ay}, the
     * {@code ay} half squashed by {@code squash} (seen at a slant), between angles {@code from} and {@code to}.
     */
    static void ring(VertexConsumer c, PoseStack.Pose p, float[] centre, float[] ax, float[] ay, float inner, float outer, float squash,
                     double from, double to, int steps, int bands, RingShader shader) {
        for (int k = 0; k < bands; k++) {
            float f0 = k / (float) bands;
            float f1 = (k + 1) / (float) bands;
            float r0 = inner + (outer - inner) * f0;
            float r1 = inner + (outer - inner) * f1;
            for (int i = 0; i < steps; i++) {
                double a0 = from + (to - from) * i / steps;
                double a1 = from + (to - from) * (i + 1) / steps;
                ringVertex(c, p, centre, ax, ay, r0, squash, a0, shader.argb(a0, f0));
                ringVertex(c, p, centre, ax, ay, r0, squash, a1, shader.argb(a1, f0));
                ringVertex(c, p, centre, ax, ay, r1, squash, a1, shader.argb(a1, f1));
                ringVertex(c, p, centre, ax, ay, r1, squash, a0, shader.argb(a0, f1));
            }
        }
    }

    private static void ringVertex(VertexConsumer c, PoseStack.Pose p, float[] o, float[] ax, float[] ay, float rad, float squash,
                                   double a, int color) {
        float cos = (float) Math.cos(a) * rad;
        float sin = (float) Math.sin(a) * rad * squash;
        c.addVertex(p, o[0] + ax[0] * cos + ay[0] * sin, o[1] + ax[1] * cos + ay[1] * sin, o[2] + ax[2] * cos + ay[2] * sin).setColor(color);
    }

    /**
     * A soft band along the great circle around {@code n}: {@code layers} strips from the centre line out to
     * {@code halfWidth} radians, the colour fading to nothing at the edges.
     */
    static void band(VertexConsumer c, PoseStack.Pose p, float r, float[] n, float halfWidth, int color, int steps, int layers) {
        float[][] b = SkyMath.basis(n);
        for (int side = -1; side <= 1; side += 2) {
            for (int k = 0; k < layers; k++) {
                float w0 = halfWidth * k / layers;
                float w1 = halfWidth * (k + 1) / layers;
                float fade0 = 1f - k / (float) layers;
                float fade1 = 1f - (k + 1) / (float) layers;
                int c0 = SkyMath.argb(Math.round(((color >>> 24) & 0xFF) * fade0 * fade0), color);
                int c1 = SkyMath.argb(Math.round(((color >>> 24) & 0xFF) * fade1 * fade1), color);
                for (int i = 0; i < steps; i++) {
                    double t0 = Math.PI * 2 * i / steps;
                    double t1 = Math.PI * 2 * (i + 1) / steps;
                    vertex(c, p, onBand(b, n, t0, side * w0), r, c0);
                    vertex(c, p, onBand(b, n, t1, side * w0), r, c0);
                    vertex(c, p, onBand(b, n, t1, side * w1), r, c1);
                    vertex(c, p, onBand(b, n, t0, side * w1), r, c1);
                }
            }
        }
    }

    /** A point at angle {@code t} along the great circle around {@code n}, {@code off} radians towards {@code n}. */
    static float[] onBand(float[][] b, float[] n, double t, float off) {
        float cos = (float) Math.cos(off);
        float sin = (float) Math.sin(off);
        float ct = (float) Math.cos(t);
        float st = (float) Math.sin(t);
        return new float[]{(b[0][0] * ct + b[1][0] * st) * cos + n[0] * sin, (b[0][1] * ct + b[1][1] * st) * cos + n[1] * sin,
                (b[0][2] * ct + b[1][2] * st) * cos + n[2] * sin};
    }

    /** A tapered streak from direction {@code a} to {@code b}, widths in blocks at the sky's distance. */
    static void streak(VertexConsumer c, PoseStack.Pose p, float r, float[] a, float[] b, float wa, float wb, int ca, int cb) {
        float[] pa = {a[0] * r, a[1] * r, a[2] * r};
        float[] pb = {b[0] * r, b[1] * r, b[2] * r};
        float[] along = SkyMath.normalize(new float[]{pb[0] - pa[0], pb[1] - pa[1], pb[2] - pa[2]});
        float[] side = SkyMath.normalize(SkyMath.cross(along, a));
        c.addVertex(p, pa[0] - side[0] * wa, pa[1] - side[1] * wa, pa[2] - side[2] * wa).setColor(ca);
        c.addVertex(p, pa[0] + side[0] * wa, pa[1] + side[1] * wa, pa[2] + side[2] * wa).setColor(ca);
        c.addVertex(p, pb[0] + side[0] * wb, pb[1] + side[1] * wb, pb[2] + side[2] * wb).setColor(cb);
        c.addVertex(p, pb[0] - side[0] * wb, pb[1] - side[1] * wb, pb[2] - side[2] * wb).setColor(cb);
    }

    /**
     * A streak with soft edges: bright along its middle line, fading to nothing at both sides (two quads), from
     * direction {@code a} to {@code b}, half-widths in blocks.
     */
    static void softStreak(VertexConsumer c, PoseStack.Pose p, float r, float[] a, float[] b, float wa, float wb, int ca, int cb) {
        float[] pa = {a[0] * r, a[1] * r, a[2] * r};
        float[] pb = {b[0] * r, b[1] * r, b[2] * r};
        float[] along = SkyMath.normalize(new float[]{pb[0] - pa[0], pb[1] - pa[1], pb[2] - pa[2]});
        float[] side = SkyMath.normalize(SkyMath.cross(along, a));
        int ea = ca & 0xFFFFFF;
        int eb = cb & 0xFFFFFF;
        for (int s = -1; s <= 1; s += 2) {
            c.addVertex(p, pa[0], pa[1], pa[2]).setColor(ca);
            c.addVertex(p, pa[0] + side[0] * wa * s, pa[1] + side[1] * wa * s, pa[2] + side[2] * wa * s).setColor(ea);
            c.addVertex(p, pb[0] + side[0] * wb * s, pb[1] + side[1] * wb * s, pb[2] + side[2] * wb * s).setColor(eb);
            c.addVertex(p, pb[0], pb[1], pb[2]).setColor(cb);
        }
    }

    /** A filled disc (fan) of one colour, angular radius in radians. */
    static void disc(VertexConsumer c, PoseStack.Pose p, float[] d, float r, float angle, int color, int segments) {
        float[][] b = SkyMath.basis(d);
        float radius = r * (float) Math.tan(angle);
        float cx = d[0] * r;
        float cy = d[1] * r;
        float cz = d[2] * r;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            c.addVertex(p, cx, cy, cz).setColor(color);
            c.addVertex(p, cx + (b[0][0] * (float) Math.cos(a0) + b[1][0] * (float) Math.sin(a0)) * radius,
                    cy + (b[0][1] * (float) Math.cos(a0) + b[1][1] * (float) Math.sin(a0)) * radius,
                    cz + (b[0][2] * (float) Math.cos(a0) + b[1][2] * (float) Math.sin(a0)) * radius).setColor(color);
            c.addVertex(p, cx + (b[0][0] * (float) Math.cos(a1) + b[1][0] * (float) Math.sin(a1)) * radius,
                    cy + (b[0][1] * (float) Math.cos(a1) + b[1][1] * (float) Math.sin(a1)) * radius,
                    cz + (b[0][2] * (float) Math.cos(a1) + b[1][2] * (float) Math.sin(a1)) * radius).setColor(color);
            c.addVertex(p, cx, cy, cz).setColor(color);
        }
    }

    static void vertex(VertexConsumer c, PoseStack.Pose p, float[] d, float r, int color) {
        c.addVertex(p, d[0] * r, d[1] * r, d[2] * r).setColor(color);
    }

    /** Deterministic noise in 0..1 for integer coordinates. */
    static float hash(int a, int b) {
        int h = a * 374761393 + b * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return ((h ^ (h >>> 16)) & 0xFFFFFF) / (float) 0xFFFFFF;
    }
}
