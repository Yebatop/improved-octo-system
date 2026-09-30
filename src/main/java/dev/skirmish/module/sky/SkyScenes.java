package dev.skirmish.module.sky;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import java.util.Random;

/**
 * What each sky shows, in four layers drawn one after another: the gradient dome, light behind the bodies
 * (additive: stars, nebulae, bands, glows, meteors), the bodies (opaque: sun, moon, planets, the black hole's
 * shadow, mountains, dust lanes) and light in front of them (additive: rings, halos, rims, jets).
 */
final class SkyScenes {
    /** One frame of the sky: radius, time in seconds, night 0..1, effect strength, sun/moon/star angles, the palette. */
    record Frame(float r, float t, float night, float dusk, float strength, float sunAngle, float moonAngle, float starAngle,
                 int moonPhase, float rain, SkyStyle.Palette palette, boolean bodies) {
        float[] sun() {
            return SkyMath.body(sunAngle);
        }

        /** The turn of the night sky, set so that what is authored "up" stands overhead at midnight. */
        float nightSky() {
            return starAngle + (float) Math.PI;
        }

        float[] moon() {
            return SkyMath.body(moonAngle);
        }

        /** Stars and other night lights: full at night, {@code byDay} of it by day, less in rain. */
        float nightLight(float byDay) {
            return strength * (byDay + (1f - byDay) * night) * (1f - rain * 0.85f);
        }
    }

    // ---- static tables ----

    private static final int NEBULA_BLOBS = 300;
    private static final float[][] NEBULA = new float[NEBULA_BLOBS][];
    private static final float[][] DUST_LANE = new float[110][];
    private static final int[] NEBULA_COLORS = {0xFF4FB0, 0x8C5CFF, 0x3FD8D0, 0xFF9A5C};
    private static final float[][] BAND_STARS = new float[900][];
    private static final float[] GALAXY_AXIS = SkyMath.normalize(new float[]{0.35f, 0.75f, 0.55f});

    static {
        Random random = new Random(0x5EB1A);
        // Three big colour lobes around one part of the sky (soft clouds), then a bright wispy filament in each.
        float[] home = SkyMath.dir(Math.toRadians(120), Math.toRadians(40));
        float[][] hb = SkyMath.basis(home);
        int n = 0;
        float[][] lobes = {{-0.18f, 0.05f}, {0.12f, 0.14f}, {0.05f, -0.16f}};
        for (int f = 0; f < 3; f++) {
            float[] centre = offset(home, hb, lobes[f][0], lobes[f][1]);
            for (int i = 0; i < 70; i++) {
                float[] pos = offset(centre, SkyMath.basis(centre), (float) random.nextGaussian() * 0.16f, (float) random.nextGaussian() * 0.12f);
                int color = random.nextFloat() < 0.8f ? f : random.nextInt(NEBULA_COLORS.length);
                NEBULA[n++] = new float[]{pos[0], pos[1], pos[2], 0.05f + random.nextFloat() * 0.12f, color, 0.25f + random.nextFloat() * 0.5f};
            }
            float[] pos = centre;
            float[] step = SkyMath.normalize(new float[]{(float) random.nextGaussian(), (float) random.nextGaussian() * 0.5f,
                    (float) random.nextGaussian()});
            for (int i = 0; i < 30; i++) {
                step = SkyMath.normalize(new float[]{step[0] + (float) random.nextGaussian() * 0.35f,
                        step[1] + (float) random.nextGaussian() * 0.2f, step[2] + (float) random.nextGaussian() * 0.35f});
                pos = SkyMath.normalize(new float[]{pos[0] + step[0] * 0.02f, pos[1] + step[1] * 0.02f, pos[2] + step[2] * 0.02f});
                NEBULA[n++] = new float[]{pos[0], pos[1], pos[2], 0.015f + random.nextFloat() * 0.03f, f, 0.7f + random.nextFloat() * 0.6f};
            }
        }
        float[] lane = SkyMath.normalize(new float[]{home[0] - 0.15f, home[1] + 0.05f, home[2] + 0.1f});
        float[] dir = SkyMath.normalize(new float[]{0.6f, -0.2f, -0.7f});
        for (int i = 0; i < DUST_LANE.length; i++) {
            lane = SkyMath.normalize(new float[]{lane[0] + dir[0] * 0.006f + (float) random.nextGaussian() * 0.012f,
                    lane[1] + dir[1] * 0.006f + (float) random.nextGaussian() * 0.012f, lane[2] + dir[2] * 0.006f
                    + (float) random.nextGaussian() * 0.012f});
            DUST_LANE[i] = new float[]{lane[0], lane[1], lane[2], 0.03f + random.nextFloat() * 0.05f};
        }
        float[][] b = SkyMath.basis(GALAXY_AXIS);
        for (int i = 0; i < BAND_STARS.length; i++) {
            float[] d = SkyShapes.onBand(b, GALAXY_AXIS, random.nextDouble() * Math.PI * 2, (float) (random.nextGaussian() * 0.09));
            BAND_STARS[i] = new float[]{d[0], d[1], d[2], 0.4f + (float) Math.pow(random.nextDouble(), 3) * 1.6f, random.nextFloat() * 6.28f};
        }
    }

