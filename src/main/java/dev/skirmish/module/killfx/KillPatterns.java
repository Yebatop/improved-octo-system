package dev.skirmish.module.killfx;

import dev.skirmish.fx.Fx;
import dev.skirmish.fx.FxField;
import dev.skirmish.fx.FxSprite;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.component.FireworkExplosion;

/**
 * The shapes of the particle kill bursts, shared by the game (vanilla particles in the level) and the Studio (effect
 * sprites made to look like them): a fountain of totem sparkles, a ring of fire around a pillar of flame, a double
 * helix of soul fire, a heart drawn in hearts, a star of light rays, a firework. Each is a shape with its own motion,
 * not a cloud in another colour. Positions are in blocks, velocities in blocks per tick like vanilla particles.
 */
public final class KillPatterns {
    /** Which particle of the burst: its main one or its accent. */
    public enum Role {
        MAIN, ACCENT
    }

    public interface Sink {
        void particle(Role role, double x, double y, double z, double vx, double vy, double vz);

        /** A firework bursting at {@code x, y, z} in {@code colors} (RGB). */
        void firework(FireworkExplosion.Shape shape, int[] colors, double x, double y, double z);
    }

    private static final FireworkExplosion.Shape[] FIREWORK_SHAPES = {
            FireworkExplosion.Shape.STAR, FireworkExplosion.Shape.CREEPER, FireworkExplosion.Shape.LARGE_BALL,
            FireworkExplosion.Shape.BURST
    };

    private KillPatterns() {
    }

