package dev.skirmish.module.pvp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TagClockTest {
    @Test
    void serverSecondsAreSmoothedWithinOneSecond() {
        TagClock clock = new TagClock();
        clock.serverSeconds(20, 0);
        assertEquals(20f, clock.serverRemaining(0), 1e-4);
        assertEquals(19.5f, clock.serverRemaining(500), 1e-4);
        // A late board update never runs the ring past the next whole second.
        assertEquals(19f, clock.serverRemaining(3000), 1e-4);
        clock.serverSeconds(19, 1000);
        assertEquals(18.75f, clock.serverRemaining(1250), 1e-4);
        assertEquals(18.75f / 20f, clock.serverFraction(1250), 1e-4);
    }

    @Test
    void aHitRaisesThePeak() {
        TagClock clock = new TagClock();
        clock.serverSeconds(10, 0);
        assertEquals(1f, clock.serverFraction(0), 1e-4);
        clock.serverSeconds(5, 5000);
        assertEquals(0.5f, clock.serverFraction(5000), 1e-4);
        clock.serverSeconds(15, 6000);
        assertEquals(1f, clock.serverFraction(6000), 1e-4);
        clock.clearServer();
        assertEquals(0f, clock.serverFraction(6000), 1e-4);
    }

    @Test
    void localTimerRestartsOnHits() {
        TagClock clock = new TagClock();
        assertEquals(0f, clock.localRemaining(0, 20_000), 1e-4);
        clock.hit(1000);
        assertEquals(15f, clock.localRemaining(6000, 20_000), 1e-4);
        clock.hit(10_000);
        assertEquals(20f, clock.localRemaining(10_000, 20_000), 1e-4);
        assertEquals(0f, clock.localRemaining(40_000, 20_000), 1e-4);
        clock.clearLocal();
        assertEquals(0f, clock.localRemaining(10_000, 20_000), 1e-4);
    }
}