    private SkyScenes() {
    }

    /** A direction moved from {@code d} by small angles along its basis vectors. */
    private static float[] offset(float[] d, float[][] b, float x, float y) {
        return SkyMath.normalize(new float[]{d[0] + b[0][0] * x + b[1][0] * y, d[1] + b[0][1] * x + b[1][1] * y, d[2] + b[0][2] * x + b[1][2] * y});
    }

    // ---- layer 2: light behind the bodies ----

    static void back(SkyStyle.Look look, VertexConsumer c, PoseStack.Pose p, Frame f) {
        float stars = f.nightLight(SkyStyle.of(look).starsByDay());
        if (stars > 0.01f) {
            SkyPainter.stars(c, p, f.r(), f.t(), stars, f.nightSky());
        }
        switch (look) {
            case GALAXY -> galaxy(c, p, f, f.nightLight(0f));
            case AURORA -> SkyPainter.aurora(c, p, f.r(), f.t(), f.nightLight(0f) * 1.2f);
            case QUASAR -> {
                nebula(c, p, f, f.nightLight(0.1f) * 0.45f);
                SkyPainter.quasar(c, p, f.r(), f.t(), f.nightLight(0.35f));
            }
            case SUNSET -> sunsetBack(c, p, f);
            case NEBULA -> nebula(c, p, f, f.nightLight(0.15f));
            case BLACK_HOLE -> blackHoleBack(c, p, f);
            case SYNTHWAVE -> synthBack(c, p, f);
            case BLOOD_MOON -> bloodBack(c, p, f);
            case ALIEN -> alienBack(c, p, f);
            case METEORS -> meteors(c, p, f, f.nightLight(0f));
        }
        if (f.bodies() && look != SkyStyle.Look.SYNTHWAVE && look != SkyStyle.Look.SUNSET) {
            sunGlow(c, p, f);
        }
    }

    // ---- layer 3: bodies ----

    static void bodies(SkyStyle.Look look, VertexConsumer c, PoseStack.Pose p, Frame f) {
        switch (look) {
            case GALAXY -> galaxyLane(c, p, f);
            case NEBULA -> nebulaDust(c, p, f);
            case BLACK_HOLE -> blackHoleShadow(c, p, f);
            case SYNTHWAVE -> synthSun(c, p, f);
            case SUNSET -> sunsetBodies(c, p, f);
            case ALIEN -> alienBodies(c, p, f);
            case BLOOD_MOON -> bloodMoon(c, p, f);
            default -> {
            }
        }
        if (!f.bodies()) {
            return;
        }
        if (look != SkyStyle.Look.SYNTHWAVE && look != SkyStyle.Look.SUNSET) {
            sunDisc(c, p, f);
        }
        if (look != SkyStyle.Look.BLOOD_MOON && look != SkyStyle.Look.SYNTHWAVE) {
            moon(c, p, f);
        }
    }

    // ---- layer 4: light in front ----

    static void front(SkyStyle.Look look, VertexConsumer c, PoseStack.Pose p, Frame f) {
        switch (look) {
            case BLACK_HOLE -> blackHoleFront(c, p, f);
            case SYNTHWAVE -> synthFront(c, p, f);
            case ALIEN -> alienFront(c, p, f);
            case SUNSET -> sunsetFront(c, p, f);
            default -> {
            }
        }
    }

    // ---- sun and moon ----

    /** Fades a body out as it sinks below the horizon. */
    private static float aboveHorizon(float[] d) {
        return SkyStyle.smooth(-0.06f, 0.04f, d[1]);
    }

    private static void sunGlow(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float[] sun = f.sun();
        float vis = aboveHorizon(sun) * (1f - f.rain() * 0.8f);
        if (vis <= 0.01f) {
            return;
        }
        SkyPainter.glow(c, p, sun, f.r(), f.r() * 0.35f, SkyMath.argb(Math.round(70 * vis), 0xFFD9A0), 32);
        SkyPainter.glow(c, p, sun, f.r(), f.r() * 0.12f, SkyMath.argb(Math.round(150 * vis), 0xFFF1C8), 24);
    }

