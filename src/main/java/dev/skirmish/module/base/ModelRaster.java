package dev.skirmish.module.base;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A small software renderer for the base model: every block of the region as its real model (textured quads with
 * the biome tint, the game's face shading, soft corners where blocks meet and the light level in front of each
 * face), turned by yaw and pitch and drawn with a depth buffer. Glass, water and ice are blended in back to front.
 * The picture is drawn at a multiple of its size and scaled down for smooth edges, then put on a backdrop with the
 * region's floor: a soft shadow, a faint block grid and the outline of the protected area. Pure Java, so it runs
 * off the render thread.
 */
final class ModelRaster {
    /** Face indices in the game's direction order. */
    static final int DOWN = 0;
    static final int UP = 1;
    static final int NORTH = 2;
    static final int SOUTH = 3;
    static final int WEST = 4;
    static final int EAST = 5;
    static final int[][] NORMAL = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    /** Axis of each face's normal: 0 x, 1 y, 2 z. */
    private static final int[] AXIS = {1, 1, 2, 2, 0, 0};
    /** The game's own face shading: bright tops, darker sides, darkest bottoms. */
    private static final float[] SHADE = {0.5f, 1f, 0.8f, 0.8f, 0.6f, 0.6f};
    /** Corner darkening by how many of the blocks around a corner are solid (both sides count as three). */
    private static final float[] AO = {1f, 0.8f, 0.66f, 0.52f};

    /** A texture: size and ARGB pixels; translucent when some pixel is neither clear nor solid (glass, water). */
    record Tex(int w, int h, int[] argb, boolean translucent) {
        static Tex of(int w, int h, int[] argb) {
            boolean translucent = false;
            for (int p : argb) {
                int a = p >>> 24;
                if (a > 8 && a < 247) {
                    translucent = true;
                    break;
                }
            }
            return new Tex(w, h, argb, translucent);
        }
    }

    /**
     * One quad of a block model in block space (0..1): four corners (x, y, z), their texture coordinates (0..1 of the
     * texture), the tint (RGB), the face it shades as, the face whose neighbour hides it (−1: never hidden), whether
     * the game shades it by direction and whether it gets soft corners.
     */
    record Quad(float[] xyz, float[] uv, int tex, int tint, int face, int cull, boolean shade, boolean ao) {
    }

    /** A block kind: its quads, whether it is a full opaque cube (hides neighbours, darkens corners) and whether it glows. */
    record Kind(Quad[] quads, boolean opaque, boolean glow) {
    }

    /**
     * The region: blocks along x, y, z; per block the kind (index + 1, 0 = air), the faces its neighbours cover (bit
     * per face) and the light level (0..15, the brighter of sky and block light). Index {@code (y * sz + z) * sx + x}.
     */
    record Scene(int sx, int sy, int sz, int[] cells, byte[] hidden, byte[] light, List<Kind> kinds, List<Tex> textures) {
        int index(int x, int y, int z) {
            return (y * sz + z) * sx + x;
        }

        boolean inside(int x, int y, int z) {
            return x >= 0 && y >= 0 && z >= 0 && x < sx && y < sy && z < sz;
        }

        int cell(int x, int y, int z) {
            return inside(x, y, z) ? cells[index(x, y, z)] : 0;
        }
    }

    /** Backdrop colours (RGB): the floor outline and grid, and the backdrop's centre and edge. */
    record Look(int accent, int center, int edge) {
    }

    /** Where the model sits in the picture, to put markers over it. */
    record View(float yaw, float pitch, float scale, float ox, float oy, float oz, float cx, float cy) {
        /** Picture x, y (pixels) and depth (smaller is nearer) of a point in grid space (blocks). */
        float[] project(float x, float y, float z) {
            float cyw = (float) Math.cos(yaw);
            float syw = (float) Math.sin(yaw);
            float cp = (float) Math.cos(pitch);
            float sp = (float) Math.sin(pitch);
            float qx = x - ox;
            float qy = y - oy;
            float qz = z - oz;
            float x1 = qx * cyw - qz * syw;
            float z1 = qx * syw + qz * cyw;
            float y2 = qy * cp + z1 * sp;
            float z2 = -qy * sp + z1 * cp;
            // Looking along +z2 with y2 up, the picture's right is −x1.
            return new float[]{cx - x1 * scale, cy - y2 * scale, z2};
        }
    }

    /** The finished picture (opaque ARGB), the depth of the nearest solid surface per pixel and the view. */
    record Frame(int w, int h, int[] argb, float[] depth, View view) {
    }

    private ModelRaster() {
    }