    /**
     * Emits a burst over a body that stood at {@code x, y, z}; {@code viewYaw} is the camera's yaw (degrees), so flat
     * shapes face the viewer; {@code scale} sets the amount (1 = normal).
     */
    public static void emit(KillFxModule.Burst type, RandomSource r, double x, double y, double z, float viewYaw, float scale,
                            int[] colors, Sink out) {
        int n = Math.max(12, Math.round(44 * scale));
        switch (type) {
            case TOTEM -> {
                // A fountain: two spiralling jets shooting up, then a ring bursting out at the top.
                for (int i = 0; i < n; i++) {
                    double t = i / (double) n;
                    double a = t * Math.PI * 5 + (i % 2) * Math.PI;
                    double rad = 0.3 + t * 0.25;
                    double c = Math.cos(a);
                    double s = Math.sin(a);
                    out.particle(Role.MAIN, x + c * rad, y + 0.1 + t * 1.5, z + s * rad,
                            c * 0.06 - s * 0.14, 0.4 + t * 0.3, s * 0.06 + c * 0.14);
                }
                int ring = Math.max(8, n / 2);
                for (int i = 0; i < ring; i++) {
                    double a = i * Math.PI * 2 / ring;
                    out.particle(Role.ACCENT, x + Math.cos(a) * 0.3, y + 2.2, z + Math.sin(a) * 0.3,
                            Math.cos(a) * 0.55, 0.12, Math.sin(a) * 0.55);
                }
            }
            case FLAME -> {
                // A ring of fire rolling out over the ground around a pillar of flame with a crown on top.
                int ring = Math.max(16, n);
                for (int i = 0; i < ring; i++) {
                    double a = i * Math.PI * 2 / ring;
                    out.particle(Role.MAIN, x + Math.cos(a) * 0.45, y + 0.05, z + Math.sin(a) * 0.45,
                            Math.cos(a) * 0.11, 0.004, Math.sin(a) * 0.11);
                }
                int pillar = Math.max(10, n / 2);
                for (int i = 0; i < pillar; i++) {
                    double t = i / (double) pillar;
                    out.particle(Role.MAIN, x + (r.nextDouble() - 0.5) * 0.25, y + t * 2.2, z + (r.nextDouble() - 0.5) * 0.25,
                            0, 0.02 + r.nextDouble() * 0.02, 0);
                }
                for (int i = 0; i < 10; i++) {
                    double a = i * Math.PI * 2 / 10;
                    out.particle(Role.ACCENT, x, y + 2.3, z, Math.cos(a) * 0.06, 0.03, Math.sin(a) * 0.06);
                }
            }
            case SOUL -> {
                // A double helix of soul fire climbing around the body, souls rising out of the middle.
                for (int i = 0; i < n; i++) {
                    double t = (i / 2) / (double) (n / 2);
                    double a = t * Math.PI * 4 + (i % 2) * Math.PI;
                    double c = Math.cos(a);
                    double s = Math.sin(a);
                    out.particle(Role.MAIN, x + c * 0.5, y + t * 2.3, z + s * 0.5, -s * 0.025, 0.035, c * 0.025);
                }
                for (int i = 0; i < 7; i++) {
                    out.particle(Role.ACCENT, x + (r.nextDouble() - 0.5) * 0.4, y + 0.4 + r.nextDouble() * 1.2,
                            z + (r.nextDouble() - 0.5) * 0.4, 0, 0.05, 0);
                }
            }
            case HEARTS -> {
                // A big heart drawn in hearts, facing the viewer, floating up.
                double rx = Math.cos(Math.toRadians(viewYaw));
                double rz = Math.sin(Math.toRadians(viewYaw));
                int pts = Math.max(18, Math.round(28 * Math.min(1.5f, scale)));
                for (int i = 0; i < pts; i++) {
                    double t = i * Math.PI * 2 / pts;
                    double hx = 16 * Math.pow(Math.sin(t), 3) / 17.0;
                    double hy = (13 * Math.cos(t) - 5 * Math.cos(2 * t) - 2 * Math.cos(3 * t) - Math.cos(4 * t)) / 17.0;
                    double size = 0.95;
                    out.particle(Role.MAIN, x + rx * hx * size, y + 1.35 + hy * size, z + rz * hx * size, 0, 0, 0);
                }
                for (int i = 0; i < 5; i++) {
                    out.particle(Role.ACCENT, x + (r.nextDouble() - 0.5) * 0.3, y + 1.2 + (r.nextDouble() - 0.5) * 0.3,
                            z + (r.nextDouble() - 0.5) * 0.3, 0, 0, 0);
                }
            }
            case SPARKS -> {
                // A star of straight light rays shooting out of the chest in every direction.
                int rays = Math.max(8, Math.round(14 * Math.min(1.5f, scale)));
                double golden = Math.PI * (3 - Math.sqrt(5));
                for (int k = 0; k < rays; k++) {
                    double dy = 1 - (k + 0.5) / rays * 2;
                    double rr = Math.sqrt(1 - dy * dy);
                    double a = golden * k;
                    double ddx = Math.cos(a) * rr;
                    double ddz = Math.sin(a) * rr;
                    for (int j = 0; j < 6; j++) {
                        double d = 0.08 + j * 0.1;
                        double v = 0.025 + j * 0.03;
                        out.particle(j == 5 ? Role.ACCENT : Role.MAIN, x + ddx * d, y + 1.1 + dy * d, z + ddz * d,
                                ddx * v, dy * v, ddz * v);
                    }
                }
            }
            case FIREWORK -> {
                // A rocket climbing out of the body, then a firework of a random shape.
                for (int i = 0; i < 14; i++) {
                    double t = i / 14.0;
                    out.particle(Role.MAIN, x + (r.nextDouble() - 0.5) * 0.05, y + 0.4 + t * 1.9, z + (r.nextDouble() - 0.5) * 0.05,
                            (r.nextDouble() - 0.5) * 0.01, -0.02, (r.nextDouble() - 0.5) * 0.01);
                }
                out.firework(FIREWORK_SHAPES[r.nextInt(FIREWORK_SHAPES.length)], colors, x, y + 2.4, z);
            }
            default -> {
            }
        }
    }