    private static void sunDisc(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float[] sun = f.sun();
        float vis = aboveHorizon(sun) * (1f - f.rain() * 0.8f);
        if (vis <= 0.01f) {
            return;
        }
        SkyShapes.disc(c, p, sun, f.r(), (float) Math.toRadians(2.2), SkyMath.argb(Math.round(255 * vis), 0xFFF8E6), 24);
    }

    /** The moon as a lit sphere: the phase comes from where the light is (full = lit from behind us). */
    private static void moon(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float[] moon = f.moon();
        float vis = aboveHorizon(moon) * (1f - f.rain() * 0.7f);
        if (vis <= 0.01f) {
            return;
        }
        double phase = f.moonPhase() * Math.PI / 4;
        float lx = (float) Math.sin(phase);
        float lz = (float) Math.cos(phase);
        int sky = SkyStyle.dome(f.palette(), (float) Math.toDegrees(Math.asin(moon[1])));
        SkyShapes.sphere(c, p, moon, f.r(), (float) Math.toRadians(2.6), 8, 28, (x, y, z) -> {
            float light = Math.max(0f, x * lx + z * lz);
            float crater = SkyShapes.hash(Math.round(x * 6), Math.round(y * 6)) * 0.18f;
            int lit = SkyStyle.scale(0xE4E9F2, 1f - crater);
            int color = SkyStyle.lerp(SkyStyle.lerp(sky, 0x2A3140, 0.35f), lit, Math.min(1f, light * 1.3f));
            return SkyMath.argb(Math.round(255 * vis), color);
        });
    }

    // ---- galaxy ----

    private static void galaxy(VertexConsumer c, PoseStack.Pose p, Frame f, float s) {
        if (s <= 0.01f) {
            return;
        }
        float[] axis = SkyMath.celestial(GALAXY_AXIS, f.nightSky());
        SkyShapes.band(c, p, f.r(), axis, 0.26f, SkyMath.argb(Math.round(40 * s), 0x7C8CFF), 96, 5);
        SkyShapes.band(c, p, f.r(), axis, 0.12f, SkyMath.argb(Math.round(60 * s), 0xB8C4FF), 96, 4);
        SkyShapes.band(c, p, f.r(), axis, 0.09f, SkyMath.argb(Math.round(55 * s), 0xFFF0E0), 96, 6);
        SkyPainter.galaxy(c, p, f.r(), s, f.nightSky());
        float unit = f.r() * 0.0014f;
        for (float[] st : BAND_STARS) {
            float tw = 0.75f + 0.25f * (float) Math.sin(f.t() * 1.3f + st[4]);
            SkyPainter.spot(c, p, SkyMath.celestial(new float[]{st[0], st[1], st[2]}, f.nightSky()), f.r(), unit * st[3],
                    SkyMath.argb(Math.round(220 * s * tw), 0xE8EEFF));
        }
    }

    private static void galaxyLane(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.nightLight(0f);
        if (s <= 0.01f) {
            return;
        }
        float[] axis = SkyMath.celestial(GALAXY_AXIS, f.nightSky());
        int dark = SkyStyle.scale(f.palette().zenith(), 0.6f);
        // A soft dark rift along the middle, wavy like the real one (dust blobs, not a line).
        float[][] b = SkyMath.basis(axis);
        for (int i = 0; i < 360; i++) {
            double t = Math.PI * 2 * i / 360;
            float off = 0.02f * (float) Math.sin(t * 5) + 0.012f * (float) Math.sin(t * 13 + 1);
            float size = 0.03f + 0.022f * (0.5f + 0.5f * (float) Math.sin(t * 7 + 2));
            SkyPainter.glow(c, p, SkyShapes.onBand(b, axis, t, off), f.r(), f.r() * size, SkyMath.argb(Math.round(38 * s), dark), 12);
        }
    }

    // ---- nebula ----

    private static void nebula(VertexConsumer c, PoseStack.Pose p, Frame f, float s) {
        if (s <= 0.01f) {
            return;
        }
        for (float[] b : NEBULA) {
            float[] d = SkyMath.celestial(new float[]{b[0], b[1], b[2]}, f.nightSky());
            int alpha = Math.round(46 * s * b[5]);
            SkyPainter.glow(c, p, d, f.r(), f.r() * b[3], SkyMath.argb(alpha, NEBULA_COLORS[(int) b[4]]), 14);
        }
        // Young stars in the nebula.
        for (int i = 0; i < NEBULA.length; i += 7) {
            float[] b = NEBULA[i];
            float[] d = SkyMath.celestial(new float[]{b[0], b[1], b[2]}, f.nightSky());
            SkyPainter.glow(c, p, d, f.r(), f.r() * 0.006f, SkyMath.argb(Math.round(230 * s), 0xFFFFFF), 8);
        }
    }

