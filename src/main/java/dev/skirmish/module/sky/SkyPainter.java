package dev.skirmish.module.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Random;

/**
 * The sky's geometry, in camera-relative space at distance {@code r}: stars, the galaxy band, aurora curtains, the
 * sunset glow and the quasar. Colours are additive (alpha scales the light). Everything is regenerated per frame
 * from seeded tables, which is cheap for a few thousand quads.
 */
final class SkyPainter {
    private static final int STARS = 1400;
    private static final int DUST = 700;
    private static final float[][] STAR = new float[STARS][];
    private static final float[][] DUSTS = new float[DUST][];
    private static final int[] DUST_COLORS = {0x8C6BFF, 0x5B7CFF, 0xFF6BC8, 0xB5C8FF};
    private static final int[] STAR_COLORS = {0xFFFFFF, 0xCFE0FF, 0xFFE7C8, 0xDCCFFF};

    static {
        Random random = new Random(0xA57E);
        for (int i = 0; i < STARS; i++) {
            double az = random.nextDouble() * Math.PI * 2;
            double el = Math.asin(random.nextDouble() * 1.15 - 0.15);
            float size = 0.6f + (float) Math.pow(random.nextDouble(), 4) * 2.2f;
            STAR[i] = new float[]{(float) az, (float) el, size, random.nextFloat() * 6.28f, 0.5f + random.nextFloat() * 2f, random.nextInt(STAR_COLORS.length)};
        }
        float[] n = SkyMath.normalize(new float[]{0.35f, 0.75f, 0.55f});
        float[][] b = SkyMath.basis(n);
        for (int i = 0; i < DUST; i++) {
            double th = random.nextDouble() * Math.PI * 2;
            double spread = random.nextGaussian() * 0.07;
            float[] p = SkyMath.normalize(new float[]{
                    (float) (Math.cos(th) * b[0][0] + Math.sin(th) * b[1][0] + spread * n[0]),
                    (float) (Math.cos(th) * b[0][1] + Math.sin(th) * b[1][1] + spread * n[1]),
                    (float) (Math.cos(th) * b[0][2] + Math.sin(th) * b[1][2] + spread * n[2])});
            DUSTS[i] = new float[]{p[0], p[1], p[2], 2f + random.nextFloat() * 5f, random.nextInt(DUST_COLORS.length), 0.3f + random.nextFloat() * 0.7f};
        }
    }

    private SkyPainter() {
    }

    /** A camera-facing square at direction {@code d} (unit) and distance {@code r}. */
    static void spot(VertexConsumer c, PoseStack.Pose p, float[] d, float r, float half, int color) {
        float[][] b = SkyMath.basis(d);
        float cx = d[0] * r;
        float cy = d[1] * r;
        float cz = d[2] * r;
        float ux = b[0][0] * half;
        float uy = b[0][1] * half;
        float uz = b[0][2] * half;
        float vx = b[1][0] * half;
        float vy = b[1][1] * half;
        float vz = b[1][2] * half;
        c.addVertex(p, cx - ux - vx, cy - uy - vy, cz - uz - vz).setColor(color);
        c.addVertex(p, cx + ux - vx, cy + uy - vy, cz + uz - vz).setColor(color);
        c.addVertex(p, cx + ux + vx, cy + uy + vy, cz + uz + vz).setColor(color);
        c.addVertex(p, cx - ux + vx, cy - uy + vy, cz - uz + vz).setColor(color);
    }

