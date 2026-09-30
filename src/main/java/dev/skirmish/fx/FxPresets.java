package dev.skirmish.fx;

import net.minecraft.util.RandomSource;

/**
 * Every effect as a recipe of particles. The same recipes run in the world (on real players) and in the Studio (on
 * the models), so what the Studio shows is what the game shows. Positions are in blocks; {@code scale} enlarges
 * sizes and counts; {@code phase} (0..1, from the clock) drives Rainbow.
 */
public final class FxPresets {
    private static final RandomSource R = RandomSource.create();

    private FxPresets() {
    }

    /** The clock phase for Rainbow colours: one turn every four seconds. */
    public static float phase() {
        return (System.currentTimeMillis() % 4000L) / 4000f;
    }

    private static double rnd(double spread) {
        return (R.nextDouble() * 2 - 1) * spread;
    }

    private static float between(float a, float b) {
        return a + R.nextFloat() * (b - a);
    }

    /** A random direction on the unit sphere, times {@code speed}. */
    private static double[] sphere(double speed) {
        double u = R.nextDouble() * 2 - 1;
        double a = R.nextDouble() * Math.PI * 2;
        double s = Math.sqrt(1 - u * u);
        return new double[]{Math.cos(a) * s * speed, u * speed, Math.sin(a) * s * speed};
    }

    private static int count(int base, float scale) {
        return Math.max(1, Math.round(base * scale));
    }

    // ---- trails ----

    /** Particles per second a trail sheds at density 1. */
    public static float trailRate(FxStyles.Trail style) {
        return switch (style) {
            case SMOKE, PETALS -> 14f;
            case SNOW, STARS -> 18f;
            case RAINBOW -> 60f;
            default -> 30f;
        };
    }

