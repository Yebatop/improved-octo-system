package dev.skirmish.module.lag;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LagMeterTest {
    private static final long S = 1_000_000_000L;
    private static final long WINDOW = 10 * S;

    @Test
    void fullSpeedServerReadsTwenty() {
        TpsEstimator tps = new TpsEstimator(WINDOW);
        assertTrue(Double.isNaN(tps.tps(0)), "no estimate from a single packet");
        for (int i = 0; i <= 5; i++) {
            tps.onTime(i * S, 1000 + i * 20L);
        }
        assertEquals(20.0, tps.tps(5 * S), 1e-9);
    }

    @Test
    void slowServerReadsLow() {
        TpsEstimator tps = new TpsEstimator(WINDOW);
        // 20 ticks every 2 s = 10 TPS.
        for (int i = 0; i <= 4; i++) {
            tps.onTime(i * 2 * S, i * 20L);
        }
        assertEquals(10.0, tps.tps(8 * S), 1e-9);
    }

    @Test
    void catchUpIsClampedAndStallCountsBeforeTheNextPacket() {
        TpsEstimator tps = new TpsEstimator(WINDOW);
        tps.onTime(0, 0);
        tps.onTime(S / 2, 40);
        assertEquals(TpsEstimator.MAX_TPS, tps.tps(S / 2), 1e-9);

        TpsEstimator stalled = new TpsEstimator(WINDOW);
        for (int i = 0; i <= 3; i++) {
            stalled.onTime(i * S, i * 20L);
        }
        assertEquals(20.0, stalled.tps(3 * S), 1e-9);
        // 4 s of silence after the last packet (1 s of it expected): 60 ticks over 3 + 3 s.
        assertEquals(10.0, stalled.tps(7 * S), 1e-9);
    }

    @Test
    void oldSamplesLeaveTheWindowAndTimeJumpsReset() {
        TpsEstimator tps = new TpsEstimator(WINDOW);
        // A slow minute long ago, then full speed.
        for (int i = 0; i <= 5; i++) {
            tps.onTime(i * 4 * S, i * 20L);
        }
        long t = 20 * S;
        long game = 100;
        for (int i = 1; i <= 12; i++) {
            tps.onTime(t + i * S, game + i * 20L);
        }
        assertEquals(20.0, tps.tps(t + 12 * S), 1e-9);
        tps.onTime(t + 13 * S, 5);
        assertEquals(1, tps.size(), "game time went back (new world): start over");
        tps.reset();
        assertEquals(0, tps.size());
    }

    @Test
    void packetClockAndStalls() {
        PacketClock clock = new PacketClock();
        clock.reset(10 * S);
        assertFalse(clock.hasPackets());
        assertEquals(500, clock.silenceMs(10 * S + S / 2), "before the first packet: silence since connecting");
        clock.onPacket(11 * S);
        assertTrue(clock.hasPackets());
        assertEquals(1600, clock.silenceMs(11 * S + 1_600_000_000L));
        assertFalse(PacketClock.stalled(1500, 1500));
        assertTrue(PacketClock.stalled(1501, 1500));
        assertFalse(PacketClock.stalled(99_000, 0), "0 = off");
        assertEquals(1, PacketClock.bannerSeconds(1600));
        assertEquals(16, PacketClock.bannerSeconds(16_400));
    }

    @Test
    void pillColors() {
        assertEquals("good", LagText.tpsColor(19.9));
        assertEquals("warn", LagText.tpsColor(15));
        assertEquals("bad", LagText.tpsColor(8));
        assertEquals("good", LagText.pingColor(40));
        assertEquals("bad", LagText.pingColor(400));
        assertEquals("text", LagText.silenceColor(300, 1500));
        assertEquals("warn", LagText.silenceColor(1200, 1500));
        assertEquals("bad", LagText.silenceColor(1600, 1500));
        assertEquals("bad", LagText.worst(new String[]{"good", "bad", "warn"}));
        assertEquals("warn", LagText.worst(new String[]{"good", "text", "warn"}));
        assertEquals("good", LagText.worst(new String[]{"good", "text", "text"}));
    }
}
