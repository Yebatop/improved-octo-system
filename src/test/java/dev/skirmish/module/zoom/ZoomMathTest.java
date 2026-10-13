package dev.skirmish.module.zoom;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZoomMathTest {
    @Test
    void factorIsClampedToTheScrollRange() {
        assertEquals(ZoomMath.MIN_FACTOR, ZoomMath.clampFactor(0.5));
        assertEquals(ZoomMath.MAX_FACTOR, ZoomMath.clampFactor(50));
        assertEquals(4.0, ZoomMath.clampFactor(4));
        assertEquals(ZoomMath.MIN_FACTOR, ZoomMath.clampFactor(Double.NaN));
    }

    @Test
    void scrollMultipliesByOneStepPerEventAndStopsAtTheLimits() {
        assertEquals(5.0, ZoomMath.scroll(4, 1), 1e-9);
        assertEquals(3.2, ZoomMath.scroll(4, -1), 1e-9);
        // Smooth-scrolling touchpads send fractions: still one step, in the sign's direction.
        assertEquals(5.0, ZoomMath.scroll(4, 0.1), 1e-9);
        assertEquals(4.0, ZoomMath.scroll(4, 0), 1e-9);
        assertEquals(ZoomMath.MAX_FACTOR, ZoomMath.scroll(9.5, 1));
        assertEquals(ZoomMath.MIN_FACTOR, ZoomMath.scroll(1.6, -1));
        double f = 4;
        for (int i = 0; i < 100; i++) {
            f = ZoomMath.scroll(f, 1);
        }
        assertEquals(ZoomMath.MAX_FACTOR, f);
    }

    @Test
    void smoothingConvergesAndSnaps() {
        double m = 1.0;
        double target = 0.25;
        int ticks = 0;
        while (m != target && ticks < 100) {
            double next = ZoomMath.approach(m, target);
            assertTrue(next <= m);
            m = next;
            ticks++;
        }
        assertEquals(target, m);
        assertTrue(ticks < 15, "took " + ticks + " ticks");
    }

    @Test
    void sensitivityScalesTheTurnSpeedCubically() {
        for (double s : new double[]{0.0, 0.25, 0.5, 1.0}) {
            for (double scale : new double[]{0.1, 0.25, 0.5}) {
                double scaled = ZoomMath.scaledSensitivity(s, scale);
                double before = Math.pow(s * 0.6 + 0.2, 3);
                double after = Math.pow(scaled * 0.6 + 0.2, 3);
                assertEquals(before * scale, after, 1e-9);
            }
        }
        assertEquals(0.5, ZoomMath.scaledSensitivity(0.5, 1.0));
        assertEquals(0.5, ZoomMath.scaledSensitivity(0.5, 0.0));
    }
}
