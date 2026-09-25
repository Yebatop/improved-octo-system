package dev.skirmish.module.base;

/**
 * A tiny software renderer for the base model: a voxel grid (one ARGB colour per block, alpha 0 = air) drawn as
 * shaded cubes into an ARGB pixel array, rotated by yaw and pitch, with a depth buffer. Only faces next to air are
 * drawn, and only those facing the viewer. Pure Java, so it runs off the render thread.
 */
final class VoxelRaster {
    /** Blocks along x, y, z and their colours, index {@code (y * sz + z) * sx + x}. */
    record Grid(int sx, int sy, int sz, int[] colors) {
        int at(int x, int y, int z) {
            if (x < 0 || y < 0 || z < 0 || x >= sx || y >= sy || z >= sz) {
                return 0;
            }
            return colors[(y * sz + z) * sx + x];
        }
    }

    /** Face normals (world space) and the corner offsets of each face, counter-clockwise seen from outside. */
    private static final int[][] NORMALS = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
    private static final float[][][] CORNERS = {
            {{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}},
            {{0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {0, 0, 0}},
            {{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}},
            {{0, 0, 1}, {0, 0, 0}, {1, 0, 0}, {1, 0, 1}},
            {{1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},
            {{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}},
    };
    /** Brightness of each face: sunlit top, darker sides, darkest bottom (like the game's own shading). */
    private static final float[] SHADE = {0.80f, 0.80f, 1.0f, 0.5f, 0.64f, 0.64f};

    private VoxelRaster() {
    }

    /**
     * Renders {@code grid} into a {@code w}×{@code h} ARGB image (transparent background). Layers above
     * {@code cutY} (grid y) are left out so the inside shows; {@code zoom} 1 fits the whole box.
     */
    static int[] render(Grid grid, float yaw, float pitch, float zoom, int cutY, int w, int h) {
        int[] out = new int[w * h];
        float[] depth = new float[w * h];
        java.util.Arrays.fill(depth, Float.POSITIVE_INFINITY);
        float cy = (float) Math.cos(yaw);
        float sy = (float) Math.sin(yaw);
        float cp = (float) Math.cos(pitch);
        float sp = (float) Math.sin(pitch);
        int top = Math.min(grid.sy() - 1, cutY);
        // Frame what is there (not the empty air of the box): centre and size from the filled blocks.
        int[] lo = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
        int[] hi = {Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int y = 0; y <= top; y++) {
            for (int z = 0; z < grid.sz(); z++) {
                for (int x = 0; x < grid.sx(); x++) {
                    if ((grid.at(x, y, z) >>> 24) != 0) {
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
        if (lo[0] == Integer.MAX_VALUE) {
            return out;
        }
        float ex = hi[0] - lo[0] + 1;
        float ey = hi[1] - lo[1] + 1;
        float ez = hi[2] - lo[2] + 1;
        float diag = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
        float scale = Math.min(w, h) / Math.max(1f, diag) * zoom;
        float ox = (lo[0] + hi[0] + 1) / 2f;
        float oy = (lo[1] + hi[1] + 1) / 2f;
        float oz = (lo[2] + hi[2] + 1) / 2f;
        float[] px = new float[4];
        float[] py = new float[4];
        float[] pz = new float[4];
        for (int y = 0; y <= top; y++) {
            for (int z = 0; z < grid.sz(); z++) {
                for (int x = 0; x < grid.sx(); x++) {
                    int color = grid.at(x, y, z);
                    if ((color >>> 24) == 0) {
                        continue;
                    }
                    for (int f = 0; f < 6; f++) {
                        int[] n = NORMALS[f];
                        int ny = y + n[1];
                        boolean open = ny > top || (grid.at(x + n[0], ny, z + n[2]) >>> 24) == 0;
                        if (!open) {
                            continue;
                        }
                        // Rotated normal's depth part: faces pointing away are skipped.
                        float rz = n[0] * sy + n[2] * cy;
                        float dz = -n[1] * sp + rz * cp;
                        if (dz >= 0f) {
                            continue;
                        }
                        for (int c = 0; c < 4; c++) {
                            float[] k = CORNERS[f][c];
                            float qx = x + k[0] - ox;
                            float qy = y + k[1] - oy;
                            float qz = z + k[2] - oz;
                            float x1 = qx * cy - qz * sy;
                            float z1 = qx * sy + qz * cy;
                            // Pitch > 0 looks down from above: higher points come nearer, farther ones show higher.
                            float y2 = qy * cp + z1 * sp;
                            float z2 = -qy * sp + z1 * cp;
                            px[c] = w / 2f + x1 * scale;
                            py[c] = h / 2f - y2 * scale;
                            pz[c] = z2;
                        }
                        int shaded = shade(color, SHADE[f]);
                        triangle(out, depth, w, h, px[0], py[0], pz[0], px[1], py[1], pz[1], px[2], py[2], pz[2], shaded);
                        triangle(out, depth, w, h, px[0], py[0], pz[0], px[2], py[2], pz[2], px[3], py[3], pz[3], shaded);
                    }
                }
            }
        }
        return out;
    }

    private static int shade(int argb, float k) {
        int r = Math.round(((argb >> 16) & 0xFF) * k);
        int g = Math.round(((argb >> 8) & 0xFF) * k);
        int b = Math.round((argb & 0xFF) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** Fills a triangle with a depth test; pixel centres at +0.5, both windings accepted. */
    private static void triangle(int[] out, float[] depth, int w, int h, float x0, float y0, float z0, float x1, float y1, float z1,
                                 float x2, float y2, float z2, int color) {
        float area = (x1 - x0) * (y2 - y0) - (x2 - x0) * (y1 - y0);
        if (Math.abs(area) < 1e-6f) {
            return;
        }
        int minX = Math.max(0, (int) Math.floor(Math.min(x0, Math.min(x1, x2))));
        int maxX = Math.min(w - 1, (int) Math.ceil(Math.max(x0, Math.max(x1, x2))));
        int minY = Math.max(0, (int) Math.floor(Math.min(y0, Math.min(y1, y2))));
        int maxY = Math.min(h - 1, (int) Math.ceil(Math.max(y0, Math.max(y1, y2))));
        float inv = 1f / area;
        for (int yy = minY; yy <= maxY; yy++) {
            float cyp = yy + 0.5f;
            for (int xx = minX; xx <= maxX; xx++) {
                float cxp = xx + 0.5f;
                float w0 = ((x1 - cxp) * (y2 - cyp) - (x2 - cxp) * (y1 - cyp)) * inv;
                float w1 = ((x2 - cxp) * (y0 - cyp) - (x0 - cxp) * (y2 - cyp)) * inv;
                float w2 = 1f - w0 - w1;
                // A small tolerance closes hairline cracks between neighbouring faces.
                if (w0 < -1e-3f || w1 < -1e-3f || w2 < -1e-3f) {
                    continue;
                }
                float z = w0 * z0 + w1 * z1 + w2 * z2;
                int i = yy * w + xx;
                if (z < depth[i]) {
                    depth[i] = z;
                    out[i] = color;
                }
            }
        }
    }
}
