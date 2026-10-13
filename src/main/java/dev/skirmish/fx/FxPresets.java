package dev.skirmish.fx;

import net.minecraft.util.RandomSource;

/**
 * Every effect as a recipe of sprites, ribbons and arcs. The same recipes run in the world (on real players) and in
 * the Studio (on the models), so what the Studio shows is what the game shows. Positions are in blocks; {@code scale}
 * enlarges sizes and counts; colours come from a palette, Rainbow walking the hue wheel with the clock.
 */
public final class FxPresets {
    private static final RandomSource R = RandomSource.create();
    /** The rainbow trail's stripes, top to bottom. */
    private static final int[] RAINBOW = {0xFFFF3B30, 0xFFFF9500, 0xFFFFD60A, 0xFF34C759, 0xFF0A84FF, 0xFFAF52DE};

    private FxPresets() {
    }

    /** What a continuous effect remembers between frames for one body (its ribbons, its pace). */
    public static final class Emitter {
        float trailDebt;
        float sparkleDebt;
        FxStyles.Trail trailStyle;
        final FxRibbon[] stripes = new FxRibbon[RAINBOW.length];
        FxStyles.Aura auraStyle;
        final FxRibbon[] aura = new FxRibbon[3];
        final float[] lastRise = new float[3];
        float beat;

        void stopAura() {
            for (int i = 0; i < aura.length; i++) {
                if (aura[i] != null) {
                    aura[i].stop();
                    aura[i] = null;
                }
            }
        }

        void stopTrail() {
            for (int i = 0; i < stripes.length; i++) {
                if (stripes[i] != null) {
                    stripes[i].stop();
                    stripes[i] = null;
                }
            }
        }

        /** Lets this body's ribbons fade out (it vanished, or effects were turned off). */
        public void stop() {
            stopAura();
            stopTrail();
            auraStyle = null;
            trailStyle = null;
        }
    }

    /** Each style's own colours, used when the colour setting is Signature. */
    public static FxPalette signature(FxStyles.Trail style) {
        return switch (style) {
            case SPARKS -> FxPalette.GOLD;
            case STARS, SNOW, SMOKE -> FxPalette.WHITE;
            case SOULS -> FxPalette.SOUL;
            case FLAME -> FxPalette.FIRE;
            case PETALS -> FxPalette.ROSE;
            case CRYSTALS -> FxPalette.EMERALD;
            case ELECTRIC -> FxPalette.ICE;
            case HEARTS -> FxPalette.BLOOD;
            default -> FxPalette.RAINBOW;
        };
    }

    public static FxPalette signature(FxStyles.Aura style) {
        return switch (style) {
            case RIPPLE -> FxPalette.ICE;
            case SPIRAL -> FxPalette.RAINBOW;
            case HALO -> FxPalette.GOLD;
            case RUNES -> FxPalette.VOID;
            case FIREFLIES -> FxPalette.TOXIC;
            case FLAMES -> FxPalette.FIRE;
            default -> FxPalette.ACCENT;
        };
    }

    public static FxPalette signature(FxStyles.Hit style) {
        return switch (style) {
            case SPARKS, STARS -> FxPalette.GOLD;
            case FLASH, SLASH -> FxPalette.WHITE;
            case SHARDS, ELECTRIC -> FxPalette.ICE;
            case BLOOD -> FxPalette.BLOOD;
            default -> FxPalette.ACCENT;
        };
    }

    /** The colours of a critical hit when the crit colour is Signature: hotter than the plain hit. */
    public static FxPalette critSignature(FxStyles.Hit style) {
        return switch (style) {
            case SPARKS, STARS -> FxPalette.FIRE;
            case SHARDS -> FxPalette.WHITE;
            case ELECTRIC -> FxPalette.VOID;
            case BLOOD -> FxPalette.BLOOD;
            default -> FxPalette.GOLD;
        };
    }