    /**
     * Directions (unit-ish, x/y in the viewer's plane for flat shapes, z out of it) of a firework's sparks, for drawing
     * one where there are no vanilla particles (the Studio).
     */
    public static double[][] fireworkSparks(FireworkExplosion.Shape shape, RandomSource r) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        switch (shape) {
            case STAR -> {
                for (int k = 0; k < 10; k++) {
                    double a0 = Math.PI / 2 + k * Math.PI / 5;
                    double a1 = Math.PI / 2 + (k + 1) * Math.PI / 5;
                    double r0 = k % 2 == 0 ? 1 : 0.42;
                    double r1 = k % 2 == 0 ? 0.42 : 1;
                    for (int j = 0; j < 5; j++) {
                        double t = j / 5.0;
                        double px = Math.cos(a0) * r0 * (1 - t) + Math.cos(a1) * r1 * t;
                        double py = Math.sin(a0) * r0 * (1 - t) + Math.sin(a1) * r1 * t;
                        out.add(new double[]{px, py, 0});
                    }
                }
            }
            case CREEPER -> {
                // The creeper face: two eyes and the mouth, as a grid of sparks.
                int[][] face = {
                        {1, 1, 0, 0, 1, 1},
                        {1, 1, 0, 0, 1, 1},
                        {0, 0, 1, 1, 0, 0},
                        {0, 1, 1, 1, 1, 0},
                        {0, 1, 0, 0, 1, 0}
                };
                for (int row = 0; row < face.length; row++) {
                    for (int col = 0; col < 6; col++) {
                        if (face[row][col] == 1) {
                            out.add(new double[]{(col - 2.5) / 3.0, (2 - row) / 3.0 + 0.1, 0});
                        }
                    }
                }
            }
            case BURST -> {
                for (int i = 0; i < 50; i++) {
                    double a = r.nextDouble() * Math.PI * 2;
                    double up = 0.3 + r.nextDouble() * 0.7;
                    double side = Math.sqrt(1 - up * up) * (0.4 + r.nextDouble() * 0.6);
                    out.add(new double[]{Math.cos(a) * side, up * (0.5 + r.nextDouble() * 0.5), Math.sin(a) * side});
                }
            }
            default -> {
                double golden = Math.PI * (3 - Math.sqrt(5));
                int n = 70;
                for (int k = 0; k < n; k++) {
                    double dy = 1 - (k + 0.5) / n * 2;
                    double rr = Math.sqrt(1 - dy * dy);
                    out.add(new double[]{Math.cos(golden * k) * rr, dy, Math.sin(golden * k) * rr});
                }
            }
        }
        return out.toArray(new double[0][]);
    }

    /**
     * A firework drawn with effect sprites, about two blocks across, so a kill right in front of me doesn't fill the
     * screen the way a vanilla firework does: the shape's sparks fly out with a streak and a twinkle each.
     */
    public static void drawFirework(FxField field, FireworkExplosion.Shape shape, int[] colors, RandomSource r,
                                    double x, double y, double z, float viewYaw) {
        double[][] sparks = fireworkSparks(shape, r);
        boolean flat = shape == FireworkExplosion.Shape.STAR || shape == FireworkExplosion.Shape.CREEPER;
        double rx = Math.cos(Math.toRadians(viewYaw));
        double rz = Math.sin(Math.toRadians(viewYaw));
        double speed = 4.2;
        for (double[] d : sparks) {
            double vx = flat ? rx * d[0] * speed : d[0] * speed;
            double vy = d[1] * speed;
            double vz = flat ? rz * d[0] * speed : d[2] * speed;
            int c = colors[r.nextInt(colors.length)] | 0xFF000000;
            field.spawn(FxSprite.TWINKLE).at(x, y, z).vel(vx, vy, vz).drag(0.12f).gravity(1.2f).life(1.4f + r.nextFloat() * 0.5f)
                    .size(0.08f, 0.02f).flicker(0.3f).core(0.4f).ramp(0xFFFFFFFF, c, c & 0x00FFFFFF);
            field.spawn(FxSprite.SPARK).at(x, y, z).vel(vx, vy, vz).drag(0.12f).gravity(1.2f).life(0.6f).size(0.035f, 0f)
                    .stretch(0.06f).fade(c);
        }
        field.spawn(FxSprite.FLARE).at(x, y, z).life(0.25f).size(1.0f, 0f).grow(Fx.Grow.POP).fade(colors[0] | 0xFF000000).core(0.3f);
    }
}
