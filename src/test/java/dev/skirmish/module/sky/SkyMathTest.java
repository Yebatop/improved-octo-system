package dev.skirmish.module.sky;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkyMathTest {
    @Test
    void nightCurve() {
        assertEquals(0f, SkyMath.night(6000), 1e-6);
        assertEquals(1f, SkyMath.night(18000), 1e-6);
        float dusk = SkyMath.night(12500);
        assertTrue(dusk > 0f && dusk < 1f, "dusk " + dusk);
        assertEquals(SkyMath.night(18000), SkyMath.night(18000 + 24000L * 5), 1e-6);
    }

    @Test
    void directionsAreUnit() {
        float[] d = SkyMath.dir(1.2, 0.4);
        assertEquals(1f, (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]), 1e-5);
        float[][] b = SkyMath.basis(d);
        assertEquals(0f, b[0][0] * d[0] + b[0][1] * d[1] + b[0][2] * d[2], 1e-5);
        assertEquals(0f, b[1][0] * d[0] + b[1][1] * d[1] + b[1][2] * d[2], 1e-5);
        assertEquals(0x80FFFFFF, SkyMath.argb(128, 0xFFFFFF));
    }
}