    /**
     * One particle of a trail shed at the feet ({@code x, y, z}); {@code mx, mz} is the direction of motion (blocks
     * per second) so the trail streams behind.
     */
    public static void trail(FxField f, FxStyles.Trail style, FxPalette pal, double x, double y, double z,
                             double mx, double mz, float scale) {
        float ph = phase();
        double px = x + rnd(0.22);
        double py = y + 0.08 + R.nextDouble() * 0.25;
        double pz = z + rnd(0.22);
        double bx = -mx * 0.15;
        double bz = -mz * 0.15;
        switch (style) {
            case OFF -> {
            }
            case SPARKS -> f.spawn(FxShape.SPARK).at(px, py, pz).vel(bx + rnd(0.7), between(0.6f, 1.8f), bz + rnd(0.7))
                    .gravity(4f).drag(0.35f).life(between(0.45f, 0.8f)).size(0.03f * scale, 0.01f).fade(pal.pick(R, ph)).stretch(0.07f);
            case STARS -> f.spawn(FxShape.STAR).at(px, py + 0.2, pz).vel(bx, between(0.1f, 0.45f), bz)
                    .life(between(0.7f, 1.1f)).size(0.09f * scale, 0f).fade(pal.pick(R, ph)).spin(R.nextFloat() * 6, between(-3f, 3f));
            case RAINBOW -> f.spawn(FxShape.GLOW).at(x + rnd(0.05), y + 0.25, z + rnd(0.05)).vel(0, 0.05, 0)
                    .life(0.9f).size(0.17f * scale, 0.03f).fade(FxColor.hsv(ph * 2f, 0.8f, 1f) & 0xE0FFFFFF);
            case SOULS -> {
                f.spawn(FxShape.GLOW).at(px, py, pz).vel(bx + rnd(0.1), between(0.4f, 0.9f), bz + rnd(0.1))
                        .life(between(0.9f, 1.3f)).size(0.12f * scale, 0f).fade(FxPalette.SOUL.pick(R, ph) & 0xD0FFFFFF);
                if (R.nextInt(3) == 0) {
                    f.spawn(FxShape.DOT).at(px, py, pz).vel(rnd(0.2), between(0.8f, 1.4f), rnd(0.2)).life(0.8f)
                            .size(0.025f * scale, 0f).fade(0xFFE8FDFF);
                }
            }
            case FLAME -> f.spawn(FxShape.GLOW).at(px, py, pz).vel(bx + rnd(0.15), between(0.5f, 1.1f), bz + rnd(0.15))
                    .life(between(0.4f, 0.7f)).size(0.15f * scale, 0.02f)
                    .color(FxPalette.FIRE.light(ph), FxPalette.FIRE.main(ph) & 0x00FFFFFF);
            case PETALS -> f.spawn(FxShape.SHARD).at(px, py + 0.4, pz).vel(bx + rnd(0.4), between(-0.1f, 0.3f), bz + rnd(0.4))
                    .gravity(0.5f).drag(0.5f).life(between(1.6f, 2.4f)).size(0.07f * scale, 0.05f * scale)
                    .fade(FxPalette.ROSE.pick(R, ph) & 0xE8FFFFFF).spin(R.nextFloat() * 6, between(-4f, 4f));
            case SNOW -> f.spawn(FxShape.DOT).at(px, py + 0.5, pz).vel(bx + rnd(0.25), between(-0.35f, -0.1f), bz + rnd(0.25))
                    .life(between(1.6f, 2.2f)).size(0.03f * scale, 0.02f * scale).fade(0xF0F4FAFF);
            case SMOKE -> f.spawn(FxShape.SMOKE).at(px, py, pz).vel(bx + rnd(0.15), between(0.2f, 0.45f), bz + rnd(0.15))
                    .drag(0.5f).life(between(1.2f, 1.8f)).size(0.12f * scale, 0.45f * scale).fade(0x70585868);
            case CRYSTALS -> {
                f.spawn(FxShape.SHARD).at(px, py, pz).vel(bx + rnd(0.6), between(0.8f, 1.6f), bz + rnd(0.6))
                        .gravity(3f).life(between(0.7f, 1.1f)).size(0.06f * scale, 0.02f)
                        .fade(FxPalette.ICE.pick(R, ph) & 0xD8FFFFFF).spin(R.nextFloat() * 6, between(-6f, 6f));
                if (R.nextInt(2) == 0) {
                    f.spawn(FxShape.GLOW).at(px, py, pz).life(0.4f).size(0.08f * scale, 0f).fade(FxPalette.ICE.light(ph) & 0xA0FFFFFF);
                }
            }
            case ELECTRIC -> {
                double[] d = sphere(between(3f, 6f));
                f.spawn(FxShape.SPARK).at(px, py + 0.3, pz).vel(d[0], Math.abs(d[1]), d[2]).drag(0.05f)
                        .life(between(0.08f, 0.16f)).size(0.022f * scale, 0.01f).fade(pal.pick(R, ph)).stretch(0.035f);
            }
        }
    }

    // ---- auras ----

