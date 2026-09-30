package dev.skirmish.module.menus;

import dev.skirmish.ui.Ui;

import java.util.Random;

/**
 * The animated space behind Skirmish's menus: a dark violet gradient, slowly drifting nebulae, twinkling stars and
 * (optionally) a quasar — a white-hot core in a glow, a tilted accretion disk with bright clumps going round and two
 * jets. All in design px with the kit's shapes; {@code full} false draws it faintly over the game (pause menu).
 */
public final class MenuBackdrop {
    private static final String L = "layout.menus.";
    private static final int STARS = 170;
    private static final float[][] STAR = new float[STARS][];

    static {
        Random random = new Random(0x5C1A);
        for (int i = 0; i < STARS; i++) {
            float size = 0.8f + random.nextFloat() * random.nextFloat() * 2.4f;
            STAR[i] = new float[]{random.nextFloat(), random.nextFloat(), size, random.nextFloat() * 6.28f, 0.4f + random.nextFloat() * 1.6f};
        }
    }

    private MenuBackdrop() {
    }

    public static void draw(Ui ui, float w, float h, long now, boolean full, boolean quasar) {
        float t = now / 1000f;
        if (full) {
            ui.graphics().fillGradient(0, 0, (int) Math.ceil(w), (int) Math.ceil(h), ui.fade(ui.color("bd_top")), ui.fade(ui.color("bd_bottom")));
        }
        float strength = full ? 1f : ui.num(L + "overlay_strength");
        ui.pushAlpha(strength);
        nebula(ui, w * 0.18f + 24f * (float) Math.sin(t / 9f), h * 0.3f + 16f * (float) Math.cos(t / 11f), h * 0.42f, ui.color("bd_nebula_a"));
        nebula(ui, w * 0.82f + 30f * (float) Math.cos(t / 13f), h * 0.74f + 18f * (float) Math.sin(t / 10f), h * 0.5f, ui.color("bd_nebula_b"));
        nebula(ui, w * 0.58f + 20f * (float) Math.sin(t / 15f), h * 0.12f, h * 0.28f, ui.color("bd_nebula_c"));
        for (float[] s : STAR) {
            float x = (s[0] * w + t * 1.5f * s[2]) % w;
            float y = s[1] * h;
            float tw = 0.5f + 0.5f * (float) Math.sin(t * s[4] + s[3]);
            int a = Math.round(60 + 170 * tw * Math.min(1f, s[2] / 2f));
            ui.circle(x, y, s[2] * 1.4f, (a << 24) | 0xFFFFFF);
        }
        if (quasar) {
            quasar(ui, w * ui.num(L + "quasar_x"), h * ui.num(L + "quasar_y"), Math.min(w, h) * ui.num(L + "quasar_size"), t);
        }
        ui.popAlpha();
    }

    /** A soft round glow: stacked translucent circles, bigger ones fainter. */
    static void nebula(Ui ui, float cx, float cy, float radius, int color) {
        int layers = 22;
        int base = (color >>> 24) & 0xFF;
        for (int i = 0; i < layers; i++) {
            float d = radius * 2f * (1f - i / (float) layers);
            int a = Math.max(1, base / layers);
            ui.circle(cx, cy, d, (a << 24) | (color & 0xFFFFFF));
        }
    }

    /** Core, glow, tilted accretion disk with orbiting clumps and two pulsing jets. */
    static void quasar(Ui ui, float cx, float cy, float size, float t) {
        int accent = ui.color("bd_quasar");
        int hot = ui.color("bd_quasar_hot");
        double tilt = Math.toRadians(-18);
        float jetLen = size * 1.9f;
        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 2.3f);
        // Jets: along the disk's axis, fading outwards.
        double axis = tilt + Math.PI / 2;
        for (int side = -1; side <= 1; side += 2) {
            int n = 14;
            for (int i = 0; i < n; i++) {
                float f0 = i / (float) n;
                float f1 = (i + 1) / (float) n;
                float x0 = cx + side * (float) Math.cos(axis) * jetLen * f0;
                float y0 = cy + side * (float) Math.sin(axis) * jetLen * f0;
                float x1 = cx + side * (float) Math.cos(axis) * jetLen * f1;
                float y1 = cy + side * (float) Math.sin(axis) * jetLen * f1;
                int a = Math.round(200 * pulse * (1f - f0) * (1f - f0));
                ui.line(x0, y0, x1, y1, Math.max(1f, size * 0.07f * (1f - f0 * 0.8f)), (a << 24) | (hot & 0xFFFFFF));
            }
        }
        nebula(ui, cx, cy, size * 0.95f, (0x70 << 24) | (accent & 0xFFFFFF));
        // Disk: three rings of the tilted ellipse.
        float[] radii = {1.25f, 1.0f, 0.78f};
        int[] alphas = {70, 150, 110};
        for (int r = 0; r < radii.length; r++) {
            ui.polyline(Math.max(1f, size * 0.035f), (alphas[r] << 24) | (accent & 0xFFFFFF), ellipse(cx, cy, size * radii[r], size * radii[r] * 0.24f, tilt, 0, 360, 72));
        }
        // Bright clumps going round.
        for (int k = 0; k < 3; k++) {
            float start = (t * 55f + k * 120f) % 360f;
            ui.polyline(Math.max(1.5f, size * 0.06f), (220 << 24) | (hot & 0xFFFFFF),
                    ellipse(cx, cy, size * 1.0f, size * 0.24f, tilt, start, start + 38f, 10));
        }
        nebula(ui, cx, cy, size * 0.32f, (0xC0 << 24) | (hot & 0xFFFFFF));
        ui.circle(cx, cy, size * 0.2f * (0.92f + 0.08f * pulse), 0xFFFFFFFF);
    }

    private static float[] ellipse(float cx, float cy, float a, float b, double tilt, float fromDeg, float toDeg, int steps) {
        float[] pts = new float[(steps + 1) * 2];
        double c = Math.cos(tilt);
        double s = Math.sin(tilt);
        for (int i = 0; i <= steps; i++) {
            double th = Math.toRadians(fromDeg + (toDeg - fromDeg) * i / steps);
            double px = a * Math.cos(th);
            double py = b * Math.sin(th);
            pts[i * 2] = (float) (cx + px * c - py * s);
            pts[i * 2 + 1] = (float) (cy + px * s + py * c);
        }
        return pts;
    }
}
