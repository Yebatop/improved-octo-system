package dev.skirmish.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.List;

/**
 * Turns effect particles into quads (position + colour) facing the viewer: {@code right}/{@code up} are the view's
 * axes in the particles' space, {@code ox, oy, oz} is subtracted from positions (the camera in the world, 0 in the
 * Studio). Glowing shapes go to {@code glow} (additive), painted ones to {@code paint}. Triangles are quads with a
 * repeated corner.
 */
public final class FxGeometry {
    private static final int SEGMENTS = 20;
    private static final float[] COS = new float[SEGMENTS + 1];
    private static final float[] SIN = new float[SEGMENTS + 1];
    private static final int RING_SEGMENTS = 28;
    /** How strongly glowing shapes are also painted (see {@link #draw}). */
    private static final float TINT = 0.4f;

    static {
        for (int i = 0; i <= SEGMENTS; i++) {
            double a = i * Math.PI * 2 / SEGMENTS;
            COS[i] = (float) Math.cos(a);
            SIN[i] = (float) Math.sin(a);
        }
    }

    private FxGeometry() {
    }

    public static void draw(List<Fx> particles, boolean additive, Matrix4f m, VertexConsumer c,
                            double ox, double oy, double oz, Vector3f right, Vector3f up, Vector3f forward) {
        for (Fx fx : particles) {
            // The painted pass also lays a faint coat of the glowing shapes, so they keep their colour on bright
            // backgrounds (snow, sky) where added light alone washes out.
            boolean coat = !additive && fx.shape.additive;
            if (fx.shape.additive != additive && !coat) {
                continue;
            }
            int color = coat ? FxColor.alpha(fx.color(), ((fx.color() >>> 24) / 255f) * TINT) : fx.color();
            if ((color >>> 24) < 2) {
                continue;
            }
            float x = (float) (fx.x - ox);
            float y = (float) (fx.y - oy);
            float z = (float) (fx.z - oz);
            float s = fx.size();
            if (s <= 0.0005f) {
                continue;
            }
            switch (fx.shape) {
                case GLOW -> {
                    // A soft falloff: bright inside, a long faint skirt, so even big glows have no visible edge.
                    int mid = FxColor.alpha(color, 0.38f);
                    disc(m, c, x, y, z, right, up, s * 0.45f, color, mid);
                    ringBand(m, c, x, y, z, right, up, s * 0.45f, s, mid, color & 0x00FFFFFF);
                    disc(m, c, x, y, z, right, up, s * 0.22f, FxColor.lighten(color, 0.6f), FxColor.alpha(FxColor.lighten(color, 0.6f), 0f));
                }
                case DOT -> {
                    disc(m, c, x, y, z, right, up, s * 0.55f, color, color);
                    ringBand(m, c, x, y, z, right, up, s * 0.55f, s, color, color & 0x00FFFFFF);
                }
                case SMOKE -> disc(m, c, x, y, z, right, up, s, color, color & 0x00FFFFFF);
                case SPARK -> spark(m, c, fx, x, y, z, right, up, forward, s, color);
                case STAR -> star(m, c, x, y, z, right, up, s, fx.rot, color);
                case RING -> flatRing(m, c, x, y, z, s, color);
                case FACE_RING -> ringBand(m, c, x, y, z, right, up, s * 0.82f, s, color & 0x00FFFFFF, color);
                case SHARD -> shard(m, c, x, y, z, right, up, s, fx.rot, (float) Math.cos(fx.rot * 1.7f), color);
                case SQUARE -> square(m, c, x, y, z, right, up, s, fx.rot, (float) Math.cos(fx.rot * 2.3f), color);
            }
        }
    }

    private static void v(Matrix4f m, VertexConsumer c, float x, float y, float z, int color) {
        c.addVertex(m, x, y, z).setColor(color);
    }

