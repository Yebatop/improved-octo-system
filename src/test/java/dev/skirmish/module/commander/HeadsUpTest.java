package dev.skirmish.module.commander;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class HeadsUpTest {
    private static final Instant T = Instant.parse("2026-09-26T12:00:00Z");

    @Test
    void announcesOncePerStart() {
        HeadsUp h = new HeadsUp();
        List<HeadsUp.Item> items = List.of(
                new HeadsUp.Item("end@1", "Захват Энда", "", T.plusSeconds(170)),
                new HeadsUp.Item("vote@1", "Голосование", "", T.plusSeconds(900)));
        Duration lead = Duration.ofMinutes(3);
        HeadsUp.Item first = h.due(items, T, lead);
        assertNotNull(first);
        assertEquals("Захват Энда", first.name());
        assertNull(h.due(items, T.plusSeconds(10), lead));
        // The vote comes within the lead later and is announced then.
        HeadsUp.Item vote = h.due(items, T.plusSeconds(730), lead);
        assertNotNull(vote);
        assertEquals("Голосование", vote.name());
        // Already started: never announced.
        assertNull(new HeadsUp().due(List.of(new HeadsUp.Item("x", "X", "", T.minusSeconds(5))), T, lead));
    }

    @Test
    void nextIncludesJustStarted() {
        List<HeadsUp.Item> items = List.of(
                new HeadsUp.Item("a", "A", "", T.minusSeconds(30)),
                new HeadsUp.Item("b", "B", "", T.plusSeconds(60)));
        assertEquals("A", HeadsUp.next(items, T, Duration.ofMinutes(3), Duration.ofMinutes(1)).name());
        assertEquals("B", HeadsUp.next(items, T.plusSeconds(40), Duration.ofMinutes(3), Duration.ofSeconds(60)).name());
        assertNull(HeadsUp.next(items, T.minusSeconds(600), Duration.ofMinutes(3), Duration.ofMinutes(1)));
    }
}