    private static void nebulaDust(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.nightLight(0f);
        if (s <= 0.01f) {
            return;
        }
        int dark = SkyStyle.scale(f.palette().zenith(), 0.7f);
        for (float[] b : DUST_LANE) {
            float[] d = SkyMath.celestial(new float[]{b[0], b[1], b[2]}, f.nightSky());
            SkyPainter.glow(c, p, d, f.r(), f.r() * b[3], SkyMath.argb(Math.round(42 * s), dark), 12);
        }
    }

    // ---- black hole ----

    private static final float[] HOLE = SkyMath.dir(Math.toRadians(205), Math.toRadians(30));
    private static final float HOLE_R = 0.075f;

    /** The accretion disk's colour: white-hot inside to red outside, brighter on the side coming towards us. */
    private static int diskColor(double angle, float across, float s) {
        float doppler = 0.35f + 0.65f * (0.5f + 0.5f * (float) Math.cos(angle));
        int rgb = across < 0.35f ? SkyStyle.lerp(0xFFF6DC, 0xFFC061, across / 0.35f) : SkyStyle.lerp(0xFFC061, 0xC2381A, (across - 0.35f) / 0.65f);
        float edge = (float) Math.sin(Math.PI * Math.min(1f, across * 1.05f + 0.02f));
        return SkyMath.argb(Math.round(230 * s * doppler * edge), rgb);
    }

    private static float[][] holeFrame(Frame f) {
        float[][] b = SkyMath.basis(HOLE);
        float tilt = (float) Math.toRadians(-9);
        float[] ax = {b[0][0] * (float) Math.cos(tilt) + b[1][0] * (float) Math.sin(tilt), b[0][1] * (float) Math.cos(tilt) + b[1][1] * (float) Math.sin(tilt),
                b[0][2] * (float) Math.cos(tilt) + b[1][2] * (float) Math.sin(tilt)};
        float[] ay = SkyMath.normalize(SkyMath.cross(new float[]{-HOLE[0], -HOLE[1], -HOLE[2]}, ax));
        return new float[][]{ax, ay, {HOLE[0] * f.r(), HOLE[1] * f.r(), HOLE[2] * f.r()}};
    }

    private static void blackHoleBack(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.strength() * (0.55f + 0.45f * f.night()) * (1f - f.rain() * 0.6f);
        float size = f.r() * HOLE_R;
        SkyPainter.glow(c, p, HOLE, f.r(), size * 7f, SkyMath.argb(Math.round(55 * s), 0xFF8A3D), 32);
        float[][] fr = holeFrame(f);
        // The far half of the disk, behind the shadow.
        SkyShapes.ring(c, p, fr[2], fr[0], fr[1], size * 1.7f, size * 5.2f, 0.17f, 0, Math.PI, 64, 8,
                (a, across) -> diskColor(a, across, s));
    }

    private static void blackHoleShadow(VertexConsumer c, PoseStack.Pose p, Frame f) {
        SkyShapes.disc(c, p, HOLE, f.r(), (float) Math.atan(HOLE_R), 0xFF000000, 48);
    }

    private static void blackHoleFront(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.strength() * (0.55f + 0.45f * f.night()) * (1f - f.rain() * 0.6f);
        float size = f.r() * HOLE_R;
        float[][] fr = holeFrame(f);
        float[][] b = SkyMath.basis(HOLE);
        // Lensed image of the far side: a bright halo hugging the shadow, strongest above and below.
        SkyShapes.ring(c, p, fr[2], b[0], b[1], size * 1.02f, size * 1.9f, 1f, 0, Math.PI * 2, 72, 6, (a, across) -> {
            float vertical = 0.35f + 0.65f * (float) Math.abs(Math.sin(a));
            float fade = (1f - across) * (1f - across);
            return SkyMath.argb(Math.round(240 * s * vertical * fade), SkyStyle.lerp(0xFFE9BE, 0xFF7A2E, across));
        });
        // Photon ring.
        SkyShapes.ring(c, p, fr[2], b[0], b[1], size * 1.0f, size * 1.07f, 1f, 0, Math.PI * 2, 64, 1,
                (a, across) -> SkyMath.argb(Math.round(255 * s), 0xFFF8EE));
        // The near half of the disk, in front of the shadow.
        SkyShapes.ring(c, p, fr[2], fr[0], fr[1], size * 1.7f, size * 5.2f, 0.17f, Math.PI, Math.PI * 2, 64, 8,
                (a, across) -> diskColor(a, across, s));
    }

    // ---- synthwave ----

    private static final float[] RETRO_SUN = SkyMath.dir(Math.toRadians(270), Math.toRadians(8));
    private static final float RETRO_R = (float) Math.toRadians(14);