    /** A disc as a fan of quads: {@code inner} colour in the middle, {@code outer} at the rim. */
    private static void disc(Matrix4f m, VertexConsumer c, float x, float y, float z, Vector3f r, Vector3f u, float radius,
                             int inner, int outer) {
        int step = radius < 0.25f ? 2 : 1;
        for (int i = 0; i < SEGMENTS; i += step) {
            int j = i + step;
            float ax = (COS[i] * r.x + SIN[i] * u.x) * radius;
            float ay = (COS[i] * r.y + SIN[i] * u.y) * radius;
            float az = (COS[i] * r.z + SIN[i] * u.z) * radius;
            float bx = (COS[j] * r.x + SIN[j] * u.x) * radius;
            float by = (COS[j] * r.y + SIN[j] * u.y) * radius;
            float bz = (COS[j] * r.z + SIN[j] * u.z) * radius;
            v(m, c, x, y, z, inner);
            v(m, c, x + ax, y + ay, z + az, outer);
            v(m, c, x + bx, y + by, z + bz, outer);
            v(m, c, x, y, z, inner);
        }
    }

    /** A band between two radii facing the viewer. */
    private static void ringBand(Matrix4f m, VertexConsumer c, float x, float y, float z, Vector3f r, Vector3f u,
                                 float inner, float outer, int innerColor, int outerColor) {
        int step = outer < 0.25f ? 2 : 1;
        for (int i = 0; i < SEGMENTS; i += step) {
            int j = i + step;
            float cx0 = COS[i] * r.x + SIN[i] * u.x;
            float cy0 = COS[i] * r.y + SIN[i] * u.y;
            float cz0 = COS[i] * r.z + SIN[i] * u.z;
            float cx1 = COS[j] * r.x + SIN[j] * u.x;
            float cy1 = COS[j] * r.y + SIN[j] * u.y;
            float cz1 = COS[j] * r.z + SIN[j] * u.z;
            v(m, c, x + cx0 * inner, y + cy0 * inner, z + cz0 * inner, innerColor);
            v(m, c, x + cx0 * outer, y + cy0 * outer, z + cz0 * outer, outerColor);
            v(m, c, x + cx1 * outer, y + cy1 * outer, z + cz1 * outer, outerColor);
            v(m, c, x + cx1 * inner, y + cy1 * inner, z + cz1 * inner, innerColor);
        }
    }

