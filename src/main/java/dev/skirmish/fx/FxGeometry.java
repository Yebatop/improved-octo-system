package dev.skirmish.fx;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Turns an effects field into textured quads: sprites facing the viewer (or stretched along their motion, flat on
 * the ground, or in their own plane), ribbons as soft bands through their points, electric arcs as glowing bands with
 * a white core. {@code right/up/forward} are the view's axes in the field's space; {@code ox, oy, oz} is subtracted
 * from positions (the camera in the world, 0 in the Studio); {@code eye} is the camera position for ribbons in
 * perspective, or null in the Studio's flat view. Glowing things go to the additive pass, painted ones to the other,
 * which also lays a faint coat of the glowing ones so they keep their colour on snow and sky.
 */
public final class FxGeometry {
    /** How strongly glowing sprites are also painted. */
    private static final float COAT = 0.32f;
    /** A texel inside the atlas's solid white area, for plain coloured shapes drawn with the effect render types. */
    public static final float SOLID_U = FxSprite.RIBBON.u0 + FxSprite.RIBBON.du * 0.5f;
    public static final float SOLID_V = FxSprite.RIBBON.v0 + FxSprite.RIBBON.dv * 0.5f;

    private FxGeometry() {
    }

    public static void draw(FxField field, boolean additive, Matrix4f m, VertexConsumer c, double ox, double oy, double oz,
                            Vector3f right, Vector3f up, Vector3f forward, double @Nullable [] eye) {
        for (Fx fx : field.live()) {
            boolean coat = !additive && fx.add;
            if (fx.add != additive && !coat) {
                continue;
            }
            int color = fx.color();
            if (coat) {
                color = FxColor.alpha(color, ((color >>> 24) / 255f) * COAT);
            }
            if ((color >>> 24) < 2) {
                continue;
            }
            float s = fx.size();
            if (s <= 0.0005f) {
                continue;
            }
            float x = (float) (fx.x - ox);
            float y = (float) (fx.y - oy);
            float z = (float) (fx.z - oz);
            sprite(m, c, fx, x, y, z, s, color, right, up, forward);
            if (fx.core > 0 && !coat) {
                sprite(m, c, fx, x, y, z, s * fx.core, FxColor.lighten(color, 0.75f), right, up, forward);
            }
        }
        for (FxRibbon r : field.ribbons()) {
            if (r.add == additive || (!additive && r.add)) {
                boolean coat = !additive && r.add;
                ribbon(m, c, r, ox, oy, oz, forward, eye, coat ? COAT : 1f, 1f, false);
                if (r.core > 0 && !coat) {
                    ribbon(m, c, r, ox, oy, oz, forward, eye, 1f, r.core, true);
                }
            }
        }
        if (additive) {
            for (FxBolt b : field.bolts()) {
                bolt(m, c, b, ox, oy, oz, forward, eye);
            }
        }
    }

    private static void v(VertexConsumer c, Matrix4f m, float x, float y, float z, float u, float vv, int color) {
        c.addVertex(m, x, y, z).setUv(u, vv).setColor(color);
    }

    private static void sprite(Matrix4f m, VertexConsumer c, Fx fx, float x, float y, float z, float s, int color,
                               Vector3f r, Vector3f u, Vector3f f) {
        FxSprite sp = fx.sprite;
        float u0 = sp.u0 + sp.du * fx.frame();
        float u1 = u0 + sp.du;
        float v0 = sp.v0;
        float v1 = sp.v0 + sp.dv;
        if (fx.orient == Fx.Orient.STRETCH) {
            stretch(m, c, fx, x, y, z, s, color, u, f, u0, u1, v0, v1);
            return;
        }
        // The quad's two in-plane axes.
        float ax;
        float ay;
        float az;
        float bx;
        float by;
        float bz;
        switch (fx.orient) {
            case FLAT -> {
                ax = 1;
                ay = 0;
                az = 0;
                bx = 0;
                by = 0;
                bz = 1;
            }
            case PLANE -> {
                float nx = fx.nx;
                float ny = fx.ny;
                float nz = fx.nz;
                // a = n × worldUp (or × X when n is nearly vertical), b = a × n.
                if (Math.abs(ny) < 0.95f) {
                    ax = -nz;
                    ay = 0;
                    az = nx;
                } else {
                    ax = 0;
                    ay = nz;
                    az = -ny;
                }
                float al = (float) Math.sqrt(ax * ax + ay * ay + az * az);
                ax /= al;
                ay /= al;
                az /= al;
                bx = ay * nz - az * ny;
                by = az * nx - ax * nz;
                bz = ax * ny - ay * nx;
            }
            default -> {
                ax = r.x;
                ay = r.y;
                az = r.z;
                bx = u.x;
                by = u.y;
                bz = u.z;
            }
        }
        float cos = (float) Math.cos(fx.rot);
        float sin = (float) Math.sin(fx.rot);
        float wobble = fx.tumble > 0 ? 1f - fx.tumble * (1f - Math.abs((float) Math.cos(fx.rot * 1.7f))) : 1f;
        float hw = s * fx.aspect * wobble;
        float hh = s;
        // Rotated axes.
        float rx = ax * cos + bx * sin;
        float ry = ay * cos + by * sin;
        float rz = az * cos + bz * sin;
        float ux = bx * cos - ax * sin;
        float uy = by * cos - ay * sin;
        float uz = bz * cos - az * sin;
        v(c, m, x - rx * hw - ux * hh, y - ry * hw - uy * hh, z - rz * hw - uz * hh, u0, v1, color);
        v(c, m, x + rx * hw - ux * hh, y + ry * hw - uy * hh, z + rz * hw - uz * hh, u1, v1, color);
        v(c, m, x + rx * hw + ux * hh, y + ry * hw + uy * hh, z + rz * hw + uz * hh, u1, v0, color);
        v(c, m, x - rx * hw + ux * hh, y - ry * hw + uy * hh, z - rz * hw + uz * hh, u0, v0, color);
    }

