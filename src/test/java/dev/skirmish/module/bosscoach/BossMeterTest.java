package dev.skirmish.module.bosscoach;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossMeterTest {
    private static final long WINDOW = 450;

    @Test
    void dropRightAfterMyHitIsMine() {
        BossMeter meter = new BossMeter();
        meter.progress(1.0f, 0, WINDOW);
        meter.myHit(1_000, true);
        meter.progress(0.98f, 1_100, WINDOW);
        // Someone else's hit, no hit of mine before it.
        meter.progress(0.95f, 2_000, WINDOW);
        assertEquals(0.05f, meter.total(), 1e-5);
        assertEquals(0.02f, meter.mine(), 1e-5);
        assertEquals(0.4f, meter.share(), 1e-4);
        assertEquals(1, meter.myHits());
    }

    @Test
    void oneDropPerHitAndOnlyInsideTheWindow() {
        BossMeter meter = new BossMeter();
        meter.progress(0.8f, 0, WINDOW);
        meter.myHit(1_000, true);
        meter.progress(0.79f, 1_050, WINDOW);
        meter.progress(0.78f, 1_100, WINDOW);
        meter.myHit(2_000, true);
        meter.progress(0.77f, 2_600, WINDOW);
        assertEquals(0.01f, meter.mine(), 1e-5);
        assertEquals(0.03f, meter.total(), 1e-5);
    }

    @Test
    void regenerationIsIgnored() {
        BossMeter meter = new BossMeter();
        meter.progress(0.5f, 0, WINDOW);
        meter.myHit(10, true);
        meter.progress(0.6f, 50, WINDOW);
        assertEquals(0f, meter.total());
        meter.progress(0.55f, 100, WINDOW);
        assertEquals(0.05f, meter.mine(), 1e-5);
        assertTrue(Float.isNaN(new BossMeter().share()));
    }

    @Test
    void onceTheBossWasHitByNameMinionHitsNoLongerCount() {
        BossMeter meter = new BossMeter();
        meter.progress(1.0f, 0, WINDOW);
        meter.myHit(100, false);
        meter.progress(0.99f, 150, WINDOW);
        assertEquals(0.01f, meter.mine(), 1e-5, "before any named hit, any mob hit counts");
        meter.myHit(1_000, true);
        meter.progress(0.98f, 1_050, WINDOW);
        meter.myHit(2_000, false);
        meter.progress(0.97f, 2_050, WINDOW);
        assertEquals(0.02f, meter.mine(), 1e-5);
    }

    @Test
    void rateOverTheRecentWindow() {
        BossMeter meter = new BossMeter();
        meter.progress(1.0f, 0, WINDOW);
        for (int i = 0; i < 5; i++) {
            long t = 1_000 + i * 1_000L;
            meter.myHit(t, true);
            meter.progress(1.0f - 0.01f * (i + 1), t + 50, WINDOW);
        }
        // 5 × 1 % between 1.05 s and 5.05 s: 5 % over 4 s.
        assertEquals(0.0125, meter.rate(5_050, 10_000), 1e-4);
        // Only the last drop inside a 1 s window: at least one second is assumed.
        assertEquals(0.01, meter.rate(5_050, 500), 1e-4);
        assertEquals(0.0, meter.rate(60_000, 10_000));
    }
}