    /** A flat ring on the ground: a bright band fading both ways, radius {@code radius}. */
    private static void flatRing(Matrix4f m, VertexConsumer c, float x, float y, float z, float radius, int color) {
        float width = Math.max(0.04f, radius * 0.12f);
        int clear = color & 0x00FFFFFF;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            double a0 = i * Math.PI * 2 / RING_SEGMENTS;
            double a1 = (i + 1) * Math.PI * 2 / RING_SEGMENTS;
            float c0 = (float) Math.cos(a0);
            float s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1);
            float s1 = (float) Math.sin(a1);
            float rIn = Math.max(0f, radius - width);
            float rOut = radius + width;
            v(m, c, x + c0 * rIn, y, z + s0 * rIn, clear);
            v(m, c, x + c0 * radius, y, z + s0 * radius, color);
            v(m, c, x + c1 * radius, y, z + s1 * radius, color);
            v(m, c, x + c1 * rIn, y, z + s1 * rIn, clear);
            v(m, c, x + c0 * radius, y, z + s0 * radius, color);
            v(m, c, x + c0 * rOut, y, z + s0 * rOut, clear);
            v(m, c, x + c1 * rOut, y, z + s1 * rOut, clear);
            v(m, c, x + c1 * radius, y, z + s1 * radius, color);
        }
    }

    /** A streak from the particle back along its motion as seen by the viewer. */
    private static void spark(Matrix4f m, VertexConsumer c, Fx fx, float x, float y, float z, Vector3f r, Vector3f u,
                              Vector3f f, float width, int color) {
        float vx = (float) fx.vx;
        float vy = (float) fx.vy;
        float vz = (float) fx.vz;
        float along = vx * f.x + vy * f.y + vz * f.z;
        vx -= along * f.x;
        vy -= along * f.y;
        vz -= along * f.z;
        float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        float dx;
        float dy;
        float dz;
        if (speed < 1e-3f) {
            dx = u.x;
            dy = u.y;
            dz = u.z;
            speed = 0f;
        } else {
            dx = vx / speed;
            dy = vy / speed;
            dz = vz / speed;
        }
        float len = Math.max(width * 2f, speed * fx.stretch);
        // Side vector: the direction turned a quarter in the view plane.
        float sx = dy * f.z - dz * f.y;
        float sy = dz * f.x - dx * f.z;
        float sz = dx * f.y - dy * f.x;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-4f) {
            sx = r.x;
            sy = r.y;
            sz = r.z;
            sl = 1f;
        }
        sx = sx / sl * width;
        sy = sy / sl * width;
        sz = sz / sl * width;
        int clear = color & 0x00FFFFFF;
        int head = FxColor.lighten(color, 0.5f);
        v(m, c, x + sx, y + sy, z + sz, color);
        v(m, c, x + dx * width, y + dy * width, z + dz * width, head);
        v(m, c, x - sx, y - sy, z - sz, color);
        v(m, c, x - dx * len, y - dy * len, z - dz * len, clear);
    }

    private static void star(Matrix4f m, VertexConsumer c, float x, float y, float z, Vector3f r, Vector3f u, float size,
                             float rot, int color) {
        float cos = (float) Math.cos(rot);
        float sin = (float) Math.sin(rot);
        // Rotated axes in the view plane.
        float ax = r.x * cos + u.x * sin;
        float ay = r.y * cos + u.y * sin;
        float az = r.z * cos + u.z * sin;
        float bx = u.x * cos - r.x * sin;
        float by = u.y * cos - r.y * sin;
        float bz = u.z * cos - r.z * sin;
        float thin = size * 0.18f;
        int tip = color & 0x00FFFFFF | 0x30000000;
        int core = FxColor.lighten(color, 0.7f);
        // Two long thin diamonds crossed.
        diamond(m, c, x, y, z, ax, ay, az, bx, by, bz, size, thin, core, tip);
        diamond(m, c, x, y, z, bx, by, bz, ax, ay, az, size, thin, core, tip);
        disc(m, c, x, y, z, r, u, size * 0.45f, FxColor.alpha(color, 0.8f), color & 0x00FFFFFF);
    }

    private static void diamond(Matrix4f m, VertexConsumer c, float x, float y, float z, float ax, float ay, float az,
                                float bx, float by, float bz, float len, float thin, int core, int tip) {
        v(m, c, x + ax * len, y + ay * len, z + az * len, tip);
        v(m, c, x + bx * thin, y + by * thin, z + bz * thin, core);
        v(m, c, x - ax * len, y - ay * len, z - az * len, tip);
        v(m, c, x - bx * thin, y - by * thin, z - bz * thin, core);
    }

    private static void shard(Matrix4f m, VertexConsumer c, float x, float y, float z, Vector3f r, Vector3f u, float size,
                              float rot, float flip, int color) {
        float cos = (float) Math.cos(rot);
        float sin = (float) Math.sin(rot);
        float[] px = {0f, 0.85f * flip, -0.6f * flip};
        float[] py = {1f, -0.55f, -0.4f};
        float[][] pts = new float[3][3];
        for (int i = 0; i < 3; i++) {
            float lx = (px[i] * cos - py[i] * sin) * size;
            float ly = (px[i] * sin + py[i] * cos) * size;
            pts[i][0] = x + r.x * lx + u.x * ly;
            pts[i][1] = y + r.y * lx + u.y * ly;
            pts[i][2] = z + r.z * lx + u.z * ly;
        }
        int edge = FxColor.lighten(color, 0.45f);
        v(m, c, pts[0][0], pts[0][1], pts[0][2], edge);
        v(m, c, pts[1][0], pts[1][1], pts[1][2], color);
        v(m, c, pts[2][0], pts[2][1], pts[2][2], color);
        v(m, c, pts[2][0], pts[2][1], pts[2][2], color);
    }

    private static void square(Matrix4f m, VertexConsumer c, float x, float y, float z, Vector3f r, Vector3f u, float size,
                               float rot, float flip, int color) {
        float cos = (float) Math.cos(rot);
        float sin = (float) Math.sin(rot);
        float hw = size * flip;
        float hh = size * 0.6f;
        float[][] corners = {{-hw, -hh}, {hw, -hh}, {hw, hh}, {-hw, hh}};
        for (float[] k : corners) {
            float lx = k[0] * cos - k[1] * sin;
            float ly = k[0] * sin + k[1] * cos;
            v(m, c, x + r.x * lx + u.x * ly, y + r.y * lx + u.y * ly, z + r.z * lx + u.z * ly, color);
        }
    }
}
