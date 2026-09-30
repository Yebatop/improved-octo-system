package dev.skirmish.module.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyStyleTest {
    @Test
    void nightAndDuskFollowTheSun() {
        assertEquals(0f, SkyStyle.night(1f), 1e-6);
        assertEquals(1f, SkyStyle.night(-1f), 1e-6);
        assertTrue(SkyStyle.night(0f) > 0.2f && SkyStyle.night(0f) < 0.8f);
        assertTrue(SkyStyle.dusk(0f) > 0.8f);
        assertEquals(0f, SkyStyle.dusk(0.9f), 1e-6);
    }

    @Test
    void domeGradient() {
        SkyStyle.Palette p = new SkyStyle.Palette(0x000010, 0x000080, 0x0000F0, 0x000000);
        assertEquals(0x000000, SkyStyle.dome(p, -30));
        assertEquals(0x0000F0, SkyStyle.dome(p, 0));
        assertEquals(0x000080, SkyStyle.dome(p, 25));
        assertEquals(0x000010, SkyStyle.dome(p, 90));
    }

    @Test
    void everyLookHasColours() {
        for (SkyStyle.Look look : SkyStyle.Look.values()) {
            SkyStyle.Style s = SkyStyle.of(look);
            SkyStyle.Palette night = SkyStyle.at(s, 1f, 0f, 0f);
            SkyStyle.Palette day = SkyStyle.at(s, 0f, 0f, 0f);
            assertTrue(brightness(day.zenith()) > brightness(night.zenith()), look + " day brighter than night");
            SkyStyle.Palette rain = SkyStyle.at(s, 0f, 0f, 1f);
            assertTrue(brightness(rain.horizon()) < brightness(day.horizon()), look + " rain darker");
        }
    }

    @Test
    void celestialMatchesVanillaSun() {
        // At angle 0 the sun (straight up) stays up; at π it is straight down.
        assertArrayEquals(new float[]{0, 1, 0}, SkyMath.body(0f), 1e-6f);
        float[] down = SkyMath.body((float) Math.PI);
        assertEquals(-1f, down[1], 1e-6f);
        float[] up = SkyMath.celestial(new float[]{0, 1, 0}, 0f);
        assertArrayEquals(SkyMath.body(0f), up, 1e-6f);
        float[] turned = SkyMath.celestial(new float[]{0, 1, 0}, 0.7f);
        assertArrayEquals(SkyMath.body(0.7f), turned, 1e-6f);
    }

    private static int brightness(int rgb) {
        return ((rgb >> 16) & 0xFF) + ((rgb >> 8) & 0xFF) + (rgb & 0xFF);
    }
}