    private static void synthBack(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.strength() * (1f - f.rain() * 0.6f);
        SkyPainter.glow(c, p, RETRO_SUN, f.r(), f.r() * 0.75f, SkyMath.argb(Math.round(85 * s), 0xFF3FA4), 40);
        SkyPainter.glow(c, p, RETRO_SUN, f.r(), f.r() * 0.35f, SkyMath.argb(Math.round(90 * s), 0xFFB547), 32);
        // Neon horizon line all around.
        SkyShapes.band(c, p, f.r(), new float[]{0, 1, 0}, 0.03f, SkyMath.argb(Math.round(160 * s), 0xFF4FD8), 96, 3);
    }

    /** The striped retro sun: horizontal slabs, the gaps widening towards the bottom; then the mountains. */
    private static void synthSun(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float[][] b = SkyMath.basis(RETRO_SUN);
        float radius = f.r() * (float) Math.tan(RETRO_R);
        float[] o = {RETRO_SUN[0] * f.r(), RETRO_SUN[1] * f.r(), RETRO_SUN[2] * f.r()};
        // Solid upper part in thin slices (so the edge stays round), then stripes whose gaps widen downwards.
        for (float y = 1f; y > 0.1f; y -= 0.04f) {
            float y1 = Math.max(0.1f, y - 0.04f);
            slab(c, p, o, b, radius, y, y1, sunColor(y), sunColor(y1));
        }
        float y = 0.1f;
        for (int stripe = 0; y > -1f; stripe++) {
            float gap = 0.03f + stripe * 0.014f;
            y -= gap;
            float h = Math.max(0.035f, 0.12f - stripe * 0.012f);
            float bottom = Math.max(-1f, y - h);
            for (float yy = y; yy > bottom; yy -= 0.03f) {
                float y1 = Math.max(bottom, yy - 0.03f);
                slab(c, p, o, b, radius, yy, y1, sunColor(yy), sunColor(y1));
            }
            y = bottom;
        }
        mountains(c, p, f);
    }

    private static int sunColor(float y) {
        float t = (1f - y) / 2f;
        return 0xFF000000 | (t < 0.5f ? SkyStyle.lerp(0xFFF06B, 0xFF9A3D, t * 2f) : SkyStyle.lerp(0xFF9A3D, 0xFF2F9C, (t - 0.5f) * 2f));
    }

    /** The part of a disc (unit radius) between heights y0 > y1, as a quad on its chords. */
    private static void slab(VertexConsumer c, PoseStack.Pose p, float[] o, float[][] b, float radius, float y0, float y1, int c0, int c1) {
        float x0 = (float) Math.sqrt(Math.max(0, 1 - y0 * y0));
        float x1 = (float) Math.sqrt(Math.max(0, 1 - y1 * y1));
        point(c, p, o, b, radius, -x0, y0, c0);
        point(c, p, o, b, radius, x0, y0, c0);
        point(c, p, o, b, radius, x1, y1, c1);
        point(c, p, o, b, radius, -x1, y1, c1);
    }

    private static void point(VertexConsumer c, PoseStack.Pose p, float[] o, float[][] b, float radius, float x, float y, int color) {
        c.addVertex(p, o[0] + (b[0][0] * x + b[1][0] * y) * radius, o[1] + (b[0][1] * x + b[1][1] * y) * radius,
                o[2] + (b[0][2] * x + b[1][2] * y) * radius).setColor(color);
    }

    /** Height of the mountain line (degrees) at an azimuth: a few layered ridges. */
    private static float ridge(double az) {
        return (float) (2.2 + 1.6 * Math.sin(az * 3 + 0.7) + 1.1 * Math.sin(az * 7 + 2.1) + 0.7 * Math.abs(Math.sin(az * 13 + 0.3))
                + 0.4 * Math.sin(az * 29));
    }

