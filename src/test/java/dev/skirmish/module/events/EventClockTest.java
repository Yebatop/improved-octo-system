package dev.skirmish.module.events;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventClockTest {
    private static List<EventClock.Seen> lite(String... keys) {
        return java.util.Arrays.stream(keys).map(k -> new EventClock.Seen(k, "CUBE", null)).toList();
    }

    @Test
    void eventsOnAtTheFirstLookHaveNoKnownStart() {
        EventClock c = new EventClock();
        c.update(lite("a"), 1_000_000);
        EventClock.Estimate e = c.estimate("a", 1_060_000);
        assertNotNull(e);
        assertFalse(e.startKnown());
        assertEquals(60_000, e.elapsed());
        assertEquals(-1, e.typical());
    }

    @Test
    void appearingBetweenPollsDatesTheStartAndEndingTeachesTheLength() {
        long t0 = 1_000_000;
        EventClock c = new EventClock();
        c.update(lite(), t0);
        c.update(lite("a"), t0 + 30_000);
        EventClock.Estimate e = c.estimate("a", t0 + 30_000);
        assertNotNull(e);
        assertTrue(e.startKnown());
        assertEquals(15_000, e.elapsed());
        for (long t = 60_000; t <= 600_000; t += 30_000) {
            c.update(lite("a"), t0 + t);
        }
        c.update(lite(), t0 + 630_000);
        // Started at 15 s (between 0 and 30 s), ended at 615 s (between 600 and 630 s).
        assertEquals(600_000, c.typical("CUBE"));
        assertNull(c.estimate("a", t0 + 630_000));
        // The next one of the kind: time left from the learned length.
        c.update(lite("b"), t0 + 660_000);
        EventClock.Estimate b = c.estimate("b", t0 + 705_000);
        assertNotNull(b);
        assertEquals(60_000, b.elapsed());
        assertEquals(540_000, b.remaining());
    }

    @Test
    void aGapInPollingLearnsNothing() {
        long t0 = 1_000_000;
        EventClock c = new EventClock();
        c.update(lite(), t0);
        c.update(lite("a"), t0 + 30_000);
        c.update(lite(), t0 + 30_000 + EventClock.MAX_GAP + 1);
        assertEquals(-1, c.typical("CUBE"));
    }

    @Test
    void seedsUntilLearnedAndMedianAfter() {
        EventClock c = new EventClock();
        c.seeds(Map.of("CUBE", 900_000L));
        assertEquals(900_000, c.typical("CUBE"));
        c.lengths.put("CUBE", new java.util.ArrayList<>(List.of(100_000L, 500_000L, 300_000L)));
        assertEquals(300_000, c.typical("CUBE"));
    }

    @Test
    void primeStartsAreExact() {
        EventClock c = new EventClock();
        c.update(List.of(new EventClock.Seen("u", "bosses", 5_000L)), 1_000_000);
        EventClock.Estimate e = c.estimate("u", 1_000_000);
        assertNotNull(e);
        assertTrue(e.startKnown());
        assertEquals(995_000, e.elapsed());
    }
}