    /**
     * Draws {@code s} into a {@code w}×{@code h} picture. Layers above {@code cutY} are left out so the inside shows;
     * {@code zoom} 1 fits what is there, {@code panX}/{@code panY} shift it by a fraction of the picture;
     * {@code ss} is the supersampling factor (1 fast, 2 smooth).
     */
    static Frame render(Scene s, float yaw, float pitch, float zoom, float panX, float panY, int cutY, int w, int h, int ss, Look look) {
        int top = Math.min(s.sy() - 1, cutY);
        // Frame what is there (not the empty air of the box).
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int y = 0; y <= top; y++) {
            for (int z = 0; z < s.sz(); z++) {
                for (int x = 0; x < s.sx(); x++) {
                    if (s.cells()[s.index(x, y, z)] != 0) {
                        lo[0] = Math.min(lo[0], x);
                        lo[1] = Math.min(lo[1], y);
                        lo[2] = Math.min(lo[2], z);
                        hi[0] = Math.max(hi[0], x);
                        hi[1] = Math.max(hi[1], y);
                        hi[2] = Math.max(hi[2], z);
                    }
                }
            }
        }
        boolean empty = lo[0] == Integer.MAX_VALUE;
        if (empty) {
            lo = new int[]{0, 0, 0};
            hi = new int[]{s.sx() - 1, 0, s.sz() - 1};
        }
        float ex = hi[0] - lo[0] + 1;
        float ey = hi[1] - lo[1] + 1;
        float ez = hi[2] - lo[2] + 1;
        // Fit the widest the model gets at any turn (so turning does not zoom), at this tilt.
        float across = (float) Math.sqrt(ex * ex + ez * ez);
        float tall = ey * (float) Math.cos(pitch) + across * Math.abs((float) Math.sin(pitch));
        float scale = Math.min(w / Math.max(1f, across), h / Math.max(1f, tall)) * 0.9f * zoom;
        View view = new View(yaw, pitch, scale, (lo[0] + hi[0] + 1) / 2f, (lo[1] + hi[1] + 1) / 2f, (lo[2] + hi[2] + 1) / 2f,
                w / 2f + panX * w, h / 2f + panY * h);
        int big = Math.max(1, ss);
        Raster r = new Raster(s, view, big, w * big, h * big, top);
        if (!empty) {
            r.draw();
        }
        int[] argb = new int[w * h];
        float[] depth = new float[w * h];
        r.downsample(w, h, argb, depth);
        backdrop(s, view, lo[1], w, h, argb, look);
        return new Frame(w, h, argb, depth, view);
    }

    /** The rasteriser for one picture: a colour and a depth buffer at the supersampled size. */
    private static final class Raster {
        private final Scene s;
        private final int w;
        private final int h;
        private final int top;
        private final int[] color;
        private final float[] depth;
        private final float cyw;
        private final float syw;
        private final float cp;
        private final float sp;
        private final float scale;
        private final float cx;
        private final float cy;
        private final View view;
        private final float[] vx = new float[4];
        private final float[] vy = new float[4];
        private final float[] vz = new float[4];
        private final float[] vu = new float[4];
        private final float[] vv = new float[4];
        private final float[] vb = new float[4];

        /** A see-through quad kept for the second pass. */
        private record Later(Quad q, Kind k, int x, int y, int z, float depth) {
        }

        Raster(Scene s, View view, int ss, int w, int h, int top) {
            this.s = s;
            this.w = w;
            this.h = h;
            this.top = top;
            this.view = view;
            color = new int[w * h];
            depth = new float[w * h];
            Arrays.fill(depth, Float.POSITIVE_INFINITY);
            cyw = (float) Math.cos(view.yaw());
            syw = (float) Math.sin(view.yaw());
            cp = (float) Math.cos(view.pitch());
            sp = (float) Math.sin(view.pitch());
            scale = view.scale() * ss;
            cx = view.cx() * ss;
            cy = view.cy() * ss;
        }

        void draw() {
            List<Later> later = new ArrayList<>();
            for (int y = 0; y <= top; y++) {
                for (int z = 0; z < s.sz(); z++) {
                    for (int x = 0; x < s.sx(); x++) {
                        int i = s.index(x, y, z);
                        int c = s.cells()[i];
                        if (c == 0) {
                            continue;
                        }
                        Kind k = s.kinds().get(c - 1);
                        int hidden = s.hidden()[i];
                        for (Quad q : k.quads()) {
                            if (q.cull() >= 0 && (hidden >> q.cull() & 1) != 0 && !(q.cull() == UP && y == top)) {
                                continue;
                            }
                            if (s.textures().get(q.tex()).translucent()) {
                                later.add(new Later(q, k, x, y, z, centreDepth(q, x, y, z)));
                            } else {
                                quad(q, k, x, y, z, false);
                            }
                        }
                    }
                }
            }
            // Water, glass and ice last, farthest first, blended over what is behind.
            later.sort((a, b) -> Float.compare(b.depth(), a.depth()));
            for (Later l : later) {
                quad(l.q(), l.k(), l.x(), l.y(), l.z(), true);
            }
        }

        private float centreDepth(Quad q, int bx, int by, int bz) {
            float[] p = q.xyz();
            float x = bx + (p[0] + p[6]) / 2f - view.ox();
            float y = by + (p[1] + p[7]) / 2f - view.oy();
            float z = bz + (p[2] + p[8]) / 2f - view.oz();
            return -y * sp + (x * syw + z * cyw) * cp;
        }

        private boolean solid(int x, int y, int z) {
            if (y > top || !s.inside(x, y, z)) {
                return false;
            }
            int c = s.cells()[s.index(x, y, z)];
            return c != 0 && s.kinds().get(c - 1).opaque();
        }

        /** Brightness from the light level of the block a face looks into; open air and cut-away space are lit. */
        private float light(int x, int y, int z) {
            if (y > top || !s.inside(x, y, z) || solid(x, y, z)) {
                return 1f;
            }
            float l = (s.light()[s.index(x, y, z)] & 15) / 15f;
            return 0.34f + 0.66f * (float) Math.pow(l, 1.4);
        }

        private void quad(Quad q, Kind k, int bx, int by, int bz, boolean blend) {
            float[] p = q.xyz();
            // Facing away: skipped (the quad's own winding gives its outward normal).
            float ax = p[3] - p[0];
            float ay = p[4] - p[1];
            float az = p[5] - p[2];
            float qx = p[6] - p[0];
            float qy = p[7] - p[1];
            float qz = p[8] - p[2];
            float nx = ay * qz - az * qy;
            float ny = az * qx - ax * qz;
            float nz = ax * qy - ay * qx;
            float dz = -ny * sp + (nx * syw + nz * cyw) * cp;
            if (dz >= -1e-7f) {
                return;
            }
            int f = q.face();
            int[] n = NORMAL[f];
            int axis = AXIS[f];
            boolean boundary = n[axis] > 0 ? p[axis] > 0.999f : p[axis] < 0.001f;
            int lx = bx + (boundary ? n[0] : 0);
            int ly = by + (boundary ? n[1] : 0);
            int lz = bz + (boundary ? n[2] : 0);
            float base = (q.shade() ? SHADE[f] : 1f) * (k.glow() ? 1f : light(lx, ly, lz));
            for (int i = 0; i < 4; i++) {
                float gx = bx + p[i * 3] - view.ox();
                float gy = by + p[i * 3 + 1] - view.oy();
                float gz = bz + p[i * 3 + 2] - view.oz();
                float x1 = gx * cyw - gz * syw;
                float z1 = gx * syw + gz * cyw;
                vx[i] = cx - x1 * scale;
                vy[i] = cy - (gy * cp + z1 * sp) * scale;
                vz[i] = -gy * sp + z1 * cp;
                vu[i] = q.uv()[i * 2];
                vv[i] = q.uv()[i * 2 + 1];
                vb[i] = base * (q.ao() && !k.glow() ? AO[corner(lx, ly, lz, axis, p, i)] : 1f);
            }
            Tex t = s.textures().get(q.tex());
            tri(0, 1, 2, t, q.tint(), blend);
            tri(0, 2, 3, t, q.tint(), blend);
        }

        /** How dark corner {@code i} is: the solid blocks beside it in the layer the face looks into. */
        private int corner(int lx, int ly, int lz, int axis, float[] p, int i) {
            int a = (axis + 1) % 3;
            int b = (axis + 2) % 3;
            int da = p[i * 3 + a] >= 0.5f ? 1 : -1;
            int db = p[i * 3 + b] >= 0.5f ? 1 : -1;
            int[] o = {lx, ly, lz};
            int[] s1 = o.clone();
            s1[a] += da;
            int[] s2 = o.clone();
            s2[b] += db;
            int[] c = o.clone();
            c[a] += da;
            c[b] += db;
            boolean side1 = solid(s1[0], s1[1], s1[2]);
            boolean side2 = solid(s2[0], s2[1], s2[2]);
            if (side1 && side2) {
                return 3;
            }
            return (side1 ? 1 : 0) + (side2 ? 1 : 0) + (solid(c[0], c[1], c[2]) ? 1 : 0);
        }

        /** Fills a triangle: depth test, nearest texel, alpha test (or blending), tint and brightness. */
        private void tri(int i0, int i1, int i2, Tex t, int tint, boolean blend) {
            float x0 = vx[i0];
            float y0 = vy[i0];
            float x1 = vx[i1];
            float y1 = vy[i1];
            float x2 = vx[i2];
            float y2 = vy[i2];
            float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
            if (Math.abs(area) < 1e-6f) {
                return;
            }
            int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
            int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
            int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
            int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
            if (minX > maxX || minY > maxY) {
                return;
            }
            float inv = 1f / area;
            int tw = t.w();
            int th = t.h();
            int[] tp = t.argb();
            float tr = ((tint >> 16) & 0xFF) / 255f;
            float tg = ((tint >> 8) & 0xFF) / 255f;
            float tb = (tint & 0xFF) / 255f;
            for (int yy = minY; yy <= maxY; yy++) {
                float py = yy + 0.5f;
                for (int xx = minX; xx <= maxX; xx++) {
                    float px = xx + 0.5f;
                    float w0 = ((x1 - px) * (y2 - py) - (x2 - px) * (y1 - py)) * inv;
                    float w1 = ((x2 - px) * (y0 - py) - (x0 - px) * (y2 - py)) * inv;
                    float w2 = 1f - w0 - w1;
                    // A small tolerance closes hairline cracks between neighbouring faces.
                    if (w0 < -1e-3f || w1 < -1e-3f || w2 < -1e-3f) {
                        continue;
                    }
                    float z = w0 * vz[i0] + w1 * vz[i1] + w2 * vz[i2];
                    int i = yy * w + xx;
                    if (z >= depth[i]) {
                        continue;
                    }
                    float u = w0 * vu[i0] + w1 * vu[i1] + w2 * vu[i2];
                    float v = w0 * vv[i0] + w1 * vv[i1] + w2 * vv[i2];
                    int tx = Math.min(tw - 1, Math.max(0, (int) (u * tw)));
                    int ty = Math.min(th - 1, Math.max(0, (int) (v * th)));
                    int texel = tp[ty * tw + tx];
                    int a = texel >>> 24;
                    if (blend ? a == 0 : a < 128) {
                        continue;
                    }
                    float b = w0 * vb[i0] + w1 * vb[i1] + w2 * vb[i2];
                    int r = Math.min(255, (int) (((texel >> 16) & 0xFF) * tr * b));
                    int g = Math.min(255, (int) (((texel >> 8) & 0xFF) * tg * b));
                    int bl = Math.min(255, (int) ((texel & 0xFF) * tb * b));
                    if (!blend) {
                        color[i] = 0xFF000000 | (r << 16) | (g << 8) | bl;
                        depth[i] = z;
                    } else {
                        color[i] = over(r, g, bl, a, color[i]);
                    }
                }
            }
        }

        /** {@code w}×{@code h} from the supersampled buffers: colours averaged by alpha, nearest depth. */
        void downsample(int ow, int oh, int[] argb, float[] outDepth) {
            int k = w / ow;
            int n = k * k;
            for (int y = 0; y < oh; y++) {
                for (int x = 0; x < ow; x++) {
                    long sa = 0;
                    long sr = 0;
                    long sg = 0;
                    long sb = 0;
                    float d = Float.POSITIVE_INFINITY;
                    for (int j = 0; j < k; j++) {
                        int row = (y * k + j) * w + x * k;
                        for (int i = 0; i < k; i++) {
                            int c = color[row + i];
                            int a = c >>> 24;
                            sa += a;
                            sr += (long) ((c >> 16) & 0xFF) * a;
                            sg += (long) ((c >> 8) & 0xFF) * a;
                            sb += (long) (c & 0xFF) * a;
                            d = Math.min(d, depth[row + i]);
                        }
                    }
                    int o = y * ow + x;
                    outDepth[o] = d;
                    if (sa == 0) {
                        argb[o] = 0;
                        continue;
                    }
                    int a = (int) (sa / n);
                    argb[o] = (a << 24) | (int) (sr / sa) << 16 | (int) (sg / sa) << 8 | (int) (sb / sa);
                }
            }
        }
    }

    /** Non-premultiplied "source over destination". */
    static int over(int r, int g, int b, int a, int dst) {
        int da = dst >>> 24;
        float sa = a / 255f;
        float dA = da / 255f * (1f - sa);
        float oa = sa + dA;
        if (oa <= 0f) {
            return 0;
        }
        int rr = Math.round((r * sa + ((dst >> 16) & 0xFF) * dA) / oa);
        int gg = Math.round((g * sa + ((dst >> 8) & 0xFF) * dA) / oa);
        int bb = Math.round((b * sa + (dst & 0xFF) * dA) / oa);
        return Math.round(oa * 255f) << 24 | Math.min(255, rr) << 16 | Math.min(255, gg) << 8 | Math.min(255, bb);
    }

    /**
     * Puts the model on its backdrop: a soft radial gradient, and below the model the region's floor seen from
     * above: a contact shadow, a faint block grid fading out around the region and the region's outline.
     */
    private static void backdrop(Scene s, View v, int floorY, int w, int h, int[] argb, Look look) {
        float cyw = (float) Math.cos(v.yaw());
        float syw = (float) Math.sin(v.yaw());
        float cp = (float) Math.cos(v.pitch());
        float sp = (float) Math.sin(v.pitch());
        boolean floor = sp > 0.08f;
        float qy = floorY - v.oy();
        float px1 = 1f / v.scale();
        float reach = Math.max(w, h) * 0.75f;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int o = y * w + x;
                int fg = argb[o];
                if ((fg >>> 24) == 255) {
                    continue;
                }
                float dx = x - w / 2f;
                float dy = y - h * 0.42f;
                float t = Math.min(1f, (float) Math.sqrt(dx * dx + dy * dy) / reach);
                int bg = lerp(look.center(), look.edge(), t * t * (3f - 2f * t));
                if (floor) {
                    // The floor point under this pixel (orthographic: one ray per pixel).
                    float x1 = (v.cx() - x - 0.5f) * px1;
                    float y2 = (v.cy() - y - 0.5f) * px1;
                    float z1 = (y2 - qy * cp) / sp;
                    float gx = x1 * cyw + z1 * syw + v.ox();
                    float gz = -x1 * syw + z1 * cyw + v.oz();
                    float ox = Math.max(-gx, gx - s.sx());
                    float oz = Math.max(-gz, gz - s.sz());
                    float out = (float) Math.sqrt(Math.max(ox, 0f) * Math.max(ox, 0f) + Math.max(oz, 0f) * Math.max(oz, 0f));
                    float sd = out + Math.min(Math.max(ox, oz), 0f);
                    // Grid lines every block, fading out a few blocks past the region.
                    float fade = 1f - smooth(0f, 6f, sd);
                    if (fade > 0f) {
                        float lx = Math.abs(gx - Math.round(gx)) * v.scale() * sp;
                        float lz = Math.abs(gz - Math.round(gz)) * v.scale() * sp;
                        float line = Math.max(0f, 1f - Math.min(lx, lz));
                        bg = lerp(bg, look.accent(), line * 0.10f * fade);
                    }
                    if (sd < 0f) {
                        bg = lerp(bg, look.accent(), 0.06f);
                    }
                    // Contact shadow hugging the region.
                    float shadow = 0.5f * (1f - smooth(-0.5f, 2.5f, sd));
                    bg = lerp(bg, 0, shadow);
                    // The region's edge.
                    float edge = Math.max(0f, 1.3f - Math.abs(sd) * v.scale() * Math.max(sp, 0.35f));
                    bg = lerp(bg, look.accent(), Math.min(1f, edge) * 0.85f);
                }
                int a = fg >>> 24;
                if (a == 0) {
                    argb[o] = 0xFF000000 | bg;
                } else {
                    argb[o] = 0xFF000000 | lerp(bg, fg & 0xFFFFFF, a / 255f);
                }
            }
        }
    }

    /** RGB lerp (alpha dropped). */
    static int lerp(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    private static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }

    // ---- building blocks for kinds without a model (fluids, chests, entity-drawn blocks) ----

    /**
     * A box from (x0, y0, z0) to (x1, y1, z1) in block space, one texture per face (DOWN..EAST order), each face
     * showing its whole texture. Faces on the block's boundary are hidden by covering neighbours.
     */
    static Quad[] box(float x0, float y0, float z0, float x1, float y1, float z1, int[] tex, int tint, boolean ao) {
        float[][] corners = {
                {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1},
                {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0},
                {x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0},
                {x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1},
                {x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1},
                {x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0},
        };
        boolean[] onEdge = {y0 <= 0.001f, y1 >= 0.999f, z0 <= 0.001f, z1 >= 0.999f, x0 <= 0.001f, x1 >= 0.999f};
        Quad[] out = new Quad[6];
        for (int f = 0; f < 6; f++) {
            out[f] = new Quad(corners[f], new float[]{0, 0, 0, 1, 1, 1, 1, 0}, tex[f], tint, f, onEdge[f] ? f : -1, true, ao);
        }
        return out;
    }
}