    public static FxPalette signature(FxStyles.Kill style) {
        return switch (style) {
            case SHATTER -> FxPalette.ICE;
            case SOUL_RISE -> FxPalette.SOUL;
            case BLOOD_BURST -> FxPalette.BLOOD;
            case VORTEX -> FxPalette.VOID;
            case BLOOM, CONFETTI -> FxPalette.RAINBOW;
            case PILLAR -> FxPalette.GOLD;
            default -> FxPalette.ACCENT;
        };
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

    /** Fades from the light colour through the main one to nothing. */
    private static Fx tint(Fx fx, FxPalette pal, float ph) {
        return fx.ramp(pal.light(ph), pal.main(ph), pal.main(ph) & 0x00FFFFFF);
    }

    private static Fx flash(FxField f, double x, double y, double z, float size, int color, float life) {
        return f.spawn(FxSprite.FLARE).at(x, y, z).size(size, 0f).grow(Fx.Grow.POP).life(life).fade(color).core(0.35f)
                .spin(R.nextFloat() * 6.28f, 0f);
    }

    // ---- trails ----

    /**
     * One frame of a trail shed at the feet ({@code x, y, z}) of a body moving at {@code mx, mz} blocks per second;
     * {@code moving} says whether it sheds at all now. Ribbon trails follow the feet; the others shed particles.
     */
    public static void trail(FxField f, Emitter e, FxStyles.Trail style, FxPalette chosen, double x, double y, double z,
                             double mx, double mz, float dt, float density, boolean moving) {
        if (e.trailStyle != style) {
            e.stopTrail();
            e.trailStyle = style;
        }
        if (style == FxStyles.Trail.OFF) {
            return;
        }
        FxPalette pal = chosen.or(signature(style));
        float ph = phase();
        if (style == FxStyles.Trail.RAINBOW) {
            if (!moving) {
                e.stopTrail();
                return;
            }
            // Six painted stripes stacked into a band, like a real rainbow drawn behind the feet.
            for (int k = 0; k < RAINBOW.length; k++) {
                if (e.stripes[k] == null) {
                    FxRibbon rb = f.ribbon();
                    rb.width = 0.12f;
                    rb.pointLife = 0.8f;
                    rb.color = RAINBOW[k];
                    rb.add = false;
                    e.stripes[k] = rb;
                }
                e.stripes[k].push(x, y + 0.12 + (RAINBOW.length - 1 - k) * 0.075, z);
            }
            e.sparkleDebt += dt * 10 * density;
            while (e.sparkleDebt >= 1) {
                e.sparkleDebt -= 1;
                f.spawn(FxSprite.TWINKLE).at(x + rnd(0.25), y + 0.1 + R.nextDouble() * 0.5, z + rnd(0.25)).vel(-mx * 0.1, 0.2, -mz * 0.1)
                        .life(between(0.5f, 0.8f)).size(between(0.07f, 0.11f), 0f).grow(Fx.Grow.POP)
                        .fade(0xFFFFFFFF).spin(R.nextFloat() * 6, 2f).core(0.3f);
            }
            return;
        }
        if (!moving) {
            e.trailDebt = 0;
            return;
        }
        e.trailDebt += dt * trailRate(style) * density;
        while (e.trailDebt >= 1f) {
            e.trailDebt -= 1f;
            shed(f, style, pal, ph, x, y, z, mx, mz);
        }
    }

    /** Particles per second a trail sheds at density 1. */
    public static float trailRate(FxStyles.Trail style) {
        return switch (style) {
            case SMOKE -> 12f;
            case PETALS, SNOW, NOTES, HEARTS -> 9f;
            case STARS, CRYSTALS -> 16f;
            case ELECTRIC -> 11f;
            case FLAME -> 34f;
            default -> 26f;
        };
    }

    private static void shed(FxField f, FxStyles.Trail style, FxPalette pal, float ph, double x, double y, double z, double mx, double mz) {
        double px = x + rnd(0.22);
        double py = y + 0.06 + R.nextDouble() * 0.2;
        double pz = z + rnd(0.22);
        double bx = -mx * 0.12;
        double bz = -mz * 0.12;
        switch (style) {
            case SPARKS -> {
                tint(f.spawn(FxSprite.SPARK).at(px, py, pz).vel(bx + rnd(0.9), between(1.2f, 2.8f), bz + rnd(0.9))
                        .gravity(10f).drag(0.5f).life(between(0.35f, 0.65f)).size(0.05f, 0.02f).stretch(0.07f).core(0.45f), pal, ph);
                if (R.nextInt(4) == 0) {
                    tint(f.spawn(FxSprite.EMBER).at(px, py + 0.1, pz).vel(rnd(0.2), between(0.3f, 0.7f), rnd(0.2))
                            .life(between(0.5f, 0.8f)).size(0.06f, 0f).flicker(0.6f), pal, ph);
                }
            }
            case STARS -> {
                FxSprite s = R.nextInt(5) == 0 ? FxSprite.STAR6 : R.nextBoolean() ? FxSprite.STAR4 : FxSprite.TWINKLE;
                f.spawn(s).at(px, py + 0.15, pz).vel(bx, between(0.2f, 0.6f), bz).life(between(0.7f, 1.2f))
                        .size(between(0.1f, s == FxSprite.STAR6 ? 0.2f : 0.15f), 0f).grow(Fx.Grow.POP)
                        .ramp(0xFFFFFFFF, pal.light(ph), pal.main(ph) & 0x00FFFFFF).spin(R.nextFloat() * 6, between(-3f, 3f)).core(0.3f);
            }
            case SOULS -> {
                FxPalette soul = pal;
                tint(f.spawn(FxSprite.SOUL).at(px, py, pz).vel(bx, between(0.5f, 0.9f), bz).sway(0.7f, 3f).fps(12)
                        .life(between(1.0f, 1.5f)).size(between(0.14f, 0.2f), 0.08f).core(0.4f), soul, ph);
                if (R.nextInt(3) == 0) {
                    f.spawn(FxSprite.SMOKE).at(px, py + 0.2, pz).vel(rnd(0.1), 0.4, rnd(0.1)).painted().spin(R.nextFloat() * 6, 0.5f)
                            .life(1.4f).size(0.16f, 0.4f).grow(Fx.Grow.EASE).fade(FxColor.alpha(FxColor.lerp(soul.main(ph), 0xFF101828, 0.6f), 0.35f));
                }
            }
            case FLAME -> {
                FxPalette fire = pal;
                int hot = fire == FxPalette.FIRE ? 0xFFFFF0B0 : FxColor.lighten(fire.light(ph), 0.4f);
                f.spawn(FxSprite.FLAME).at(px, py, pz).vel(bx + rnd(0.15), between(1.0f, 1.7f), bz + rnd(0.15)).fps(16)
                        .life(between(0.4f, 0.65f)).size(between(0.18f, 0.24f), 0.06f).sway(0.4f, 6f)
                        .ramp(hot, fire.main(ph), fire.main(ph) & 0x00FFFFFF).core(0.35f);
                if (R.nextInt(3) == 0) {
                    f.spawn(FxSprite.EMBER).at(px, py + 0.2, pz).vel(rnd(0.3), between(1.2f, 2.2f), rnd(0.3)).sway(0.8f, 5f)
                            .life(between(0.6f, 1.0f)).size(0.04f, 0f).flicker(0.5f).fade(fire == FxPalette.FIRE ? 0xFFFFC060 : fire.light(ph));
                }
                if (R.nextInt(6) == 0) {
                    f.spawn(FxSprite.SMOKE).at(px, py + 0.55, pz).vel(rnd(0.1), 0.5, rnd(0.1)).painted().spin(R.nextFloat() * 6, 0.6f)
                            .life(1.3f).size(0.2f, 0.5f).grow(Fx.Grow.EASE).fade(0x50282224);
                }
            }
            case PETALS -> {
                FxPalette p = pal;
                f.spawn(FxSprite.PETAL).at(px, py + 0.5, pz).vel(bx + rnd(0.3), between(-0.1f, 0.3f), bz + rnd(0.3)).painted()
                        .gravity(0.35f).drag(0.5f).sway(0.8f, 2.5f).tumble(0.8f).spin(R.nextFloat() * 6, between(-3f, 3f))
                        .life(between(1.8f, 2.6f)).size(0.09f, 0.08f).fadeIn(0.1f).fade(FxColor.alpha(p.pick(R, ph), 0.95f));
            }
            case SNOW -> {
                f.spawn(FxSprite.SNOWFLAKE).at(px, py + 0.6, pz).vel(bx + rnd(0.2), between(-0.35f, -0.15f), bz + rnd(0.2))
                        .sway(0.4f, 2f).spin(R.nextFloat() * 6, between(-1.5f, 1.5f)).life(between(1.8f, 2.4f))
                        .size(between(0.07f, 0.11f), 0.05f).fadeIn(0.1f)
                        .fade(FxColor.lighten(pal.light(ph), 0.5f));
                if (R.nextInt(3) == 0) {
                    f.spawn(FxSprite.DUST).at(px, py + 0.3, pz).vel(rnd(0.1), -0.1, rnd(0.1)).life(1f).size(0.035f, 0f).flicker(0.7f).fade(0xFFFFFFFF);
                }
            }
            case SMOKE -> {
                int base = FxColor.alpha(FxColor.lerp(pal.main(ph), 0xFF606068, 0.55f), 0.63f);
                f.spawn(FxSprite.SMOKE).at(px, py, pz).vel(bx + rnd(0.12), between(0.25f, 0.45f), bz + rnd(0.12)).painted()
                        .drag(0.55f).spin(R.nextFloat() * 6, between(-0.8f, 0.8f)).life(between(1.3f, 1.9f))
                        .size(0.14f, 0.55f).grow(Fx.Grow.EASE).fadeIn(0.12f).fade(base);
            }
            case CRYSTALS -> {
                FxPalette p = pal;
                f.spawn(FxSprite.SHARD).at(px, py, pz).vel(bx + rnd(0.6), between(1.2f, 2.0f), bz + rnd(0.6))
                        .gravity(6f).tumble(0.9f).spin(R.nextFloat() * 6, between(-7f, 7f)).life(between(0.8f, 1.1f))
                        .size(between(0.06f, 0.09f), 0.03f).fade(FxColor.alpha(p.light(ph), 0.85f));
                if (R.nextInt(2) == 0) {
                    f.spawn(FxSprite.TWINKLE).at(px, py + 0.2, pz).life(0.45f).size(0.12f, 0f).grow(Fx.Grow.POP)
                            .fade(0xFFFFFFFF).spin(R.nextFloat() * 6, 0f).core(0.4f);
                }
            }
            case ELECTRIC -> {
                FxPalette p = pal;
                double a = R.nextDouble() * Math.PI * 2;
                double r = between(0.4f, 0.9f);
                f.bolt().between(x + rnd(0.1), y + between(0.2f, 1.0f), z + rnd(0.1), x + Math.cos(a) * r, y + 0.02, z + Math.sin(a) * r)
                        .look(between(0.1f, 0.2f), 0.022f, 0.35f, p.light(ph));
                f.spawn(FxSprite.FLARE).at(x + Math.cos(a) * r, y + 0.05, z + Math.sin(a) * r).life(0.12f).size(0.18f, 0f)
                        .grow(Fx.Grow.POP).fade(p.light(ph));
            }
            case NOTES -> f.spawn(FxSprite.NOTE).at(px, py + 0.3, pz).vel(bx, between(0.5f, 0.8f), bz).sway(0.9f, 3f)
                    .spin(rnd(0.4) > 0 ? 0.2f : -0.2f, 0f).life(between(1.2f, 1.6f)).size(0.12f, 0.1f).grow(Fx.Grow.POP)
                    .fade(pal == FxPalette.RAINBOW ? FxColor.hsv(R.nextFloat(), 0.55f, 1f) : pal.pick(R, ph)).core(0.25f);
            case HEARTS -> {
                FxPalette p = pal;
                f.spawn(FxSprite.HEART).at(px, py + 0.3, pz).vel(bx, between(0.5f, 0.8f), bz).sway(0.5f, 2.5f)
                        .life(between(1.0f, 1.4f)).size(between(0.1f, 0.14f), 0.06f).grow(Fx.Grow.POP).fade(p.main(ph)).core(0.3f);
            }
            default -> {
            }
        }
    }

    // ---- auras ----

    /**
     * One frame of an aura around a body standing at {@code x, y, z} ({@code h} tall) at clock {@code t} seconds.
     * Ribbons and short-lived sprites are laid down every frame, so the aura follows smoothly.
     */
    public static void aura(FxField f, Emitter e, FxStyles.Aura style, FxPalette chosen, double x, double y, double z, float h,
                            float t, float dt, float scale) {
        if (e.auraStyle != style) {
            e.stopAura();
            e.auraStyle = style;
        }
        FxPalette pal = chosen.or(signature(style));
        float ph = phase();
        float keep = Math.max(0.03f, dt * 1.7f);
        switch (style) {
            case OFF -> {
            }
            case RIPPLE -> {
                e.beat += dt;
                if (e.beat >= 0.75f) {
                    e.beat = 0;
                    f.spawn(FxSprite.SHOCKWAVE).at(x, y + 0.03, z).flat().life(1.2f).size(0.15f * scale, 1.35f * scale)
                            .grow(Fx.Grow.EASE).ramp(pal.light(ph), pal.main(ph), pal.main(ph) & 0x00FFFFFF);
                    f.later(0.18f, () -> f.spawn(FxSprite.THIN_RING).at(x, y + 0.04, z).flat().life(0.9f).size(0.1f * scale, 0.95f * scale)
                            .grow(Fx.Grow.EASE).fade(pal.light(ph)));
                }
                if (R.nextFloat() < dt * 10) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = (0.5 + R.nextDouble() * 0.6) * scale;
                    f.spawn(FxSprite.DUST).at(x + Math.cos(a) * r, y + 0.05, z + Math.sin(a) * r).vel(0, between(0.4f, 0.9f), 0)
                            .life(1f).size(0.05f * scale, 0f).fade(pal.light(ph));
                }
            }
            case ORBIT -> {
                for (int k = 0; k < 3; k++) {
                    double a = t * 2.8 + k * Math.PI * 2 / 3;
                    double r = 0.72 * scale;
                    double oy = y + h * 0.55 + Math.sin(t * 2.2 + k * 2.1) * 0.3;
                    double px = x + Math.cos(a) * r;
                    double pz = z + Math.sin(a) * r;
                    if (e.aura[k] == null) {
                        e.aura[k] = f.ribbon();
                        e.aura[k].width = 0.2f * scale;
                        e.aura[k].pointLife = 0.55f;
                        e.aura[k].core = 0.35f;
                    }
                    int col = k == 1 ? pal.light(ph + 0.3f) : pal.main(ph + k * 0.33f);
                    e.aura[k].color = col;
                    e.aura[k].push(px, oy, pz);
                    f.spawn(FxSprite.GLOW).at(px, oy, pz).life(keep).size(0.17f * scale, 0.17f * scale).fade(col).core(0.45f).fadeIn(0f);
                    if (R.nextFloat() < dt * 9) {
                        f.spawn(FxSprite.DUST).at(px, oy, pz).vel(rnd(0.2), rnd(0.2), rnd(0.2)).life(0.6f).size(0.04f * scale, 0f)
                                .fade(pal.light(ph));
                    }
                }
            }
            case SPIRAL -> {
                for (int k = 0; k < 2; k++) {
                    double a = t * 3.4 + k * Math.PI;
                    float rise = (float) (((t * 0.55 + k * 0.5) % 1.0) * (h + 0.35));
                    if (rise < e.lastRise[k] && e.aura[k] != null) {
                        e.aura[k].stop();
                        e.aura[k] = null;
                    }
                    e.lastRise[k] = rise;
                    if (e.aura[k] == null) {
                        e.aura[k] = f.ribbon();
                        e.aura[k].width = 0.15f * scale;
                        e.aura[k].pointLife = 0.6f;
                        e.aura[k].core = 0.3f;
                    }
                    double r = 0.6 * scale;
                    double px = x + Math.cos(a) * r;
                    double pz = z + Math.sin(a) * r;
                    e.aura[k].color = pal.main(ph + k * 0.5f);
                    e.aura[k].push(px, y + rise, pz);
                    if (R.nextFloat() < dt * 8) {
                        f.spawn(FxSprite.TWINKLE).at(px, y + rise, pz).life(0.5f).size(0.1f * scale, 0f).grow(Fx.Grow.POP)
                                .fade(pal.light(ph)).spin(R.nextFloat() * 6, 2f);
                    }
                }
            }
            case HALO -> {
                f.spawn(FxSprite.HALO).at(x, y + h + 0.36, z).flat().life(keep).size(0.32f * scale, 0.32f * scale).fadeIn(0f)
                        .spin(t * 0.9f, 0f).fade(FxColor.alpha(pal.light(ph), 0.95f));
                f.spawn(FxSprite.GLOW).at(x, y + h + 0.36, z).life(keep).size(0.28f * scale, 0.28f * scale).fadeIn(0f)
                        .fade(FxColor.alpha(pal.main(ph), 0.25f));
                if (R.nextFloat() < dt * 5) {
                    f.spawn(FxSprite.TWINKLE).orbit(x, z, 0.32f * scale, R.nextFloat() * 6.28f, 1.5f).at(0, y + h + 0.36, 0)
                            .orbit(x, z, 0.32f * scale, R.nextFloat() * 6.28f, 1.5f).vel(0, 0.15, 0)
                            .life(0.8f).size(0.09f * scale, 0f).grow(Fx.Grow.POP).fade(0xFFFFFFFF).core(0.3f);
                }
            }
            case RUNES -> {
                float pulse = 0.7f + 0.3f * (float) Math.sin(t * 3);
                f.spawn(FxSprite.MAGIC_CIRCLE).at(x, y + 0.03, z).flat().life(keep).size(1.0f * scale, 1.0f * scale).fadeIn(0f)
                        .spin(t * 0.45f, 0f).fade(FxColor.alpha(pal.main(ph), 0.85f * pulse));
                f.spawn(FxSprite.MAGIC_CIRCLE).at(x, y + 0.035, z).flat().life(keep).size(0.55f * scale, 0.55f * scale).fadeIn(0f)
                        .spin(-t * 0.8f, 0f).fade(FxColor.alpha(pal.light(ph), 0.45f * pulse));
                if (R.nextFloat() < dt * 6f) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = 0.85 * scale;
                    f.spawn(FxSprite.RUNE).at(x + Math.cos(a) * r, y + 0.1, z + Math.sin(a) * r).vel(0, between(0.4f, 0.6f), 0)
                            .fps(0.0001f).life(1.5f).size(0.14f * scale, 0.09f * scale).fadeIn(0.25f)
                            .ramp(pal.light(ph), pal.main(ph), pal.main(ph) & 0x00FFFFFF).core(0.3f);
                }
            }
            case FIREFLIES -> {
                if (R.nextFloat() < dt * 12) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = (0.35 + R.nextDouble() * 0.7) * scale;
                    f.spawn(FxSprite.GLOW).at(x + Math.cos(a) * r, y + R.nextDouble() * h, z + Math.sin(a) * r)
                            .vel(rnd(0.15), rnd(0.1), rnd(0.15)).sway(0.5f, 1.5f).flicker(0.55f)
                            .life(between(1.6f, 2.4f)).size(0.06f * scale, 0.02f).fadeIn(0.2f).fade(pal.pick(R, ph)).core(0.6f);
                }
            }
            case FLAMES -> {
                FxPalette fire = pal;
                int hot = fire == FxPalette.FIRE ? 0xFFFFF0B0 : FxColor.lighten(fire.light(ph), 0.4f);
                int n = Math.max(1, Math.round(dt * 55));
                for (int i = 0; i < n; i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = 0.78 * scale;
                    f.spawn(FxSprite.FLAME).at(x + Math.cos(a) * r, y + 0.02, z + Math.sin(a) * r).vel(0, between(0.7f, 1.1f), 0)
                            .fps(16).life(between(0.4f, 0.6f)).size(0.17f * scale, 0.05f)
                            .ramp(hot, fire.main(ph), fire.main(ph) & 0x00FFFFFF).core(0.3f);
                }
                if (R.nextFloat() < dt * 8) {
                    f.spawn(FxSprite.EMBER).at(x + rnd(0.8 * scale), y + 0.3, z + rnd(0.8 * scale)).vel(rnd(0.2), between(1f, 2f), rnd(0.2))
                            .sway(0.7f, 4f).life(1f).size(0.04f, 0f).flicker(0.5f).fade(fire == FxPalette.FIRE ? 0xFFFFB050 : fire.light(ph));
                }
            }
        }
    }

    // ---- hits ----

    /**
     * A hit landing at {@code x, y, z} (on the victim's side facing the attacker, the ground under it at {@code groundY},
     * NaN when there is none); {@code dx, dz} points from the attacker to the victim. A critical hit is bigger and in
     * {@code critPal}. Each style has a shape and a motion of its own that read at a glance in a real fight: sparks
     * skitter on the ground, glass lands and glints, blood leaves splats, stars circle the head.
     */
    public static void hit(FxField f, FxStyles.Hit style, FxPalette pal, FxPalette critPal, boolean crit,
                           double x, double y, double z, double groundY, double dx, double dz, float scale) {
        if (style == FxStyles.Hit.OFF) {
            return;
        }
        float ph = phase();
        FxPalette base = pal.or(signature(style));
        FxPalette hot = critPal.or(critSignature(style));
        FxPalette p = crit ? hot : base;
        float s = scale * (crit ? 1.35f : 1f);
        double len = Math.sqrt(dx * dx + dz * dz);
        double nx = len < 1e-4 ? 1 : dx / len;
        double nz = len < 1e-4 ? 0 : dz / len;
        // Square to the swing, horizontally.
        double sx = -nz;
        double sz = nx;
        boolean ground = !Double.isNaN(groundY);
        double floor = ground ? groundY + 0.02 : Double.NaN;
        // The victim's middle, a little behind the hit point.
        double cx = x + nx * 0.35;
        double cz = z + nz * 0.35;
        switch (style) {
            case OFF -> {
            }
            case SPARKS -> {
                // Steel on steel: a spray of white-hot streaks thrown back at me that fall, bounce and skitter.
                for (int i = 0; i < count(crit ? 30 : 20, scale); i++) {
                    double[] d = sphere(between(2.5f, 5f));
                    double back = between(1.5f, 3.5f);
                    f.spawn(FxSprite.SPARK).at(x, y, z).vel(d[0] * 0.8 - nx * back, Math.abs(d[1]) * 0.9 + 1.4, d[2] * 0.8 - nz * back)
                            .gravity(14f).drag(0.6f).floor(floor, 0.35f).life(between(0.6f, 1.0f)).size(0.05f * s, 0.025f * s)
                            .stretch(0.075f).ramp(FxColor.lighten(p.light(ph), 0.7f), p.main(ph), p.main(ph) & 0x00FFFFFF).core(0.45f);
                }
                flash(f, x, y, z, 0.6f * s, FxColor.lighten(p.light(ph), 0.5f), 0.14f);
                for (int i = 0; i < 4; i++) {
                    f.spawn(FxSprite.EMBER).at(x + rnd(0.1), y + rnd(0.1), z + rnd(0.1)).vel(rnd(0.6) - nx * 0.5, between(0.2f, 0.7f), rnd(0.6) - nz * 0.5)
                            .gravity(1.5f).floor(floor, 0.2f).life(between(0.8f, 1.2f)).size(0.06f * s, 0.02f).flicker(0.5f).fade(p.light(ph));
                }
            }
            case FLASH -> {
                // A camera flash: a white-hot point, a long horizontal lens streak, rays and a ring of light.
                int white = FxColor.lighten(p.light(ph), 0.6f);
                flash(f, x, y, z, 1.0f * s, white, 0.24f);
                f.spawn(FxSprite.GLOW).at(x, y, z).aspect(8f).size(0.13f * s, 0.05f * s).grow(Fx.Grow.EASE).life(0.34f).fadeIn(0.02f)
                        .fade(white).core(0.4f);
                f.spawn(FxSprite.GLOW).at(x, y, z).aspect(0.12f).size(0.7f * s, 0.2f * s).grow(Fx.Grow.EASE).life(0.22f).fadeIn(0.02f)
                        .fade(FxColor.alpha(white, 0.7f));
                f.spawn(FxSprite.STARBURST).at(x, y, z).life(0.3f).size(0.7f * s, 1.3f * s).grow(Fx.Grow.EASE).spin(R.nextFloat() * 6, 1.2f)
                        .fade(FxColor.lighten(p.light(ph), 0.3f)).core(0.3f);
                f.spawn(FxSprite.RING).at(x, y, z).facing(-nx, 0, -nz).life(0.4f).size(0.2f * s, 1.4f * s).grow(Fx.Grow.EASE)
                        .ramp(white, p.main(ph), p.main(ph) & 0x00FFFFFF);
            }
            case SHARDS -> {
                // Breaking glass: shards fly out, fall, bounce once or twice and lie glinting on the ground.
                for (int i = 0; i < count(crit ? 18 : 13, scale); i++) {
                    double[] d = sphere(between(1.8f, 3.4f));
                    f.spawn(FxSprite.SHARD).at(x + rnd(0.08), y + rnd(0.08), z + rnd(0.08))
                            .vel(d[0] - nx * 1.6, Math.abs(d[1]) + 1.3, d[2] - nz * 1.6).gravity(12f).drag(0.7f).floor(floor, 0.3f)
                            .tumble(0.9f).spin(R.nextFloat() * 6, between(-12f, 12f)).life(between(1.3f, 1.8f))
                            .size(between(0.09f, 0.15f) * s, 0.06f * s).fadeIn(0.02f)
                            .ramp(FxColor.lighten(p.light(ph), 0.6f), p.light(ph), p.main(ph) & 0x00FFFFFF);
                }
                double gy = ground ? groundY + 0.12 : y - 0.6;
                for (int i = 0; i < 7; i++) {
                    f.later(between(0.25f, 1.1f), () -> f.spawn(FxSprite.TWINKLE).at(cx + rnd(0.8), gy + R.nextDouble() * 0.1, cz + rnd(0.8))
                            .life(0.3f).size(0.14f * s, 0f).grow(Fx.Grow.POP).fade(0xFFFFFFFF).spin(R.nextFloat() * 6, 0f).core(0.4f));
                }
                f.spawn(FxSprite.THIN_RING).at(x, y, z).facing(-nx, 0, -nz).life(0.25f).size(0.1f * s, 0.7f * s).grow(Fx.Grow.EASE)
                        .fade(p.light(ph));
                flash(f, x, y, z, 0.4f * s, FxColor.lighten(p.light(ph), 0.5f), 0.1f);
            }
            case IMPACT -> {
                // A blow that shakes the ground: a ring of force where it lands, cracks and a ring of dust at the feet.
                f.spawn(FxSprite.SHOCKWAVE).at(x, y, z).facing(-nx, 0, -nz).life(0.4f).size(0.15f * s, 1.3f * s)
                        .grow(Fx.Grow.EASE).ramp(p.light(ph), p.main(ph), p.main(ph) & 0x00FFFFFF);
                f.later(0.05f, () -> f.spawn(FxSprite.THIN_RING).at(x, y, z).facing(-nx, 0, -nz).life(0.32f).size(0.1f * s, 0.9f * s)
                        .grow(Fx.Grow.EASE).fade(p.light(ph)));
                if (ground) {
                    f.spawn(FxSprite.SHOCKWAVE).at(cx, groundY + 0.03, cz).flat().life(0.55f).size(0.2f * s, 1.7f * s).grow(Fx.Grow.EASE)
                            .fade(FxColor.alpha(p.main(ph), 0.85f));
                    f.spawn(FxSprite.CRACK).at(cx, groundY + 0.015, cz).flat().painted().spin(R.nextFloat() * 6, 0f).life(1.6f)
                            .size(0.55f * s, 0.6f * s).fadeIn(0.01f).ramp(0xB0202020, 0xB0202020, 0x00202020);
                    for (int i = 0; i < 12; i++) {
                        double a = i * Math.PI * 2 / 12 + rnd(0.2);
                        f.spawn(FxSprite.SMOKE).at(cx + Math.cos(a) * 0.3, groundY + 0.1, cz + Math.sin(a) * 0.3)
                                .vel(Math.cos(a) * 2.2, 0.35, Math.sin(a) * 2.2).drag(0.15f).painted().spin(R.nextFloat() * 6, 0.8f)
                                .life(between(0.7f, 1.0f)).size(0.1f * s, 0.32f * s).grow(Fx.Grow.EASE).fadeIn(0.05f).fade(0x707A7468);
                    }
                }
                for (int i = 0; i < count(10, s); i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double ca = Math.cos(a);
                    double sa = Math.sin(a);
                    f.spawn(FxSprite.SPARK).at(x, y, z).vel(sx * ca * 4, sa * 4, sz * ca * 4).drag(0.1f).life(0.28f).size(0.04f * s, 0f)
                            .stretch(0.05f).fade(p.light(ph));
                }
                flash(f, x, y, z, 0.55f * s, p.light(ph), 0.12f);
            }
            case BLOOD -> {
                // A burst of red at the wound, a spray of drops that arc, fall and leave splats where they land.
                f.spawn(FxSprite.SPLAT).at(x - nx * 0.04, y, z - nz * 0.04).facing(-nx, 0, -nz).painted().spin(R.nextFloat() * 6, 0f)
                        .life(0.3f).size(0.18f * s, 0.55f * s).grow(Fx.Grow.EASE).fadeIn(0.01f).ramp(0xF0D0101C, 0xD0A00818, 0x00800010);
                for (int i = 0; i < count(crit ? 24 : 18, scale); i++) {
                    double[] d = sphere(between(1.6f, 3.2f));
                    float size = between(0.07f, 0.11f) * s;
                    int c = FxColor.lerp(0xFFE01828, 0xFF900012, R.nextFloat());
                    f.spawn(FxSprite.DROP).at(x, y, z).vel(d[0] - nx * 1.6, Math.abs(d[1]) * 0.8 + 1.4, d[2] - nz * 1.6).painted()
                            .gravity(13f).stretch(0.05f).aspect(0.6f).life(ground ? 2.5f : between(0.6f, 0.9f)).size(size, size)
                            .floor(floor, 0f).splat(FxColor.alpha(FxColor.lerp(c, 0xFFB00818, 0.3f), 0.95f)).fade(c);
                }
                for (int i = 0; i < 3; i++) {
                    f.spawn(FxSprite.SMOKE).at(x, y, z).painted().vel(-nx * 0.5 + rnd(0.2), 0.2, -nz * 0.5 + rnd(0.2))
                            .spin(R.nextFloat() * 6, 0.5f).life(0.7f).size(0.14f * s, 0.5f * s).grow(Fx.Grow.EASE).fade(0x80A01020);
                }
                if (ground && crit) {
                    f.spawn(FxSprite.SPLAT).at(cx, groundY + 0.014, cz).flat().painted().spin(R.nextFloat() * 6, 0f)
                            .life(3f).size(0.2f * s, 0.5f * s).grow(Fx.Grow.EASE).fadeIn(0.02f).ramp(0xE8A00818, 0xE8A00818, 0x00800010);
                }
            }
            case STARS -> {
                // Seeing stars: fat cartoon stars circle the head for a second and a half.
                double headY = y + 0.7;
                int n = crit ? 6 : 5;
                for (int i = 0; i < n; i++) {
                    int col = i % 2 == 0 ? p.main(ph) : p.light(ph);
                    float a0 = i * 6.28f / n;
                    f.spawn(FxSprite.TOON_STAR).orbit(cx, cz, 0.42f * s, a0, 4.2f).at(0, headY + (i % 2) * 0.06, 0)
                            .orbit(cx, cz, 0.42f * s, a0, 4.2f).vel(0, 0.05, 0).painted().spin(0f, 1.5f)
                            .life(1.6f).size(0.17f * s, 0.15f * s).grow(Fx.Grow.POP).fadeIn(0.02f).ramp(col, col, col & 0x00FFFFFF);
                    f.spawn(FxSprite.GLOW).orbit(cx, cz, 0.42f * s, a0, 4.2f).at(0, headY + (i % 2) * 0.06, 0)
                            .orbit(cx, cz, 0.42f * s, a0, 4.2f).vel(0, 0.05, 0).life(1.6f).size(0.16f * s, 0.12f * s)
                            .fade(FxColor.alpha(p.light(ph), 0.5f));
                }
                f.spawn(FxSprite.STARBURST).at(x, y, z).life(0.18f).size(0.35f * s, 0.6f * s).grow(Fx.Grow.EASE)
                        .spin(R.nextFloat() * 6, 0f).fade(p.light(ph));
                for (int i = 0; i < 3; i++) {
                    f.spawn(FxSprite.TWINKLE).at(x + rnd(0.25), y + rnd(0.25), z + rnd(0.25)).life(0.35f).size(0.14f * s, 0f)
                            .grow(Fx.Grow.POP).fade(0xFFFFFFFF).spin(R.nextFloat() * 6, 0f);
                }
            }
            case ELECTRIC -> {
                // A shock: arcs jump from the hit into the ground and crawl over the body, crackling for a moment.
                double gy = ground ? groundY + 0.02 : y - 1.0;
                for (int i = 0; i < (crit ? 4 : 3); i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double r = between(0.3f, 0.75f);
                    f.bolt().between(x, y, z, cx + Math.cos(a) * r, gy, cz + Math.sin(a) * r)
                            .look(between(0.35f, 0.5f), 0.032f * s, 0.32f, p.light(ph));
                    f.spawn(FxSprite.FLARE).at(cx + Math.cos(a) * r, gy + 0.05, cz + Math.sin(a) * r).life(0.25f).size(0.3f * s, 0f)
                            .grow(Fx.Grow.POP).fade(p.light(ph));
                }
                for (int i = 0; i < 3; i++) {
                    double[] d = sphere(between(0.45f, 0.8f));
                    f.bolt().between(x, y, z, cx + d[0] * 0.6, y + d[1] * 0.9, cz + d[2] * 0.6)
                            .look(between(0.25f, 0.35f), 0.026f * s, 0.4f, p.light(ph));
                }
                f.later(0.18f, () -> {
                    for (int i = 0; i < 2; i++) {
                        double[] d = sphere(between(0.4f, 0.7f));
                        f.bolt().between(cx + d[0] * 0.3, y + d[1] * 0.5, cz + d[2] * 0.3, cx - d[0] * 0.4, y - d[1] * 0.6, cz - d[2] * 0.4)
                                .look(0.2f, 0.022f * s, 0.45f, p.light(ph));
                    }
                });
                flash(f, x, y, z, 0.75f * s, p.light(ph), 0.16f);
                for (int i = 0; i < 10; i++) {
                    double[] d = sphere(between(3f, 5.5f));
                    f.spawn(FxSprite.SPARK).at(x, y, z).vel(d[0], d[1] + 1, d[2]).gravity(9f).drag(0.3f).floor(floor, 0.4f)
                            .life(between(0.3f, 0.55f)).size(0.03f * s, 0f).stretch(0.05f).fade(0xFFFFFFFF);
                }
            }
            case SLASH -> {
                // A blade's cut: a wide crescent across the target, a second one crossing it on a crit.
                float angle = (R.nextBoolean() ? 1 : -1) * between(0.5f, 0.9f);
                f.spawn(FxSprite.SLASH).at(x - nx * 0.05, y + 0.05, z - nz * 0.05).facing(-nx, 0, -nz).spin(angle, angle > 0 ? -2.2f : 2.2f)
                        .life(0.32f).size(0.65f * s, 1.15f * s).grow(Fx.Grow.EASE)
                        .ramp(FxColor.lighten(p.light(ph), 0.6f), p.main(ph), p.main(ph) & 0x00FFFFFF).core(0.6f);
                if (crit) {
                    f.later(0.06f, () -> f.spawn(FxSprite.SLASH).at(x - nx * 0.06, y + 0.05, z - nz * 0.06).facing(-nx, 0, -nz)
                            .spin(-angle, angle > 0 ? 2.2f : -2.2f).life(0.3f).size(0.6f * s, 1.05f * s).grow(Fx.Grow.EASE)
                            .ramp(FxColor.lighten(hot.light(ph), 0.5f), hot.main(ph), hot.main(ph) & 0x00FFFFFF).core(0.6f));
                }
                double ux = Math.cos(angle);
                for (int i = 0; i < count(9, s); i++) {
                    double k = between(-1f, 1f);
                    f.spawn(FxSprite.SPARK).at(x + sx * k * 0.45, y + k * 0.35 * ux, z + sz * k * 0.45)
                            .vel(sx * k * 3 - nx * 1.5, ux * k * 2 + 1, sz * k * 3 - nz * 1.5).gravity(8f).drag(0.3f).floor(floor, 0.3f)
                            .life(between(0.3f, 0.5f)).size(0.035f * s, 0f).stretch(0.06f).fade(p.light(ph));
                }
            }
        }
        if (crit && style != FxStyles.Hit.STARS) {
            f.spawn(FxSprite.STARBURST).at(x, y, z).life(0.22f).size(0.6f * s, 0.25f).grow(Fx.Grow.POP).spin(R.nextFloat() * 6, 0f)
                    .fade(hot.light(ph)).core(0.3f);
        }
    }

    // ---- kills ----

    /** A kill burst over a body that stood at {@code x, y, z}. */
    public static void kill(FxField f, FxStyles.Kill style, FxPalette chosen, double x, double y, double z, float scale) {
        FxPalette pal = chosen.or(signature(style));
        float ph = phase();
        double cy = y + 1.0;
        switch (style) {
            case SUPERNOVA -> {
                flash(f, x, cy, z, 2.4f * scale, pal.light(ph), 0.4f);
                f.spawn(FxSprite.STARBURST).at(x, cy, z).life(0.35f).size(1.6f * scale, 2.4f * scale).spin(R.nextFloat() * 6, 0.5f)
                        .fade(FxColor.lighten(pal.light(ph), 0.3f)).core(0.3f);
                f.spawn(FxSprite.SHOCKWAVE).at(x, y + 0.05, z).flat().life(0.9f).size(0.3f * scale, 4.2f * scale).grow(Fx.Grow.EASE)
                        .ramp(pal.light(ph), pal.main(ph), pal.main(ph) & 0x00FFFFFF);
                f.spawn(FxSprite.SHOCKWAVE).at(x, cy, z).life(0.5f).size(0.3f * scale, 2.8f * scale).grow(Fx.Grow.EASE).fade(pal.main(ph));
                for (int i = 0; i < count(40, scale); i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    double sp = between(5f, 8f);
                    tint(f.spawn(FxSprite.SPARK).at(x, cy, z).vel(Math.cos(a) * sp, between(-0.5f, 3f), Math.sin(a) * sp)
                            .drag(0.15f).gravity(2.5f).life(between(0.7f, 1.0f)).size(0.05f * scale, 0f).stretch(0.08f).core(0.4f), pal, ph);
                }
                for (int i = 0; i < 14; i++) {
                    double a = i * Math.PI * 2 / 14;
                    f.spawn(FxSprite.SMOKE).at(x, y + 0.2, z).vel(Math.cos(a) * 2.2, 0.3, Math.sin(a) * 2.2).painted().drag(0.3f)
                            .spin(R.nextFloat() * 6, 0.5f).life(1.4f).size(0.3f * scale, 0.9f * scale).grow(Fx.Grow.EASE).fadeIn(0.05f)
                            .fade(FxColor.alpha(FxColor.lerp(pal.main(ph), 0xFF404050, 0.6f), 0.45f));
                }
            }
            case CONFETTI -> {
                for (int i = 0; i < count(80, scale); i++) {
                    double[] d = sphere(2.2);
                    f.spawn(FxSprite.CONFETTI).at(x + rnd(0.3), cy, z + rnd(0.3)).vel(d[0], between(4.5f, 8f), d[2]).painted()
                            .gravity(7f).drag(0.35f).sway(0.6f, 4f).tumble(0.95f).spin(R.nextFloat() * 6, between(-11f, 11f))
                            .aspect(0.7f).life(between(2.2f, 3.0f)).size(0.065f * scale, 0.06f * scale)
                            .fade(FxColor.hsv(R.nextFloat(), 0.75f, 1f));
                }
                for (int k = 0; k < 4; k++) {
                    FxRibbon rb = f.ribbon();
                    rb.width = 0.09f * scale;
                    rb.pointLife = 0.6f;
                    rb.color = FxColor.hsv(k / 4f + R.nextFloat() * 0.1f, 0.75f, 1f);
                    rb.stopAt = 2.2f;
                    double a0 = k * Math.PI / 2;
                    rb.mover = (age, out) -> {
                        double rad = 0.4 + age * 0.5;
                        double h = age < 0.4 ? age / 0.4 * 2.6 : 2.6 - (age - 0.4) * 1.3;
                        out[0] = x + Math.cos(a0 + age * 5) * rad;
                        out[1] = y + 0.3 + Math.max(0, h);
                        out[2] = z + Math.sin(a0 + age * 5) * rad;
                    };
                }
                flash(f, x, cy, z, 0.9f * scale, 0xFFFFFFFF, 0.2f);
                f.later(0.2f, () -> {
                    for (int i = 0; i < 6; i++) {
                        double[] d = sphere(2.5);
                        f.spawn(FxSprite.STAR4).at(x, y + 2.6, z).vel(d[0], d[1], d[2]).drag(0.2f).life(0.7f)
                                .size(0.2f * scale, 0f).grow(Fx.Grow.POP).fade(FxColor.hsv(R.nextFloat(), 0.5f, 1f)).spin(0, 4f).core(0.3f);
                    }
                });
            }
            case SHATTER -> {
                flash(f, x, cy, z, 1.3f * scale, 0xFFFFFFFF, 0.2f);
                f.spawn(FxSprite.THIN_RING).at(x, cy, z).life(0.3f).size(0.3f * scale, 1.9f * scale).grow(Fx.Grow.EASE).fade(0xFFE6F6FF);
                for (int i = 0; i < count(50, scale); i++) {
                    double[] d = sphere(between(2.5f, 5f));
                    int c = FxColor.alpha(FxColor.lerp(pal.light(ph), 0xFFFFFFFF, 0.45f), 0.8f);
                    f.spawn(FxSprite.SHARD).at(x + rnd(0.3), y + R.nextDouble() * 1.8, z + rnd(0.3)).vel(d[0], d[1] + 2, d[2])
                            .gravity(11f).tumble(0.9f).spin(R.nextFloat() * 6, between(-10f, 10f)).life(between(1.1f, 1.6f))
                            .size(between(0.08f, 0.16f) * scale, 0.05f).fade(c);
                }
                for (int i = 0; i < 12; i++) {
                    f.later(between(0f, 0.5f), () -> f.spawn(FxSprite.TWINKLE).at(x + rnd(1.0), y + R.nextDouble() * 2, z + rnd(1.0))
                            .life(0.35f).size(0.16f * scale, 0f).grow(Fx.Grow.POP).fade(0xFFFFFFFF).spin(R.nextFloat() * 6, 0f).core(0.4f));
                }
            }
            case SOUL_RISE -> {
                FxPalette soul = pal;
                f.spawn(FxSprite.BEAM).at(x, y + 5.5, z).vel(0, 0.001, 0).stretch(5500f).life(1.4f).size(0.45f * scale, 0.15f * scale)
                        .ramp(soul.light(ph), soul.main(ph), soul.main(ph) & 0x00FFFFFF).core(0.35f);
                f.spawn(FxSprite.MAGIC_CIRCLE).at(x, y + 0.04, z).flat().life(1.5f).size(1.5f * scale, 1.2f * scale).spin(0, 1.2f)
                        .ramp(soul.light(ph), soul.main(ph), soul.main(ph) & 0x00FFFFFF);
                for (int k = 0; k < 10; k++) {
                    f.later(k * 0.07f, () -> {
                        for (int i = 0; i < 3; i++) {
                            tint(f.spawn(FxSprite.SOUL).orbit(x, z, 0.55f * scale, R.nextFloat() * 6.28f, 3f).at(0, y + 0.2, 0)
                                    .orbit(x, z, 0.55f * scale, R.nextFloat() * 6.28f, 3f).vel(0, between(2f, 3.2f), 0).drag(0.7f).fps(12)
                                    .life(between(1.2f, 1.6f)).size(0.2f * scale, 0.08f).core(0.4f), soul, ph);
                        }
                    });
                }
                for (int i = 0; i < 6; i++) {
                    double a = i * Math.PI / 3;
                    f.spawn(FxSprite.RUNE).at(x + Math.cos(a) * 0.9, y + 0.2, z + Math.sin(a) * 0.9).vel(0, 1.2, 0).fps(0.0001f)
                            .life(1.4f).size(0.14f * scale, 0.08f).fadeIn(0.2f).fade(soul.light(ph)).core(0.3f);
                }
            }
            case BLOOD_BURST -> {
                f.spawn(FxSprite.SPLAT).at(x, y + 0.02, z).flat().painted().spin(R.nextFloat() * 6, 0f).life(3f)
                        .size(0.9f * scale, 1.1f * scale).fadeIn(0.03f).color(0xE0700008, 0x00500006);
                for (int i = 0; i < count(60, scale); i++) {
                    double[] d = sphere(between(2.5f, 5f));
                    f.spawn(FxSprite.DROP).at(x + rnd(0.3), y + 0.4 + R.nextDouble() * 1.4, z + rnd(0.3)).vel(d[0], d[1] + 2, d[2]).painted()
                            .gravity(13f).stretch(0.05f).aspect(0.6f).life(between(0.9f, 1.3f)).size(between(0.06f, 0.1f) * scale, 0.05f)
                            .fade(FxColor.lerp(0xFFB0101C, 0xFF5A0008, R.nextFloat()));
                }
                for (int i = 0; i < 10; i++) {
                    double[] d = sphere(1.2);
                    f.spawn(FxSprite.SMOKE).at(x, cy, z).vel(d[0], d[1] * 0.5 + 0.3, d[2]).painted().drag(0.4f).spin(R.nextFloat() * 6, 0.4f)
                            .life(1.2f).size(0.3f * scale, 0.8f * scale).grow(Fx.Grow.EASE).fade(0x707A0A14);
                }
                flash(f, x, cy, z, 0.9f * scale, 0xFFFF3040, 0.2f);
            }
            case VORTEX -> {
                for (int k = 0; k < 4; k++) {
                    FxRibbon rb = f.ribbon();
                    rb.width = 0.16f * scale;
                    rb.pointLife = 0.3f;
                    rb.color = pal.main(ph + k * 0.25f);
                    rb.stopAt = 0.5f;
                    double a0 = k * Math.PI / 2;
                    rb.mover = (age, out) -> {
                        double t = Math.min(1, age / 0.5);
                        double rad = 1.9 * scale * (1 - t);
                        out[0] = x + Math.cos(a0 + t * 9) * rad;
                        out[1] = y + 0.2 + t * 0.9;
                        out[2] = z + Math.sin(a0 + t * 9) * rad;
                    };
                }
                f.spawn(FxSprite.MAGIC_CIRCLE).at(x, y + 0.04, z).flat().life(0.6f).size(1.6f * scale, 0.2f).spin(0, 6f).fade(pal.main(ph));
                f.later(0.5f, () -> {
                    flash(f, x, cy, z, 1.9f * scale, pal.light(ph), 0.3f);
                    f.spawn(FxSprite.SHOCKWAVE).at(x, cy, z).life(0.45f).size(0.3f * scale, 2.6f * scale).grow(Fx.Grow.EASE).fade(pal.main(ph));
                    f.spawn(FxSprite.SHOCKWAVE).at(x, y + 0.05, z).flat().life(0.7f).size(0.3f * scale, 3f * scale).grow(Fx.Grow.EASE).fade(pal.light(ph));
                    for (int i = 0; i < count(32, scale); i++) {
                        double[] d = sphere(between(4f, 7f));
                        tint(f.spawn(FxSprite.SPARK).at(x, cy, z).vel(d[0], d[1], d[2]).drag(0.2f).life(0.6f).size(0.05f * scale, 0f)
                                .stretch(0.08f).core(0.4f), pal, ph);
                    }
                });
            }
            case BLOOM -> {
                FxRibbon rocket = f.ribbon();
                rocket.width = 0.07f * scale;
                rocket.pointLife = 0.3f;
                rocket.color = pal.light(ph);
                rocket.stopAt = 0.35f;
                rocket.mover = (age, out) -> {
                    out[0] = x + Math.sin(age * 20) * 0.03;
                    out[1] = y + 0.2 + age / 0.35 * 2.6;
                    out[2] = z;
                };
                f.later(0.35f, () -> {
                    double by = y + 2.8;
                    flash(f, x, by, z, 1.3f * scale, pal.light(ph), 0.3f);
                    for (int i = 0; i < count(56, scale); i++) {
                        double[] d = sphere(between(4f, 6f));
                        f.spawn(FxSprite.SPARK).at(x, by, z).vel(d[0], d[1], d[2]).drag(0.25f).gravity(2f).life(between(1.1f, 1.6f))
                                .size(0.045f * scale, 0f).stretch(0.12f).core(0.4f).fade(pal.pick(R, ph + R.nextFloat() * 0.5f));
                    }
                    for (int i = 0; i < 14; i++) {
                        f.later(between(0.5f, 1.0f), () -> {
                            double[] d = sphere(between(1.5f, 2.6f));
                            f.spawn(FxSprite.TWINKLE).at(x + d[0], by + d[1] - 0.6, z + d[2]).life(0.25f).size(0.16f * scale, 0f)
                                    .grow(Fx.Grow.POP).fade(0xFFFFFFFF).spin(R.nextFloat() * 6, 0f).core(0.5f);
                        });
                    }
                });
            }
            case PILLAR -> {
                f.spawn(FxSprite.BEAM).at(x, y + 8, z).vel(0, 0.001, 0).stretch(8000f).life(1.1f).size(0.6f * scale, 0.2f * scale)
                        .ramp(FxColor.lighten(pal.light(ph), 0.4f), pal.main(ph), pal.main(ph) & 0x00FFFFFF).core(0.3f);
                f.spawn(FxSprite.SHOCKWAVE).at(x, y + 0.05, z).flat().life(0.8f).size(0.3f * scale, 2.3f * scale).grow(Fx.Grow.EASE).fade(pal.main(ph));
                f.spawn(FxSprite.MAGIC_CIRCLE).at(x, y + 0.04, z).flat().life(1.1f).size(1.3f * scale, 1.0f * scale).spin(0, 2f).fade(pal.light(ph));
                for (int i = 0; i < count(26, scale); i++) {
                    f.spawn(FxSprite.DUST).at(x + rnd(0.35), y + R.nextDouble() * 1.5, z + rnd(0.35)).vel(rnd(0.3), between(4f, 8f), rnd(0.3))
                            .drag(0.4f).life(between(0.6f, 1.0f)).size(0.07f * scale, 0f).fade(pal.light(ph));
                }
            }
        }
    }

    // ---- totems ----

    /** A totem saving someone standing at {@code x, y, z} facing {@code yaw}. */
    public static void totem(FxField f, FxStyles.Totem style, double x, double y, double z, float yaw, float scale) {
        float ph = phase();
        switch (style) {
            case VANILLA -> {
            }
            case GOLDEN, EMERALD -> {
                FxPalette pal = style == FxStyles.Totem.EMERALD ? FxPalette.EMERALD : FxPalette.GOLD;
                flash(f, x, y + 1.1, z, 1.5f * scale, pal.light(ph), 0.32f);
                f.spawn(FxSprite.MAGIC_CIRCLE).at(x, y + 0.04, z).flat().life(1.4f).size(1.5f * scale, 1.3f * scale).spin(0, 1.5f)
                        .ramp(pal.light(ph), pal.main(ph), pal.main(ph) & 0x00FFFFFF);
                for (int k = 0; k < 3; k++) {
                    f.later(k * 0.12f, () -> f.spawn(FxSprite.RING).at(x, y + 0.1, z).flat().vel(0, 1.6, 0).life(0.9f)
                            .size(0.5f * scale, 1.15f * scale).grow(Fx.Grow.EASE).fade(pal.light(ph)));
                }
                if (style == FxStyles.Totem.GOLDEN) {
                    for (int i = 0; i < count(22, scale); i++) {
                        double[] d = sphere(1.8);
                        f.spawn(FxSprite.FEATHER).at(x, y + 1.4, z).vel(d[0], Math.abs(d[1]) + 1.2, d[2]).gravity(1.1f).drag(0.5f)
                                .sway(0.8f, 2f).tumble(0.5f).spin(R.nextFloat() * 6, between(-2f, 2f)).life(between(1.8f, 2.6f))
                                .size(0.16f * scale, 0.12f).fadeIn(0.05f)
                                .ramp(0xFFFFF6D0, FxColor.lerp(0xFFFFE08A, 0xFFF0B030, R.nextFloat()), 0x00E09020);
                    }
                } else {
                    for (int i = 0; i < count(18, scale); i++) {
                        double[] d = sphere(2.6);
                        f.spawn(FxSprite.SHARD).at(x, y + 1.1, z).vel(d[0], d[1] + 2, d[2]).gravity(8f).tumble(0.9f)
                                .spin(R.nextFloat() * 6, 8f).life(1.1f).size(0.09f * scale, 0.04f).fade(FxColor.alpha(pal.light(ph), 0.9f));
                    }
                    for (int i = 0; i < 6; i++) {
                        double a = i * Math.PI / 3;
                        f.spawn(FxSprite.RUNE).at(x + Math.cos(a) * 0.9, y + 0.3, z + Math.sin(a) * 0.9).vel(0, 1.0, 0).fps(0.0001f)
                                .life(1.3f).size(0.13f * scale, 0.08f).fadeIn(0.2f).fade(pal.light(ph)).core(0.3f);
                    }
                }
                for (int i = 0; i < 12; i++) {
                    f.later(between(0f, 0.6f), () -> f.spawn(FxSprite.STAR4).at(x + rnd(0.9), y + 0.3 + R.nextDouble() * 1.8, z + rnd(0.9))
                            .life(0.5f).size(0.16f * scale, 0f).grow(Fx.Grow.POP).fade(pal.light(ph)).spin(R.nextFloat() * 6, 3f).core(0.4f));
                }
                for (int i = 0; i < count(26, scale); i++) {
                    double a = R.nextDouble() * Math.PI * 2;
                    tint(f.spawn(FxSprite.SPARK).at(x + Math.cos(a) * 0.5, y + 0.2, z + Math.sin(a) * 0.5)
                            .vel(Math.cos(a) * 1.2, between(3f, 6f), Math.sin(a) * 1.2).gravity(3f).drag(0.45f).life(between(0.8f, 1.1f))
                            .size(0.045f * scale, 0f).stretch(0.07f).core(0.4f), pal, ph);
                }
            }
            case SOUL -> {
                FxPalette soul = FxPalette.SOUL;
                f.spawn(FxSprite.BEAM).at(x, y + 4.5, z).vel(0, 0.001, 0).stretch(4500f).life(1.2f).size(0.4f * scale, 0.1f)
                        .ramp(soul.light(ph), soul.main(ph), soul.main(ph) & 0x00FFFFFF).core(0.3f);
                for (int i = 0; i < 24; i++) {
                    float a = i * 6.28f / 24;
                    f.later(i * 0.02f, () -> tint(f.spawn(FxSprite.SOUL).orbit(x, z, 0.65f * scale, a, 3.5f).at(0, y + 0.2, 0)
                            .orbit(x, z, 0.65f * scale, a, 3.5f).vel(0, 2.2, 0).drag(0.6f).fps(12).life(1.2f)
                            .size(0.18f * scale, 0.06f).core(0.35f), soul, ph));
                }
                f.spawn(FxSprite.THIN_RING).at(x, y + 0.05, z).flat().life(1f).size(0.3f * scale, 1.8f * scale).grow(Fx.Grow.EASE).fade(soul.light(ph));
            }
            case RAINBOW -> {
                for (int k = 0; k < 2; k++) {
                    FxRibbon rb = f.ribbon();
                    rb.width = 0.14f * scale;
                    rb.pointLife = 0.7f;
                    rb.rainbow = true;
                    rb.stopAt = 1.0f;
                    double a0 = k * Math.PI;
                    rb.mover = (age, out) -> {
                        out[0] = x + Math.cos(a0 + age * 9) * 0.7 * scale;
                        out[1] = y + age * 2.4;
                        out[2] = z + Math.sin(a0 + age * 9) * 0.7 * scale;
                    };
                }
                for (int i = 0; i < count(36, scale); i++) {
                    double[] d = sphere(2.2);
                    f.spawn(FxSprite.CONFETTI).at(x, y + 1.6, z).vel(d[0], Math.abs(d[1]) + 2, d[2]).painted().gravity(6f).drag(0.4f)
                            .sway(0.5f, 4f).tumble(0.95f).spin(R.nextFloat() * 6, 9f).aspect(0.7f).life(2f).size(0.06f * scale, 0.05f)
                            .fade(FxColor.hsv(R.nextFloat(), 0.75f, 1f));
                }
                flash(f, x, y + 1.1, z, 1.2f * scale, FxColor.hsv(ph, 0.4f, 1f), 0.3f);
            }
            case NOVA -> {
                flash(f, x, y + 1, z, 2.6f * scale, 0xFFFFFFFF, 0.32f);
                f.spawn(FxSprite.STARBURST).at(x, y + 1, z).life(0.35f).size(1.8f * scale, 2.6f * scale).spin(R.nextFloat() * 6, 0.4f)
                        .fade(0xFFE6F2FF).core(0.3f);
                f.spawn(FxSprite.SHOCKWAVE).at(x, y + 1, z).life(0.5f).size(0.3f * scale, 2.6f * scale).grow(Fx.Grow.EASE).fade(0xFFCFE6FF);
                f.spawn(FxSprite.SHOCKWAVE).at(x, y + 0.05, z).flat().life(0.8f).size(0.3f * scale, 3f * scale).grow(Fx.Grow.EASE).fade(0xFFB8DAFF);
                for (int i = 0; i < count(44, scale); i++) {
                    double[] d = sphere(between(4f, 7f));
                    f.spawn(FxSprite.SPARK).at(x, y + 1, z).vel(d[0], d[1], d[2]).drag(0.2f).life(0.6f).size(0.045f * scale, 0f)
                            .stretch(0.08f).core(0.5f).fade(FxPalette.ICE.pick(R, ph));
                }
            }
            case PHOENIX -> {
                double[] fwd = {-Math.sin(Math.toRadians(yaw)), Math.cos(Math.toRadians(yaw))};
                double sideX = -fwd[1];
                double sideZ = fwd[0];
                for (int side = -1; side <= 1; side += 2) {
                    for (int k = 0; k < 16; k++) {
                        double t = k / 15.0;
                        double out = (0.25 + t * 1.3) * side;
                        double up = Math.sin(t * Math.PI) * 0.7 + t * 0.35;
                        double wx = x + sideX * out - fwd[0] * (0.25 + t * 0.2);
                        double wy = y + 1.25 + up;
                        double wz = z + sideZ * out - fwd[1] * (0.25 + t * 0.2);
                        f.later(k * 0.018f, () -> f.spawn(FxSprite.FLAME).at(wx, wy, wz).vel(0, 0.6, 0).fps(16).life(0.75f)
                                .size(0.26f * scale, 0.08f).ramp(0xFFFFF0B0, 0xFFFF7A1A, 0x00C21807).core(0.35f));
                    }
                }
                flash(f, x, y + 1.3, z, 1.6f * scale, 0xFFFFB040, 0.35f);
                for (int i = 0; i < count(30, scale); i++) {
                    f.spawn(FxSprite.EMBER).at(x + rnd(0.6), y + R.nextDouble() * 1.8, z + rnd(0.6)).vel(rnd(0.6), between(1.5f, 3f), rnd(0.6))
                            .sway(0.8f, 5f).life(between(0.8f, 1.3f)).size(0.05f * scale, 0f).flicker(0.5f).fade(0xFFFFB050);
                }
                f.spawn(FxSprite.SHOCKWAVE).at(x, y + 0.05, z).flat().life(0.8f).size(0.3f * scale, 2.2f * scale).grow(Fx.Grow.EASE).fade(0xFFFF8A1E);
            }
        }
    }
}
