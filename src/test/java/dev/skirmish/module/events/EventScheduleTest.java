package dev.skirmish.module.events;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventScheduleTest {
    /** 2026-09-27 is a Sunday; 16:00 MSK = 13:00 UTC. */
    private static final Instant SUNDAY_START = Instant.parse("2026-09-27T13:00:00Z");

    @Test
    void endCaptureBeforeDuringAfter() {
        Instant wednesday = Instant.parse("2026-09-23T20:00:00Z");
        EventSchedule.Window w = EventSchedule.endCapture(wednesday);
        assertEquals(SUNDAY_START, w.start());
        assertEquals(Instant.parse("2026-09-27T14:30:00Z"), w.end());
        assertFalse(w.active(wednesday));

        Instant during = Instant.parse("2026-09-27T14:00:00Z");
        w = EventSchedule.endCapture(during);
        assertEquals(SUNDAY_START, w.start());
        assertTrue(w.active(during));
        assertTrue(EventSchedule.endCapture(SUNDAY_START).active(SUNDAY_START));

        Instant after = Instant.parse("2026-09-27T14:30:00Z");
        assertEquals(SUNDAY_START.plus(Duration.ofDays(7)), EventSchedule.endCapture(after).start());

        // Sunday morning MSK (Saturday night UTC) is still this Sunday.
        assertEquals(SUNDAY_START, EventSchedule.endCapture(Instant.parse("2026-09-26T22:30:00Z")).start());
        // Monday 00:30 MSK = Sunday 21:30 UTC: next week already.
        assertEquals(SUNDAY_START.plus(Duration.ofDays(7)), EventSchedule.endCapture(Instant.parse("2026-09-27T21:30:00Z")).start());
    }

    @Test
    void bunkerEveryHour() {
        assertEquals(Instant.parse("2026-09-23T21:00:00Z"), EventSchedule.nextBunker(Instant.parse("2026-09-23T20:12:34Z")));
        assertEquals(Instant.parse("2026-09-23T21:00:00Z"), EventSchedule.nextBunker(Instant.parse("2026-09-23T20:00:00Z")));
    }

    @Test
    void restartAt0430Msk() {
        assertEquals(Instant.parse("2026-09-24T01:30:00Z"), EventSchedule.nextRestart(Instant.parse("2026-09-23T20:00:00Z")));
        assertEquals(Instant.parse("2026-09-24T01:30:00Z"), EventSchedule.nextRestart(Instant.parse("2026-09-23T22:00:00Z")));
        assertEquals(Instant.parse("2026-09-25T01:30:00Z"), EventSchedule.nextRestart(Instant.parse("2026-09-24T01:30:00Z")));
        assertEquals(Instant.parse("2026-09-24T01:30:00Z"), EventSchedule.nextRestart(Instant.parse("2026-09-24T01:29:59Z")));
    }

    @Test
    void voteCycle() {
        Instant anchor = Instant.parse("2026-09-23T18:00:00Z");
        assertNull(EventSchedule.nextVote(anchor, null));
        assertEquals(anchor, EventSchedule.nextVote(anchor, anchor));
        assertEquals(anchor.plus(Duration.ofMinutes(65)), EventSchedule.nextVote(anchor.plusSeconds(60), anchor));
        assertEquals(anchor.plus(Duration.ofMinutes(130)), EventSchedule.nextVote(anchor.plus(Duration.ofMinutes(66)), anchor));
        assertNull(EventSchedule.nextVote(anchor.plus(Duration.ofHours(5)), anchor), "stale anchor is hidden");
    }

    @Test
    void clockText() {
        assertEquals("0:00", EventSchedule.clock(-5));
        assertEquals("0:01", EventSchedule.clock(1));
        assertEquals("4:05", EventSchedule.clock(245_000));
        assertEquals("1:02:03", EventSchedule.clock(3_723_000));
    }
}