    /** Facing the viewer, stretched from behind the particle to just ahead of it along its motion. */
    private static void stretch(Matrix4f m, VertexConsumer c, Fx fx, float x, float y, float z, float w, int color,
                                Vector3f up, Vector3f f, float u0, float u1, float v0, float v1) {
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
            dx = up.x;
            dy = up.y;
            dz = up.z;
        } else {
            dx = vx / speed;
            dy = vy / speed;
            dz = vz / speed;
        }
        float len = Math.max(w * 2f, speed * fx.stretch);
        float sx = dy * f.z - dz * f.y;
        float sy = dz * f.x - dx * f.z;
        float sz = dx * f.y - dy * f.x;
        float sl = (float) Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-5f) {
            return;
        }
        float hw = w * fx.aspect;
        sx = sx / sl * hw;
        sy = sy / sl * hw;
        sz = sz / sl * hw;
        float hx = x + dx * w;
        float hy = y + dy * w;
        float hz = z + dz * w;
        float tx = x - dx * len;
        float ty = y - dy * len;
        float tz = z - dz * len;
        if (fx.sprite == FxSprite.DROP || fx.sprite == FxSprite.BEAM || fx.sprite == FxSprite.FEATHER) {
            // Upright sprites: their top (v0) trails, their bottom (v1) leads.
            v(c, m, tx - sx, ty - sy, tz - sz, u0, v0, color);
            v(c, m, hx - sx, hy - sy, hz - sz, u0, v1, color);
            v(c, m, hx + sx, hy + sy, hz + sz, u1, v1, color);
            v(c, m, tx + sx, ty + sy, tz + sz, u1, v0, color);
        } else {
            // Sideways sprites (sparks): the left (u0) trails, the right (u1) leads.
            v(c, m, tx - sx, ty - sy, tz - sz, u0, v1, color);
            v(c, m, hx - sx, hy - sy, hz - sz, u1, v1, color);
            v(c, m, hx + sx, hy + sy, hz + sz, u1, v0, color);
            v(c, m, tx + sx, ty + sy, tz + sz, u0, v0, color);
        }
    }

    private static void ribbon(Matrix4f m, VertexConsumer c, FxRibbon r, double ox, double oy, double oz, Vector3f f,
                               double @Nullable [] eye, float alphaScale, float widthScale, boolean hot) {
        int n = r.count;
        if (n < 2) {
            return;
        }
        FxSprite sp = r.sprite;
        float uc = sp.u0 + sp.du * 0.5f;
        float v0 = sp.v0 + sp.dv * 0.04f;
        float v1 = sp.v0 + sp.dv * 0.96f;
        float[] lx = new float[n];
        float[] ly = new float[n];
        float[] lz = new float[n];
        for (int k = 0; k < n; k++) {
            int i = (r.start + k) % r.px.length;
            lx[k] = (float) (r.px[i] - ox);
            ly[k] = (float) (r.py[i] - oy);
            lz[k] = (float) (r.pz[i] - oz);
        }
        float[] sx = new float[n];
        float[] sy = new float[n];
        float[] sz = new float[n];
        int[] cols = new int[n];
        for (int k = 0; k < n; k++) {
            int a = Math.max(0, k - 1);
            int b = Math.min(n - 1, k + 1);
            float tx = lx[b] - lx[a];
            float ty = ly[b] - ly[a];
            float tz = lz[b] - lz[a];
            float wx;
            float wy;
            float wz;
            if (eye != null) {
                wx = lx[k] + (float) (ox - eye[0]);
                wy = ly[k] + (float) (oy - eye[1]);
                wz = lz[k] + (float) (oz - eye[2]);
            } else {
                wx = f.x;
                wy = f.y;
                wz = f.z;
            }
            float cx = ty * wz - tz * wy;
            float cy = tz * wx - tx * wz;
            float cz = tx * wy - ty * wx;
            float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            float age = r.ageOf(k);
            float width = r.width * widthScale * (float) Math.pow(1f - age, 0.6) * (k == n - 1 ? 0.85f : 1f);
            if (cl < 1e-6f) {
                sx[k] = sy[k] = sz[k] = 0;
            } else {
                sx[k] = cx / cl * width * 0.5f;
                sy[k] = cy / cl * width * 0.5f;
                sz[k] = cz / cl * width * 0.5f;
            }
            int i = (r.start + k) % r.px.length;
            int col = hot ? FxColor.lighten(r.col[i], 0.7f) : r.col[i];
            float alpha = (float) Math.pow(1f - age, 1.3) * alphaScale;
            cols[k] = FxColor.alpha(col, ((col >>> 24) / 255f) * alpha);
        }
        for (int k = 0; k + 1 < n; k++) {
            v(c, m, lx[k] - sx[k], ly[k] - sy[k], lz[k] - sz[k], uc, v1, cols[k]);
            v(c, m, lx[k + 1] - sx[k + 1], ly[k + 1] - sy[k + 1], lz[k + 1] - sz[k + 1], uc, v1, cols[k + 1]);
            v(c, m, lx[k + 1] + sx[k + 1], ly[k + 1] + sy[k + 1], lz[k + 1] + sz[k + 1], uc, v0, cols[k + 1]);
            v(c, m, lx[k] + sx[k], ly[k] + sy[k], lz[k] + sz[k], uc, v0, cols[k]);
        }
    }

    private static void bolt(Matrix4f m, VertexConsumer c, FxBolt b, double ox, double oy, double oz, Vector3f f,
                             double @Nullable [] eye) {
        float a = b.alpha();
        if (a <= 0.01f) {
            return;
        }
        int glow = FxColor.alpha(b.color, ((b.color >>> 24) / 255f) * a * 0.6f);
        int core = FxColor.alpha(FxColor.lighten(b.color, 0.85f), a);
        polyline(m, c, b.pts, FxBolt.SEGMENTS + 1, ox, oy, oz, b.width * 3.2f, glow, f, eye);
        polyline(m, c, b.pts, FxBolt.SEGMENTS + 1, ox, oy, oz, b.width, core, f, eye);
        if (b.hasBranch) {
            polyline(m, c, b.branch, 5, ox, oy, oz, b.width * 2f, glow, f, eye);
            polyline(m, c, b.branch, 5, ox, oy, oz, b.width * 0.6f, core, f, eye);
        }
    }

    private static void polyline(Matrix4f m, VertexConsumer c, double[] pts, int n, double ox, double oy, double oz,
                                 float width, int color, Vector3f f, double @Nullable [] eye) {
        FxSprite sp = FxSprite.RIBBON;
        float uc = sp.u0 + sp.du * 0.5f;
        float v0 = sp.v0 + sp.dv * 0.04f;
        float v1 = sp.v0 + sp.dv * 0.96f;
        for (int k = 0; k + 1 < n; k++) {
            float x0 = (float) (pts[k * 3] - ox);
            float y0 = (float) (pts[k * 3 + 1] - oy);
            float z0 = (float) (pts[k * 3 + 2] - oz);
            float x1 = (float) (pts[k * 3 + 3] - ox);
            float y1 = (float) (pts[k * 3 + 4] - oy);
            float z1 = (float) (pts[k * 3 + 5] - oz);
            float tx = x1 - x0;
            float ty = y1 - y0;
            float tz = z1 - z0;
            float wx = eye == null ? f.x : x0 + (float) (ox - eye[0]);
            float wy = eye == null ? f.y : y0 + (float) (oy - eye[1]);
            float wz = eye == null ? f.z : z0 + (float) (oz - eye[2]);
            float cx = ty * wz - tz * wy;
            float cy = tz * wx - tx * wz;
            float cz = tx * wy - ty * wx;
            float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (cl < 1e-6f) {
                continue;
            }
            float h = width * 0.5f / cl;
            cx *= h;
            cy *= h;
            cz *= h;
            v(c, m, x0 - cx, y0 - cy, z0 - cz, uc, v1, color);
            v(c, m, x1 - cx, y1 - cy, z1 - cz, uc, v1, color);
            v(c, m, x1 + cx, y1 + cy, z1 + cz, uc, v0, color);
            v(c, m, x0 + cx, y0 + cy, z0 + cz, uc, v0, color);
        }
    }
}
