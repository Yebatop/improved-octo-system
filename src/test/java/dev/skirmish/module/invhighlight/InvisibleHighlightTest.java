package dev.skirmish.module.invhighlight;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class InvisibleHighlightTest {
    @Test
    void pulseStaysVisible() {
        for (long t = 0; t < 3200; t += 50) {
            float f = InvisibleHighlightModule.pulseFactor(t, 1600f);
            assertTrue(f >= 0.55f - 1e-6 && f <= 1f + 1e-6, "pulse " + f + " at " + t);
        }
    }
}
