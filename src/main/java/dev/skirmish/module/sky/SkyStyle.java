package dev.skirmish.module.sky;

/**
 * The colours of each sky: a day and a night gradient (zenith, mid sky, horizon, below the horizon) and the glow
 * near the sun at dusk, blended by how far the sun is below the horizon and darkened by rain. The fog is tinted with
 * the same horizon colour so the terrain fades into the sky without a seam. Pure Java.
 */
final class SkyStyle {
    /** Every sky there is. The first four keep their old config names. */
    enum Look {
        GALAXY, AURORA, QUASAR, SUNSET, NEBULA, BLACK_HOLE, SYNTHWAVE, BLOOD_MOON, ALIEN, METEORS
    }

    /** A vertical gradient: RGB at the zenith, at 25° up, at the horizon and below it. */
    record Palette(int zenith, int mid, int horizon, int ground) {
    }

    /** A look's colours: day and night gradients, the dusk glow and how many stars show by day (0..1). */
    record Style(Palette day, Palette night, int dusk, float starsByDay) {
    }

    private SkyStyle() {
    }

    static Style of(Look look) {
        return switch (look) {
            case GALAXY -> new Style(new Palette(0x1F5FD6, 0x4A8BEA, 0xA8CCF5, 0x3A5578),
                    new Palette(0x02040C, 0x0A1430, 0x1B2A52, 0x070B18), 0xFF9A5C, 0.05f);
            case AURORA -> new Style(new Palette(0x3C7FC4, 0x7FB6E0, 0xD8EEF7, 0x6A8FA3),
                    new Palette(0x020B12, 0x06222B, 0x0E3A3E, 0x051418), 0xFF8FB0, 0.0f);
            case QUASAR -> new Style(new Palette(0x34328F, 0x5A5CC9, 0xB6A9EE, 0x3A3470),
                    new Palette(0x05030F, 0x150A33, 0x2A1450, 0x0C0618), 0xC07CFF, 0.15f);
            case SUNSET -> new Style(new Palette(0x4A3C9E, 0xD26B8C, 0xFFB27A, 0x6B3F4F),
                    new Palette(0x120B2E, 0x3E1F5A, 0xA0456B, 0x26142E), 0xFF8A3D, 0.1f);
            case NEBULA -> new Style(new Palette(0x2A3F9E, 0x4C6BD6, 0x9FB4F0, 0x3A3F6B),
                    new Palette(0x070417, 0x1A0B3A, 0x3B1A5C, 0x120820), 0xFF6FAE, 0.2f);
            case BLACK_HOLE -> new Style(new Palette(0x2B2440, 0x4D3E6A, 0xA08A9E, 0x2E2536),
                    new Palette(0x010103, 0x07060F, 0x1A1020, 0x050308), 0xFF8A3D, 0.35f);
            case SYNTHWAVE -> new Style(new Palette(0x3B1E8C, 0x8E3FC2, 0xFF7AA8, 0x4A1E5C),
                    new Palette(0x0B0224, 0x3A0B5E, 0xC2186F, 0x1A0526), 0xFFB547, 0.3f);
            case BLOOD_MOON -> new Style(new Palette(0x5A1414, 0x9C2F24, 0xE0785A, 0x4A1A14),
                    new Palette(0x0A0103, 0x2A0508, 0x5C0E10, 0x140205), 0xFF4A2A, 0.1f);
            case ALIEN -> new Style(new Palette(0x1E8C7A, 0x48C0A0, 0xC8F0C8, 0x3A6B5A),
                    new Palette(0x021210, 0x07302A, 0x155048, 0x06201C), 0xF0E070, 0.25f);
            case METEORS -> new Style(new Palette(0x2466C8, 0x5596E6, 0xB0D4F7, 0x3A5A80),
                    new Palette(0x01030C, 0x081533, 0x162B5C, 0x050A1A), 0xFFAA70, 0.0f);
        };
    }

    /** 1 in the night, 0 by day, from the sun's height (y of its direction): dusk from 0.15 down to −0.25. */
    static float night(float sunY) {
        return 1f - smooth(-0.25f, 0.15f, sunY);
    }

    /** How much dusk it is: 1 while the sun is at the horizon, 0 when it is far from it. */
    static float dusk(float sunY) {
        return Math.max(0f, 1f - Math.abs(sunY + 0.03f) / 0.28f);
    }

    /** The gradient now: day and night blended, the dusk glow on the lower sky, darker and greyer in rain. */
    static Palette at(Style s, float night, float dusk, float rain) {
        Palette p = blend(s.day(), s.night(), night);
        int horizon = lerp(p.horizon(), s.dusk(), dusk * 0.65f);
        int mid = lerp(p.mid(), s.dusk(), dusk * 0.25f);
        p = new Palette(p.zenith(), mid, horizon, p.ground());
        if (rain > 0f) {
            float k = 1f - rain * 0.45f;
            p = new Palette(grey(p.zenith(), rain * 0.6f, k), grey(p.mid(), rain * 0.6f, k), grey(p.horizon(), rain * 0.6f, k),
                    grey(p.ground(), rain * 0.6f, k));
        }
        return p;
    }

    /** The dome colour at an elevation in degrees: ground below −8°, then horizon, mid (25°) and zenith. */
    static int dome(Palette p, float elevation) {
        if (elevation <= -8f) {
            return p.ground();
        }
        if (elevation <= 0f) {
            return lerp(p.ground(), p.horizon(), (elevation + 8f) / 8f);
        }
        if (elevation <= 25f) {
            float t = elevation / 25f;
            return lerp(p.horizon(), p.mid(), t * (2f - t));
        }
        float t = (elevation - 25f) / 65f;
        return lerp(p.mid(), p.zenith(), Math.min(1f, t));
    }

    static Palette blend(Palette a, Palette b, float t) {
        return new Palette(lerp(a.zenith(), b.zenith(), t), lerp(a.mid(), b.mid(), t), lerp(a.horizon(), b.horizon(), t),
                lerp(a.ground(), b.ground(), t));
    }

    /** RGB lerp (alpha dropped). */
    static int lerp(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * k);
        return (r << 16) | (g << 8) | bl;
    }

    /** Towards grey by {@code amount}, then scaled by {@code k}. */
    static int grey(int rgb, float amount, float k) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >> 8) & 0xFF;
        int b = rgb & 0xFF;
        int l = (r * 3 + g * 6 + b) / 10;
        int mixed = lerp(rgb, (l << 16) | (l << 8) | l, amount);
        return scale(mixed, k);
    }

    static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((rgb & 0xFF) * k));
        return (r << 16) | (g << 8) | b;
    }

    static float smooth(float e0, float e1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - e0) / (e1 - e0)));
        return t * t * (3f - 2f * t);
    }
}