    /**
     * An aura around a body standing at {@code x, y, z} ({@code h} tall) for one frame of {@code dt} seconds at
     * clock {@code t} (seconds). Short-lived particles are laid down every frame, so the aura follows smoothly.
     */
    public static void aura(FxField f, FxStyles.Aura style, FxPalette pal, double x, double y, double z, float h,
                            float t, float dt, float scale) {
        float ph = phase();
        float keep = Math.max(0.03f, dt * 1.6f);
        switch (style) {
            case OFF -> {
            }
            case RIPPLE -> {
                if (crossed(t, dt, 0.75f)) {
                    f.spawn(FxShape.RING).at(x, y + 0.03, z).life(1.3f).size(0.15f * scale, 1.25f * scale).fade(pal.main(ph) & 0xE0FFFFFF);
                }
                if (R.nextFloat() < dt * 10) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = (0.4 + R.nextDouble() * 0.6) * scale;
                    f.spawn(FxShape.DOT).at(x + Math.cos(a) * r, y + 0.05, z + Math.sin(a) * r).vel(0, between(0.3f, 0.7f), 0)
                            .life(0.9f).size(0.03f * scale, 0f).fade(pal.light(ph));
                }
            }
            case ORBIT -> {
                for (int k = 0; k < 3; k++) {
                    double a = t * 2.6 + k * Math.PI * 2 / 3;
                    double r = 0.72 * scale;
                    double oy = y + h * 0.55 + Math.sin(t * 2.2 + k * 2.1) * 0.28;
                    f.spawn(FxShape.GLOW).at(x + Math.cos(a) * r, oy, z + Math.sin(a) * r)
                            .life(0.32f).size(0.12f * scale, 0f).fade(k == 1 ? pal.light(ph + 0.3f) : pal.main(ph + k * 0.33f));
                }
            }
            case SPIRAL -> {
                for (int k = 0; k < 2; k++) {
                    double a = t * 3.2 + k * Math.PI;
                    double rise = ((t * 0.7 + k * 0.5) % 1.0) * (h + 0.2);
                    double r = 0.6 * scale;
                    f.spawn(FxShape.GLOW).at(x + Math.cos(a) * r, y + rise, z + Math.sin(a) * r)
                            .life(0.55f).size(0.075f * scale, 0f).fade(pal.main(ph + k * 0.5f));
                }
            }
            case HALO -> {
                f.spawn(FxShape.RING).at(x, y + h + 0.32, z).life(keep).size(0.3f * scale, 0.3f * scale).fade(pal.light(ph) & 0xD0FFFFFF);
                if (R.nextFloat() < dt * 6) {
                    double a = R.nextDouble() * Math.PI * 2;
                    f.spawn(FxShape.STAR).at(x + Math.cos(a) * 0.3 * scale, y + h + 0.32, z + Math.sin(a) * 0.3 * scale)
                            .vel(0, 0.2, 0).life(0.6f).size(0.07f * scale, 0f).fade(pal.light(ph)).spin(0, 3f);
                }
            }
            case RUNES -> {
                double r = 0.95 * scale;
                f.spawn(FxShape.RING).at(x, y + 0.03, z).life(keep).size((float) r, (float) r).fade(pal.main(ph) & 0x70FFFFFF);
                for (int k = 0; k < 6; k++) {
                    double a = t * 0.8 + k * Math.PI / 3;
                    float pulse = 0.75f + 0.25f * (float) Math.sin(t * 4 + k);
                    f.spawn(FxShape.STAR).at(x + Math.cos(a) * r, y + 0.12, z + Math.sin(a) * r).life(keep)
                            .size(0.1f * scale * pulse, 0.1f * scale * pulse).fade(pal.light(ph + k / 6f)).spin((float) a, 0f);
                }
            }
            case FIREFLIES -> {
                if (R.nextFloat() < dt * 14) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = (0.3 + R.nextDouble() * 0.7) * scale;
                    f.spawn(FxShape.GLOW).at(x + Math.cos(a) * r, y + R.nextDouble() * h, z + Math.sin(a) * r)
                            .vel(rnd(0.25), rnd(0.2), rnd(0.25)).life(between(1.2f, 1.8f)).size(0.07f * scale, 0f)
                            .fade(pal.pick(R, ph));
                }
            }
            case FLAMES -> {
                int n = Math.max(1, Math.round(dt * 70));
                for (int i = 0; i < n; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = 0.8 * scale;
                    f.spawn(FxShape.GLOW).at(x + Math.cos(a) * r, y + 0.05, z + Math.sin(a) * r).vel(0, between(0.5f, 1.0f), 0)
                            .life(between(0.35f, 0.55f)).size(0.13f * scale, 0.02f)
                            .color(FxPalette.FIRE.light(ph), FxPalette.FIRE.main(ph) & 0x00FFFFFF);
                }
            }
        }
    }

    /** Whether a beat of {@code every} seconds fell inside the last frame. */
    private static boolean crossed(float t, float dt, float every) {
        return Math.floor(t / every) != Math.floor((t - dt) / every);
    }

    // ---- hits ----

    /**
     * A hit on something at {@code x, y, z} (its middle); {@code dx, dz} points from the attacker to it. A critical
     * hit is bigger and uses {@code critPal}.
     */
    public static void hit(FxField f, FxStyles.Hit style, FxPalette pal, FxPalette critPal, boolean crit,
                           double x, double y, double z, double dx, double dz, float scale) {
        float ph = phase();
        FxPalette p = crit ? critPal : pal;
        float s = scale * (crit ? 1.45f : 1f);
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx = len < 1e-4 ? 0 : dx / len;
        double nz = len < 1e-4 ? 0 : dz / len;
        switch (style) {
            case OFF -> {
            }
            case SPARKS -> {
                for (int i = 0; i < count(14, s); i++) {
                    double[] d = sphere(between(2f, 4.5f));
                    f.spawn(FxShape.SPARK).at(x, y, z).vel(d[0] + nx * 2.5, d[1] + 1.2, d[2] + nz * 2.5)
                            .gravity(7f).drag(0.3f).life(between(0.3f, 0.55f)).size(0.028f * s, 0.01f).fade(p.pick(R, ph)).stretch(0.05f);
                }
                f.spawn(FxShape.GLOW).at(x, y, z).life(0.13f).size(0.4f * s, 0.1f).fade(p.light(ph));
            }
            case FLASH -> {
                f.spawn(FxShape.GLOW).at(x, y, z).life(0.18f).size(0.95f * s, 0f).fade(p.light(ph));
                f.spawn(FxShape.FACE_RING).at(x, y, z).life(0.3f).size(0.15f * s, 1.05f * s).fade(p.main(ph));
                for (int i = 0; i < count(6, s); i++) {
                    double[] d = sphere(3.5);
                    f.spawn(FxShape.SPARK).at(x, y, z).vel(d[0], d[1], d[2]).drag(0.2f).life(0.25f).size(0.025f * s, 0f).fade(p.light(ph));
                }
            }
            case SHARDS -> {
                for (int i = 0; i < count(10, s); i++) {
                    double[] d = sphere(between(1.8f, 3.4f));
                    f.spawn(FxShape.SHARD).at(x, y, z).vel(d[0] + nx * 1.5, d[1] + 1.6, d[2] + nz * 1.5)
                            .gravity(10f).life(between(0.55f, 0.85f)).size(between(0.05f, 0.09f) * s, 0.02f)
                            .fade(p.pick(R, ph) & 0xE8FFFFFF).spin(R.nextFloat() * 6, between(-9f, 9f));
                }
            }
            case IMPACT -> {
                f.spawn(FxShape.RING).at(x, y - 0.2, z).life(0.35f).size(0.2f * s, 1.15f * s).fade(p.main(ph));
                f.spawn(FxShape.STAR).at(x, y, z).life(0.25f).size(0.38f * s, 0f).fade(p.light(ph)).spin(0, 5f);
                for (int i = 0; i < count(5, s); i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    f.spawn(FxShape.DOT).at(x, y - 0.2, z).vel(Math.cos(a) * 2.4, 0.3, Math.sin(a) * 2.4).drag(0.1f)
                            .life(0.3f).size(0.03f * s, 0f).fade(p.light(ph));
                }
            }
            case BLOOD -> {
                for (int i = 0; i < count(12, s); i++) {
                    double[] d = sphere(between(1.2f, 2.6f));
                    FxShape shape = i % 3 == 0 ? FxShape.SHARD : FxShape.SMOKE;
                    int c = FxPalette.BLOOD.pick(R, ph);
                    f.spawn(shape).at(x, y, z).vel(d[0] + nx * 1.6, d[1] + 1.2, d[2] + nz * 1.6).gravity(11f)
                            .life(between(0.5f, 0.85f)).size(between(0.035f, 0.07f) * s, 0.015f)
                            .fade(FxColor.lerp(c, 0xFF5A0010, 0.4f) & 0xF0FFFFFF).spin(R.nextFloat() * 6, between(-6f, 6f));
                }
            }
            case STARS -> {
                for (int i = 0; i < count(5, s); i++) {
                    double a = i * Math.PI * 2 / count(5, s) + R.nextDouble() * 0.5;
                    f.spawn(FxShape.STAR).at(x, y + 0.2, z).vel(Math.cos(a) * 1.3, between(0.6f, 1.4f), Math.sin(a) * 1.3)
                            .drag(0.2f).life(0.6f).size(0.13f * s, 0f).fade(p.pick(R, ph)).spin(R.nextFloat() * 6, 5f);
                }
            }
            case ELECTRIC -> {
                Runnable zap = () -> {
                    for (int i = 0; i < count(8, s); i++) {
                        double[] d = sphere(between(5f, 8f));
                        f.spawn(FxShape.SPARK).at(x + rnd(0.2), y + rnd(0.4), z + rnd(0.2)).vel(d[0], d[1], d[2]).drag(0.05f)
                                .life(between(0.08f, 0.15f)).size(0.026f * s, 0.01f).fade(p.light(ph)).stretch(0.03f);
                    }
                    f.spawn(FxShape.GLOW).at(x, y, z).life(0.09f).size(0.5f * s, 0f).fade(p.main(ph) & 0xB0FFFFFF);
                };
                zap.run();
                f.later(0.06f, zap);
            }
            case SLASH -> {
                // An arc across the target, square to the swing.
                double sx = -nz;
                double sz = nx;
                if (len < 1e-4) {
                    sx = 1;
                    sz = 0;
                }
                int n = count(14, s);
                for (int i = 0; i < n; i++) {
                    double k = i / (double) (n - 1) * 2 - 1;
                    double ax = x - nx * 0.25 + sx * k * 0.8 * s;
                    double ay = y + 0.45 * s - Math.abs(k) * 0.1 - k * 0.45 * s;
                    double az = z - nz * 0.25 + sz * k * 0.8 * s;
                    int c = FxColor.lerp(p.light(ph), p.main(ph), (float) Math.abs(k));
                    f.later(i * 0.008f, () -> f.spawn(FxShape.GLOW).at(ax, ay, az).life(0.26f).size(0.11f * s, 0f).fade(c));
                }
            }
        }
        if (crit && style != FxStyles.Hit.OFF && style != FxStyles.Hit.STARS) {
            f.spawn(FxShape.STAR).at(x, y + 0.1, z).life(0.3f).size(0.3f * s, 0f).fade(critPal.light(ph)).spin(0.4f, 6f);
        }
    }

    // ---- kills ----

    /** A kill burst over a body lying at {@code x, y, z}. */
    public static void kill(FxField f, FxStyles.Kill style, FxPalette pal, double x, double y, double z, float scale) {
        float ph = phase();
        double cy = y + 1.0;
        switch (style) {
            case SUPERNOVA -> {
                f.spawn(FxShape.GLOW).at(x, cy, z).life(0.35f).size(2.1f * scale, 0f).fade(pal.light(ph));
                f.spawn(FxShape.RING).at(x, y + 0.05, z).life(0.85f).size(0.3f * scale, 3.6f * scale).fade(pal.main(ph));
                f.later(0.12f, () -> f.spawn(FxShape.RING).at(x, y + 0.05, z).life(0.7f).size(0.2f * scale, 2.4f * scale).fade(pal.light(ph)));
                for (int i = 0; i < count(40, scale); i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double sp = between(4f, 7.5f);
                    f.spawn(FxShape.SPARK).at(x, cy, z).vel(Math.cos(a) * sp, between(-0.5f, 2.5f), Math.sin(a) * sp)
                            .drag(0.18f).gravity(2f).life(between(0.6f, 0.95f)).size(0.035f * scale, 0f).fade(pal.pick(R, ph)).stretch(0.06f);
                }
                for (int i = 0; i < count(18, scale); i++) {
                    f.spawn(FxShape.DOT).at(x + rnd(0.5), y + R.nextDouble() * 1.8, z + rnd(0.5)).vel(rnd(0.3), between(1f, 2.5f), rnd(0.3))
                            .drag(0.4f).life(between(0.8f, 1.3f)).size(0.035f * scale, 0f).fade(pal.light(ph));
                }
            }
            case CONFETTI -> {
                for (int i = 0; i < count(70, scale); i++) {
                    double[] d = sphere(2.2);
                    f.spawn(FxShape.SQUARE).at(x + rnd(0.3), cy, z + rnd(0.3)).vel(d[0], between(4.5f, 8f), d[2])
                            .gravity(8f).drag(0.3f).life(between(1.8f, 2.8f)).size(0.06f * scale, 0.05f * scale)
                            .fade(FxColor.hsv(R.nextFloat(), 0.75f, 1f)).spin(R.nextFloat() * 6, between(-11f, 11f));
                }
                f.later(0.18f, () -> {
                    for (int i = 0; i < 5; i++) {
                        double[] d = sphere(2.5);
                        f.spawn(FxShape.STAR).at(x, y + 2.6, z).vel(d[0], d[1], d[2]).drag(0.2f).life(0.7f)
                                .size(0.2f * scale, 0f).fade(FxColor.hsv(R.nextFloat(), 0.5f, 1f)).spin(0, 4f);
                    }
                });
            }
            case SHATTER -> {
                f.spawn(FxShape.FACE_RING).at(x, cy, z).life(0.3f).size(0.3f * scale, 1.6f * scale).fade(0xFFE6F6FF);
                for (int i = 0; i < count(46, scale); i++) {
                    double[] d = sphere(between(2.5f, 5f));
                    int c = FxColor.lerp(pal.light(ph), 0xFFFFFFFF, 0.4f) & 0xB8FFFFFF;
                    f.spawn(FxShape.SHARD).at(x + rnd(0.3), y + R.nextDouble() * 1.8, z + rnd(0.3)).vel(d[0], d[1] + 2, d[2])
                            .gravity(11f).life(between(1f, 1.5f)).size(between(0.07f, 0.16f) * scale, 0.04f)
                            .fade(c).spin(R.nextFloat() * 6, between(-10f, 10f));
                }
            }
            case SOUL_RISE -> {
                f.spawn(FxShape.RING).at(x, y + 0.05, z).life(1.2f).size(0.4f * scale, 1.4f * scale).fade(FxPalette.SOUL.main(ph));
                for (int k = 0; k < 8; k++) {
                    f.later(k * 0.09f, () -> {
                        for (int i = 0; i < count(8, scale); i++) {
                            f.spawn(FxShape.GLOW).at(x + rnd(0.4), y + R.nextDouble() * 0.6, z + rnd(0.4)).vel(rnd(0.15), between(2f, 4.2f), rnd(0.15))
                                    .drag(0.6f).life(between(0.9f, 1.4f)).size(0.16f * scale, 0f).fade(FxPalette.SOUL.pick(R, ph) & 0xD8FFFFFF);
                        }
                    });
                }
                f.later(0.75f, () -> f.spawn(FxShape.GLOW).at(x, y + 3.2, z).life(0.5f).size(1.2f * scale, 0f).fade(FxPalette.SOUL.light(ph)));
            }
            case BLOOD_BURST -> {
                f.spawn(FxShape.RING).at(x, y + 0.04, z).life(0.9f).size(0.3f * scale, 1.8f * scale).fade(0xC0B0001A);
                for (int i = 0; i < count(60, scale); i++) {
                    double[] d = sphere(between(2f, 4.5f));
                    f.spawn(i % 3 == 0 ? FxShape.SHARD : FxShape.SMOKE).at(x + rnd(0.3), y + R.nextDouble() * 1.8, z + rnd(0.3))
                            .vel(d[0], d[1] + 1.5, d[2]).gravity(12f).life(between(0.8f, 1.3f))
                            .size(between(0.04f, 0.09f) * scale, 0.02f).fade(FxColor.lerp(FxPalette.BLOOD.pick(R, ph), 0xFF4A000A, 0.35f))
                            .spin(R.nextFloat() * 6, between(-6f, 6f));
                }
            }
            case VORTEX -> {
                int n = count(40, scale);
                for (int i = 0; i < n; i++) {
                    double a = i * Math.PI * 2 / n;
                    double r = 1.7 * scale;
                    double py = y + (i % 5) * 0.45;
                    f.spawn(FxShape.GLOW).at(x + Math.cos(a) * r, py, z + Math.sin(a) * r)
                            .vel(-Math.sin(a) * 5 - Math.cos(a) * 3, 0.4, Math.cos(a) * 5 - Math.sin(a) * 3)
                            .drag(0.05f).life(0.5f).size(0.12f * scale, 0.03f).fade(pal.pick(R, ph));
                }
                f.later(0.45f, () -> {
                    f.spawn(FxShape.GLOW).at(x, cy, z).life(0.3f).size(1.6f * scale, 0f).fade(pal.light(ph));
                    for (int i = 0; i < count(30, scale); i++) {
                        double[] d = sphere(between(4f, 7f));
                        f.spawn(FxShape.SPARK).at(x, cy, z).vel(d[0], d[1], d[2]).drag(0.2f).life(0.6f)
                                .size(0.035f * scale, 0f).fade(pal.pick(R, ph)).stretch(0.06f);
                    }
                });
            }
            case BLOOM -> {
                f.spawn(FxShape.SPARK).at(x, y + 0.2, z).vel(0, 14, 0).drag(0.05f).life(0.18f).size(0.05f * scale, 0.03f).fade(pal.light(ph)).stretch(0.04f);
                f.later(0.17f, () -> {
                    double by = y + 2.7;
                    f.spawn(FxShape.GLOW).at(x, by, z).life(0.3f).size(1.3f * scale, 0f).fade(pal.light(ph));
                    for (int i = 0; i < count(60, scale); i++) {
                        double[] d = sphere(between(4f, 6f));
                        f.spawn(i % 4 == 0 ? FxShape.STAR : FxShape.SPARK).at(x, by, z).vel(d[0], d[1], d[2]).drag(0.22f).gravity(1.8f)
                                .life(between(1.1f, 1.6f)).size((i % 4 == 0 ? 0.09f : 0.035f) * scale, 0f).fade(pal.pick(R, ph))
                                .stretch(0.07f).spin(R.nextFloat() * 6, 3f);
                    }
                });
            }
            case PILLAR -> {
                for (int i = 0; i < 16; i++) {
                    double py = y + i * 0.5;
                    f.later(i * 0.015f, () -> f.spawn(FxShape.GLOW).at(x, py, z).life(0.9f).size(0.45f * scale, 0f).fade(pal.light(ph) & 0xC0FFFFFF));
                }
                f.spawn(FxShape.RING).at(x, y + 0.05, z).life(0.8f).size(0.3f * scale, 1.6f * scale).fade(pal.main(ph));
                for (int i = 0; i < count(20, scale); i++) {
                    f.spawn(FxShape.SPARK).at(x + rnd(0.3), y + R.nextDouble() * 2, z + rnd(0.3)).vel(rnd(0.5), between(3f, 7f), rnd(0.5))
                            .drag(0.3f).life(between(0.5f, 0.9f)).size(0.03f * scale, 0f).fade(pal.pick(R, ph)).stretch(0.06f);
                }
            }
        }
    }

    // ---- totems ----

    /** A totem saving someone standing at {@code x, y, z}. */
    public static void totem(FxField f, FxStyles.Totem style, double x, double y, double z, float scale) {
        float ph = phase();
        FxPalette pal = switch (style) {
            case EMERALD -> FxPalette.EMERALD;
            case SOUL -> FxPalette.SOUL;
            case RAINBOW -> FxPalette.RAINBOW;
            case NOVA -> FxPalette.WHITE;
            case PHOENIX -> FxPalette.FIRE;
            default -> FxPalette.GOLD;
        };
        switch (style) {
            case VANILLA -> {
            }
            case NOVA -> {
                f.spawn(FxShape.GLOW).at(x, y + 1, z).life(0.28f).size(2.6f * scale, 0f).fade(0xFFFFFFFF);
                f.spawn(FxShape.FACE_RING).at(x, y + 1, z).life(0.45f).size(0.3f * scale, 2.4f * scale).fade(0xFFDDEBFF);
                for (int i = 0; i < count(50, scale); i++) {
                    double[] d = sphere(between(4f, 7f));
                    f.spawn(FxShape.SPARK).at(x, y + 1, z).vel(d[0], d[1], d[2]).drag(0.2f).life(0.6f)
                            .size(0.03f * scale, 0f).fade(FxPalette.ICE.pick(R, ph)).stretch(0.06f);
                }
            }
            case PHOENIX -> {
                for (int k = 0; k < 2; k++) {
                    for (int i = 0; i < 26; i++) {
                        double a = i * 0.45 + k * Math.PI;
                        double py = y + i * 0.09;
                        double r = 0.7 * scale;
                        f.later(i * 0.02f, () -> f.spawn(FxShape.GLOW).at(x + Math.cos(a) * r, py, z + Math.sin(a) * r).vel(0, 1.2, 0)
                                .life(0.6f).size(0.16f * scale, 0.02f).color(FxPalette.FIRE.light(ph), FxPalette.FIRE.main(ph) & 0x00FFFFFF));
                    }
                }
                f.later(0.5f, () -> {
                    f.spawn(FxShape.GLOW).at(x, y + 2.4, z).life(0.4f).size(1.2f * scale, 0f).fade(FxPalette.FIRE.light(ph));
                    for (int i = 0; i < count(30, scale); i++) {
                        double[] d = sphere(between(3f, 5f));
                        f.spawn(FxShape.SPARK).at(x, y + 2.4, z).vel(d[0], d[1] + 1, d[2]).gravity(4f).drag(0.3f).life(0.8f)
                                .size(0.03f * scale, 0f).fade(FxPalette.FIRE.pick(R, ph)).stretch(0.06f);
                    }
                });
            }
            default -> {
                for (int k = 0; k < 3; k++) {
                    double ry = y + 0.1 + k * 0.8;
                    f.later(k * 0.12f, () -> f.spawn(FxShape.RING).at(x, ry, z).life(0.75f).size(0.2f * scale, 1.6f * scale).fade(pal.main(ph)));
                }
                for (int i = 0; i < count(40, scale); i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    f.spawn(FxShape.SPARK).at(x + Math.cos(a) * 0.5, y + R.nextDouble() * 0.4, z + Math.sin(a) * 0.5)
                            .vel(Math.cos(a) * 1.5, between(3f, 6.5f), Math.sin(a) * 1.5).gravity(3f).drag(0.4f)
                            .life(between(0.8f, 1.2f)).size(0.032f * scale, 0f).fade(pal.pick(R, ph)).stretch(0.06f);
                }
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    f.spawn(FxShape.STAR).at(x + Math.cos(a) * 0.9 * scale, y + 1.1, z + Math.sin(a) * 0.9 * scale).vel(0, 0.8, 0)
                            .life(0.9f).size(0.14f * scale, 0f).fade(pal.light(ph + i / 8f)).spin((float) a, 3f);
                }
                if (style == FxStyles.Totem.EMERALD) {
                    for (int i = 0; i < count(14, scale); i++) {
                        double[] d = sphere(3);
                        f.spawn(FxShape.SHARD).at(x, y + 1, z).vel(d[0], d[1] + 2, d[2]).gravity(8f).life(1f)
                                .size(0.08f * scale, 0.03f).fade(pal.pick(R, ph) & 0xE0FFFFFF).spin(R.nextFloat() * 6, 8f);
                    }
                }
            }
        }
    }
}