    /** A round glow: fan of degenerate quads, {@code centre} colour in the middle fading to transparent. */
    static void glow(VertexConsumer c, PoseStack.Pose p, float[] d, float r, float radius, int centre, int segments) {
        float[][] b = SkyMath.basis(d);
        float cx = d[0] * r;
        float cy = d[1] * r;
        float cz = d[2] * r;
        int edge = centre & 0xFFFFFF;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2 * i / segments;
            double a1 = Math.PI * 2 * (i + 1) / segments;
            float x0 = cx + (float) ((Math.cos(a0) * b[0][0] + Math.sin(a0) * b[1][0]) * radius);
            float y0 = cy + (float) ((Math.cos(a0) * b[0][1] + Math.sin(a0) * b[1][1]) * radius);
            float z0 = cz + (float) ((Math.cos(a0) * b[0][2] + Math.sin(a0) * b[1][2]) * radius);
            float x1 = cx + (float) ((Math.cos(a1) * b[0][0] + Math.sin(a1) * b[1][0]) * radius);
            float y1 = cy + (float) ((Math.cos(a1) * b[0][1] + Math.sin(a1) * b[1][1]) * radius);
            float z1 = cz + (float) ((Math.cos(a1) * b[0][2] + Math.sin(a1) * b[1][2]) * radius);
            c.addVertex(p, cx, cy, cz).setColor(centre);
            c.addVertex(p, x0, y0, z0).setColor(edge);
            c.addVertex(p, x1, y1, z1).setColor(edge);
            c.addVertex(p, cx, cy, cz).setColor(centre);
        }
    }

    static void stars(VertexConsumer c, PoseStack.Pose p, float r, float t, float strength) {
        stars(c, p, r, t, strength, 0f);
    }

    /** The starfield, turned with the night sky by {@code angle} (radians, the game's star angle). */
    static void stars(VertexConsumer c, PoseStack.Pose p, float r, float t, float strength, float angle) {
        float unit = r * 0.0016f;
        for (float[] s : STAR) {
            float tw = 0.7f + 0.3f * (float) Math.sin(t * s[4] + s[3]);
            int a = Math.round(255 * strength * tw * Math.min(1f, 0.35f + s[2] * 0.3f));
            if (a < 4) {
                continue;
            }
            // Round, soft stars: a small fan bright in the middle; the bigger ones get a faint halo too.
            float[] d = SkyMath.celestial(SkyMath.dir(s[0], s[1]), angle);
            glow(c, p, d, r, unit * s[2] * 1.5f, SkyMath.argb(a, STAR_COLORS[(int) s[5]]), 7);
            if (s[2] > 1.6f) {
                glow(c, p, d, r, unit * s[2] * 3.5f, SkyMath.argb(a / 5, STAR_COLORS[(int) s[5]]), 8);
            }
        }
    }

    static void galaxy(VertexConsumer c, PoseStack.Pose p, float r, float strength) {
        galaxy(c, p, r, strength, 0f);
    }

    /** The galaxy's dust clouds and bulge, turned with the night sky by {@code angle}. */
    static void galaxy(VertexConsumer c, PoseStack.Pose p, float r, float strength, float angle) {
        for (float[] d : DUSTS) {
            int a = Math.round(75 * strength * d[5]);
            glow(c, p, SkyMath.celestial(new float[]{d[0], d[1], d[2]}, angle), r, r * 0.014f * d[3], SkyMath.argb(a, DUST_COLORS[(int) d[4]]), 6);
        }
        float[] core = SkyMath.normalize(new float[]{DUSTS[0][0], DUSTS[0][1] + 0.1f, DUSTS[0][2]});
        glow(c, p, SkyMath.celestial(core, angle), r, r * 0.12f, SkyMath.argb(Math.round(60 * strength), 0xB89CFF), 24);
    }

    /**
     * Aurora curtains: three wavy ribbons along the northern sky, each made of thin vertical rays that flicker, bright
     * green at the foot fading to violet at the top.
     */
    static void aurora(VertexConsumer c, PoseStack.Pose p, float r, float t, float strength) {
        int[] foot = {0x3DFFB0, 0x4FE8C8, 0x7CFF9C};
        int[] crown = {0xB07CFF, 0x5B7CFF, 0xFF6BD5};
        for (int k = 0; k < 3; k++) {
            double centre = Math.PI + (k - 1) * 0.62 + 0.18 * Math.sin(t * 0.04 + k);
            double width = 1.5 - k * 0.22;
            int steps = 150;
            for (int i = 0; i < steps; i++) {
                double a0 = centre - width / 2 + width * i / steps;
                double a1 = centre - width / 2 + width * (i + 1) / steps;
                double base0 = Math.toRadians(12 + k * 7 + 6 * Math.sin(a0 * 4 + t * 0.35 + k));
                double base1 = Math.toRadians(12 + k * 7 + 6 * Math.sin(a1 * 4 + t * 0.35 + k));
                double h0 = Math.toRadians(22 + 10 * Math.sin(a0 * 3 - t * 0.25 + k * 2));
                double h1 = Math.toRadians(22 + 10 * Math.sin(a1 * 3 - t * 0.25 + k * 2));
                float edge = (float) Math.sin(Math.PI * i / steps);
                // Rays: a flickering comb across the curtain.
                float ray = 0.35f + 0.65f * (float) Math.pow(0.5 + 0.5 * Math.sin(a0 * 60 + k * 7 + t * (0.8 + k * 0.3)), 3);
                float wave = 0.7f + 0.3f * (float) Math.sin(a0 * 9 + t * 1.1 + k);
                int alphaFoot = Math.round(170 * strength * edge * wave * ray);
                float[] b0 = SkyMath.dir(a0, base0);
                float[] b1 = SkyMath.dir(a1, base1);
                float[] m0 = SkyMath.dir(a0, base0 + h0 * 0.35);
                float[] m1 = SkyMath.dir(a1, base1 + h1 * 0.35);
                float[] t0 = SkyMath.dir(a0, base0 + h0);
                float[] t1 = SkyMath.dir(a1, base1 + h1);
                int cFoot = SkyMath.argb(alphaFoot, foot[k]);
                int cMid = SkyMath.argb(Math.round(alphaFoot * 0.6f), SkyStyle.lerp(foot[k], crown[k], 0.4f));
                int cTop = SkyMath.argb(0, crown[k]);
                c.addVertex(p, b0[0] * r, b0[1] * r, b0[2] * r).setColor(cFoot);
                c.addVertex(p, b1[0] * r, b1[1] * r, b1[2] * r).setColor(cFoot);
                c.addVertex(p, m1[0] * r, m1[1] * r, m1[2] * r).setColor(cMid);
                c.addVertex(p, m0[0] * r, m0[1] * r, m0[2] * r).setColor(cMid);
                c.addVertex(p, m0[0] * r, m0[1] * r, m0[2] * r).setColor(cMid);
                c.addVertex(p, m1[0] * r, m1[1] * r, m1[2] * r).setColor(cMid);
                c.addVertex(p, t1[0] * r, t1[1] * r, t1[2] * r).setColor(cTop);
                c.addVertex(p, t0[0] * r, t0[1] * r, t0[2] * r).setColor(cTop);
            }
        }
    }

    /** Warm band along the horizon (all around), strongest in the west. */
    static void sunset(VertexConsumer c, PoseStack.Pose p, float r, float strength) {
        int steps = 96;
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2 * i / steps;
            double a1 = Math.PI * 2 * (i + 1) / steps;
            float west = 0.45f + 0.55f * (float) Math.max(0, Math.cos(a0 - Math.PI / 2));
            float[][] rows = {{-4, 0}, {3, 0}, {14, 0}, {32, 0}};
            int[] cols = {0xFF7A3D, 0xFF9A5C, 0xFF6BA8, 0x7C5CFF};
            int[] alphas = {140, 120, 60, 0};
            for (int k = 0; k < 3; k++) {
                double e0 = Math.toRadians(rows[k][0]);
                double e1 = Math.toRadians(rows[k + 1][0]);
                int c0 = SkyMath.argb(Math.round(alphas[k] * strength * west), cols[k]);
                int c1 = SkyMath.argb(Math.round(alphas[k + 1] * strength * west), cols[k + 1]);
                float[] p00 = SkyMath.dir(a0, e0);
                float[] p10 = SkyMath.dir(a1, e0);
                float[] p11 = SkyMath.dir(a1, e1);
                float[] p01 = SkyMath.dir(a0, e1);
                c.addVertex(p, p00[0] * r, p00[1] * r, p00[2] * r).setColor(c0);
                c.addVertex(p, p10[0] * r, p10[1] * r, p10[2] * r).setColor(c0);
                c.addVertex(p, p11[0] * r, p11[1] * r, p11[2] * r).setColor(c1);
                c.addVertex(p, p01[0] * r, p01[1] * r, p01[2] * r).setColor(c1);
            }
        }
    }

    /** The quasar: glow, white core, tilted accretion disk with orbiting clumps and two long jets. */
    static void quasar(VertexConsumer c, PoseStack.Pose p, float r, float t, float strength) {
        float[] d = SkyMath.dir(Math.toRadians(40), Math.toRadians(38));
        float[][] b = SkyMath.basis(d);
        float size = r * 0.085f;
        glow(c, p, d, r, size * 3.2f, SkyMath.argb(Math.round(90 * strength), 0x9C7CFF), 32);
        glow(c, p, d, r, size * 1.1f, SkyMath.argb(Math.round(200 * strength), 0xCFE6FF), 24);
        glow(c, p, d, r, size * 0.5f, SkyMath.argb(Math.round(255 * strength), 0xFFFFFF), 16);
        glow(c, p, d, r, size * 0.22f, SkyMath.argb(Math.round(255 * strength), 0xFFFFFF), 12);
        // Disk plane: tilted around the sky direction.
        double tilt = Math.toRadians(-20);
        float[] ax = rotate(b[0], b[1], tilt);
        float[] ay = rotate(b[1], new float[]{-b[0][0], -b[0][1], -b[0][2]}, tilt);
        float[] centre = {d[0] * r, d[1] * r, d[2] * r};
        float pulse = 0.8f + 0.2f * (float) Math.sin(t * 2.1f);
        for (int ring = 0; ring < 3; ring++) {
            float rad = size * (2.2f - ring * 0.45f);
            int alpha = Math.round((ring == 1 ? 170 : 90) * strength);
            ellipse(c, p, centre, ax, ay, rad, rad * 0.26f, 0, 360, 96, size * 0.05f, SkyMath.argb(alpha, 0xA98CFF));
        }
        for (int k = 0; k < 3; k++) {
            float start = (t * 50f + k * 120f) % 360f;
            ellipse(c, p, centre, ax, ay, size * 1.75f, size * 1.75f * 0.26f, start, start + 36f, 10, size * 0.09f,
                    SkyMath.argb(Math.round(230 * strength), 0xE6F0FF));
        }
        // Jets along the disk's axis (ay), fading outwards.
        for (int side = -1; side <= 1; side += 2) {
            int n = 20;
            for (int i = 0; i < n; i++) {
                float f0 = i / (float) n;
                float f1 = (i + 1) / (float) n;
                float len = size * 9f;
                float w0 = size * 0.12f * (1f - f0 * 0.7f);
                float w1 = size * 0.12f * (1f - f1 * 0.7f);
                int c0 = SkyMath.argb(Math.round(210 * strength * pulse * (1 - f0) * (1 - f0)), 0xCFE6FF);
                int c1 = SkyMath.argb(Math.round(210 * strength * pulse * (1 - f1) * (1 - f1)), 0xCFE6FF);
                float[] a0 = add(centre, scale(ay, side * len * f0));
                float[] a1 = add(centre, scale(ay, side * len * f1));
                quadStrip(c, p, a0, a1, ax, w0, w1, c0, c1);
            }
        }
    }

    private static void ellipse(VertexConsumer c, PoseStack.Pose p, float[] centre, float[] ax, float[] ay, float a, float bb,
                                float fromDeg, float toDeg, int steps, float width, int color) {
        for (int i = 0; i < steps; i++) {
            double t0 = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps);
            double t1 = Math.toRadians(fromDeg + (toDeg - fromDeg) * (i + 1) / steps);
            float[] p0 = add(centre, add(scale(ax, (float) (a * Math.cos(t0))), scale(ay, (float) (bb * Math.sin(t0)))));
            float[] p1 = add(centre, add(scale(ax, (float) (a * Math.cos(t1))), scale(ay, (float) (bb * Math.sin(t1)))));
            float[] dir = SkyMath.normalize(new float[]{p1[0] - p0[0], p1[1] - p0[1], p1[2] - p0[2]});
            float[] toCam = SkyMath.normalize(new float[]{-p0[0], -p0[1], -p0[2]});
            float[] side = SkyMath.normalize(SkyMath.cross(dir, toCam));
            quadStrip(c, p, p0, p1, side, width, width, color, color);
        }
    }

    private static void quadStrip(VertexConsumer c, PoseStack.Pose p, float[] a, float[] b, float[] side, float wa, float wb, int ca, int cb) {
        c.addVertex(p, a[0] - side[0] * wa, a[1] - side[1] * wa, a[2] - side[2] * wa).setColor(ca);
        c.addVertex(p, a[0] + side[0] * wa, a[1] + side[1] * wa, a[2] + side[2] * wa).setColor(ca);
        c.addVertex(p, b[0] + side[0] * wb, b[1] + side[1] * wb, b[2] + side[2] * wb).setColor(cb);
        c.addVertex(p, b[0] - side[0] * wb, b[1] - side[1] * wb, b[2] - side[2] * wb).setColor(cb);
    }

    private static float[] rotate(float[] u, float[] v, double angle) {
        float c = (float) Math.cos(angle);
        float s = (float) Math.sin(angle);
        return new float[]{u[0] * c + v[0] * s, u[1] * c + v[1] * s, u[2] * c + v[2] * s};
    }

    private static float[] add(float[] a, float[] b) {
        return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
    }

    private static float[] scale(float[] a, float k) {
        return new float[]{a[0] * k, a[1] * k, a[2] * k};
    }
}