    private static void mountains(VertexConsumer c, PoseStack.Pose p, Frame f) {
        int steps = 180;
        int dark = 0xFF000000 | SkyStyle.scale(f.palette().ground(), 0.8f);
        int top = 0xFF000000 | SkyStyle.lerp(f.palette().ground(), 0x5A1A6E, 0.5f);
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2 * i / steps;
            double a1 = Math.PI * 2 * (i + 1) / steps;
            float h0 = Math.max(0.3f, ridge(a0));
            float h1 = Math.max(0.3f, ridge(a1));
            SkyShapes.vertex(c, p, SkyMath.dir(a0, Math.toRadians(-4)), f.r(), dark);
            SkyShapes.vertex(c, p, SkyMath.dir(a1, Math.toRadians(-4)), f.r(), dark);
            SkyShapes.vertex(c, p, SkyMath.dir(a1, Math.toRadians(h1)), f.r(), top);
            SkyShapes.vertex(c, p, SkyMath.dir(a0, Math.toRadians(h0)), f.r(), top);
        }
    }

    private static void synthFront(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.strength() * (1f - f.rain() * 0.6f);
        int steps = 180;
        int rim = SkyMath.argb(Math.round(200 * s), 0xFF4FD8);
        for (int i = 0; i < steps; i++) {
            double a0 = Math.PI * 2 * i / steps;
            double a1 = Math.PI * 2 * (i + 1) / steps;
            float[] p0 = SkyMath.dir(a0, Math.toRadians(Math.max(0.3f, ridge(a0))));
            float[] p1 = SkyMath.dir(a1, Math.toRadians(Math.max(0.3f, ridge(a1))));
            SkyShapes.streak(c, p, f.r(), p0, p1, f.r() * 0.0025f, f.r() * 0.0025f, rim, rim);
        }
    }

    // ---- eternal sunset ----

    private static final float[] LOW_SUN = SkyMath.dir(Math.toRadians(250), Math.toRadians(3));

    private static void sunsetBack(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.strength() * (1f - f.rain() * 0.7f);
        SkyPainter.sunset(c, p, f.r(), s);
        SkyPainter.glow(c, p, LOW_SUN, f.r(), f.r() * 0.9f, SkyMath.argb(Math.round(80 * s), 0xFF7A3D), 40);
        SkyPainter.glow(c, p, LOW_SUN, f.r(), f.r() * 0.3f, SkyMath.argb(Math.round(140 * s), 0xFFC37A), 32);
        // Soft rays fanning up from the sun.
        float[][] b = SkyMath.basis(LOW_SUN);
        for (int i = 0; i < 11; i++) {
            double a = Math.toRadians(18 + i * 13.5 + 3 * Math.sin(f.t() * 0.1 + i));
            float[] dir = SkyMath.normalize(new float[]{b[0][0] * (float) Math.cos(a) + b[1][0] * (float) Math.sin(a),
                    b[0][1] * (float) Math.cos(a) + b[1][1] * (float) Math.sin(a), b[0][2] * (float) Math.cos(a) + b[1][2] * (float) Math.sin(a)});
            float[] end = SkyMath.normalize(new float[]{LOW_SUN[0] + dir[0] * 0.9f, LOW_SUN[1] + dir[1] * 0.9f, LOW_SUN[2] + dir[2] * 0.9f});
            float pulse = 0.6f + 0.4f * (float) Math.sin(f.t() * 0.3 + i * 1.7);
            SkyShapes.softStreak(c, p, f.r(), LOW_SUN, end, f.r() * 0.01f, f.r() * 0.07f,
                    SkyMath.argb(Math.round(26 * s * pulse), 0xFFD3A0), SkyMath.argb(0, 0xFF8A5C));
        }
    }

    private static void sunsetBodies(VertexConsumer c, PoseStack.Pose p, Frame f) {
        SkyShapes.disc(c, p, LOW_SUN, f.r(), (float) Math.toRadians(3.2), 0xFFFFE2B0, 32);
        // Long fluffy clouds lit from below by the low sun: overlapping soft puffs along a gentle curve.
        Random random = new Random(77);
        float fade = 1f - f.rain() * 0.5f;
        for (int i = 0; i < 8; i++) {
            double az = Math.toRadians(170 + random.nextFloat() * 160);
            double el = Math.toRadians(5 + random.nextFloat() * 16);
            double len = Math.toRadians(25 + random.nextFloat() * 40);
            float thick = 0.024f + random.nextFloat() * 0.02f;
            float drift = f.t() * 0.002f * (0.5f + random.nextFloat());
            int lit = SkyStyle.lerp(0xFF8A6B, 0xFFC08A, random.nextFloat());
            int shade = SkyStyle.lerp(lit, f.palette().mid(), 0.45f);
            int puffs = 40;
            double bend = random.nextFloat() * 0.06 - 0.03;
            for (int k = 0; k < puffs; k++) {
                float u = k / (float) (puffs - 1);
                float taper = (float) Math.sin(Math.PI * (0.08 + 0.84 * u));
                double pa = az + drift + len * u;
                double pe = el + bend * Math.sin(Math.PI * u) + Math.toRadians((random.nextFloat() - 0.5f) * 1.2f);
                float size = f.r() * thick * (0.6f + 0.8f * random.nextFloat()) * taper;
                // A darker base puff, then a lit one slightly lower (light comes from the sun below).
                SkyPainter.glow(c, p, SkyMath.dir(pa, pe + Math.toRadians(0.8)), f.r(), size * 1.3f, SkyMath.argb(Math.round(40 * fade * taper), shade), 12);
                SkyPainter.glow(c, p, SkyMath.dir(pa, pe - Math.toRadians(0.3)), f.r(), size, SkyMath.argb(Math.round(55 * fade * taper), lit), 12);
            }
        }
    }

    private static void sunsetFront(VertexConsumer c, PoseStack.Pose p, Frame f) {
        SkyPainter.glow(c, p, LOW_SUN, f.r(), f.r() * 0.08f, SkyMath.argb(Math.round(200 * f.strength()), 0xFFFFFF), 24);
    }

    // ---- blood moon ----

    private static final float[] BLOOD = SkyMath.dir(Math.toRadians(140), Math.toRadians(24));

    private static void bloodBack(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.strength() * (0.4f + 0.6f * f.night()) * (1f - f.rain() * 0.6f);
        SkyPainter.glow(c, p, BLOOD, f.r(), f.r() * 0.8f, SkyMath.argb(Math.round(95 * s), 0xFF2A1A), 40);
        SkyPainter.glow(c, p, BLOOD, f.r(), f.r() * 0.3f, SkyMath.argb(Math.round(90 * s), 0xFF5A3A), 32);
        // Red mist low over the horizon.
        SkyShapes.band(c, p, f.r(), new float[]{0, 1, 0}, 0.14f, SkyMath.argb(Math.round(70 * s), 0xB0141A), 72, 4);
    }

    private static void bloodMoon(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float vis = 0.35f + 0.65f * f.night();
        SkyShapes.sphere(c, p, BLOOD, f.r(), (float) Math.toRadians(10), 14, 56, (x, y, z) -> {
            float light = Math.max(0f, x * -0.45f + y * 0.35f + z * 0.82f);
            float crater = SkyShapes.hash(Math.round(x * 9 + 20), Math.round(y * 9 + 20));
            float mare = SkyShapes.hash(Math.round(x * 3 + 7), Math.round(y * 3 + 3));
            int base = SkyStyle.lerp(0xFF6A3D, 0xA8201C, mare * 0.7f + crater * 0.3f);
            int color = SkyStyle.lerp(0x2A0406, base, 0.2f + light * 0.9f);
            return SkyMath.argb(Math.round(255 * vis), color);
        });
    }

    // ---- alien planet ----

    private static final float[] GIANT = SkyMath.dir(Math.toRadians(60), Math.toRadians(26));
    private static final float GIANT_R = (float) Math.toRadians(15);
    private static final int[] BANDS = {0xE8C99A, 0xC98F5A, 0xF2DDB8, 0xA96A44, 0xE0B98C, 0x8C5A3A, 0xF0D6A8};

    private static float[][] giantRings(Frame f) {
        float[][] b = SkyMath.basis(GIANT);
        float tilt = (float) Math.toRadians(22);
        float[] ax = SkyMath.normalize(new float[]{b[0][0] * (float) Math.cos(tilt) + b[1][0] * (float) Math.sin(tilt),
                b[0][1] * (float) Math.cos(tilt) + b[1][1] * (float) Math.sin(tilt), b[0][2] * (float) Math.cos(tilt) + b[1][2] * (float) Math.sin(tilt)});
        float[] ay = SkyMath.normalize(SkyMath.cross(new float[]{-GIANT[0], -GIANT[1], -GIANT[2]}, ax));
        return new float[][]{ax, ay, {GIANT[0] * f.r(), GIANT[1] * f.r(), GIANT[2] * f.r()}};
    }

    private static int ringColor(float across, float alpha) {
        float gap = across > 0.55f && across < 0.62f ? 0.15f : 1f;
        float edge = (float) Math.sin(Math.PI * across);
        int rgb = SkyStyle.lerp(0xE8D2A8, 0xA88A6A, across);
        return SkyMath.argb(Math.round(170 * alpha * gap * (0.4f + 0.6f * edge)), rgb);
    }

    private static void alienBack(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.strength() * (1f - f.rain() * 0.6f);
        SkyPainter.glow(c, p, GIANT, f.r(), f.r() * 0.55f, SkyMath.argb(Math.round(55 * s), 0xF0D6A8), 32);
        float[][] fr = giantRings(f);
        float radius = f.r() * (float) Math.tan(GIANT_R);
        // Far half of the rings, behind the planet (drawn here as light; the planet covers it).
        SkyShapes.ring(c, p, fr[2], fr[0], fr[1], radius * 1.35f, radius * 2.3f, 0.2f, 0, Math.PI, 72, 8,
                (a, across) -> ringColor(across, s));
        // A green veil like an aurora of another world.
        SkyPainter.aurora(c, p, f.r(), f.t() * 0.6f, f.nightLight(0f) * 0.6f);
    }

    private static void alienBodies(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float tilt = (float) Math.toRadians(22);
        float st = (float) Math.sin(tilt);
        float ct = (float) Math.cos(tilt);
        SkyShapes.sphere(c, p, GIANT, f.r(), GIANT_R, 16, 64, (x, y, z) -> {
            float lat = y * ct - x * st;
            float swirl = 0.08f * (float) Math.sin(x * 9 + f.t() * 0.05f);
            float band = (lat + swirl + 1f) * 0.5f * (BANDS.length - 1);
            int i = Math.max(0, Math.min(BANDS.length - 2, (int) band));
            int rgb = SkyStyle.lerp(BANDS[i], BANDS[i + 1], band - i);
            float light = Math.max(0f, x * -0.55f + y * 0.35f + z * 0.76f);
            return 0xFF000000 | SkyStyle.lerp(0x1A120C, rgb, 0.12f + light * 0.95f);
        });
        // Two small moons circling slowly.
        for (int k = 0; k < 2; k++) {
            double az = Math.toRadians(95 + k * 40) + f.t() * 0.004 * (k + 1);
            float[] d = SkyMath.dir(az, Math.toRadians(34 + k * 12));
            float size = (float) Math.toRadians(k == 0 ? 2.4 : 1.5);
            int tone = k == 0 ? 0xC8E8FF : 0xFFD0B0;
            SkyShapes.sphere(c, p, d, f.r(), size, 6, 20, (x, y, z) -> {
                float light = Math.max(0f, x * -0.55f + y * 0.35f + z * 0.76f);
                return 0xFF000000 | SkyStyle.lerp(0x10181C, tone, 0.1f + light);
            });
        }
    }

    private static void alienFront(VertexConsumer c, PoseStack.Pose p, Frame f) {
        float s = f.strength() * (1f - f.rain() * 0.6f);
        float[][] fr = giantRings(f);
        float radius = f.r() * (float) Math.tan(GIANT_R);
        SkyShapes.ring(c, p, fr[2], fr[0], fr[1], radius * 1.35f, radius * 2.3f, 0.2f, Math.PI, Math.PI * 2, 72, 8,
                (a, across) -> ringColor(across, s));
    }

    // ---- meteors ----

    private static void meteors(VertexConsumer c, PoseStack.Pose p, Frame f, float s) {
        if (s <= 0.01f) {
            return;
        }
        int slots = 14;
        for (int slot = 0; slot < slots; slot++) {
            float period = 2.2f + SkyShapes.hash(slot, 1) * 2.5f;
            float time = f.t() + SkyShapes.hash(slot, 2) * period;
            int cycle = (int) Math.floor(time / period);
            float phase = time / period - cycle;
            float life = 0.45f;
            if (phase > life) {
                continue;
            }
            float k = phase / life;
            double az = SkyShapes.hash(slot, cycle * 3 + 1) * Math.PI * 2;
            double el = Math.toRadians(30 + SkyShapes.hash(slot, cycle * 3 + 2) * 45);
            double heading = SkyShapes.hash(slot, cycle * 3 + 3) * Math.PI * 2;
            float[] start = SkyMath.dir(az, el);
            float[][] b = SkyMath.basis(start);
            float[] dir = {b[0][0] * (float) Math.cos(heading) + b[1][0] * (float) Math.sin(heading) - 0.3f * b[1][0],
                    b[0][1] * (float) Math.cos(heading) + b[1][1] * (float) Math.sin(heading) - 0.3f * b[1][1],
                    b[0][2] * (float) Math.cos(heading) + b[1][2] * (float) Math.sin(heading) - 0.3f * b[1][2]};
            dir = SkyMath.normalize(dir);
            float travel = 0.35f;
            float[] head = SkyMath.normalize(new float[]{start[0] + dir[0] * travel * k, start[1] + dir[1] * travel * k,
                    start[2] + dir[2] * travel * k});
            float tail = 0.16f * (float) Math.sin(Math.PI * Math.min(1f, k * 1.4f));
            float[] end = SkyMath.normalize(new float[]{head[0] - dir[0] * tail, head[1] - dir[1] * tail, head[2] - dir[2] * tail});
            float bright = (float) Math.sin(Math.PI * k);
            SkyShapes.streak(c, p, f.r(), head, end, f.r() * 0.004f, 0f, SkyMath.argb(Math.round(255 * s * bright), 0xE6F2FF),
                    SkyMath.argb(0, 0x8CB4FF));
            SkyPainter.glow(c, p, head, f.r(), f.r() * 0.012f, SkyMath.argb(Math.round(200 * s * bright), 0xFFFFFF), 8);
        }
    }
}
