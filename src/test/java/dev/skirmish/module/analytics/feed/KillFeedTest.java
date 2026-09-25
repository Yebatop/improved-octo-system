package dev.skirmish.module.analytics.feed;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KillFeedTest {
    @Test
    void newestFirstAndExpiring() {
        KillFeed<String> feed = new KillFeed<>();
        feed.add(KillFeed.Kind.KILL, UUID.randomUUID(), "A", false, "X", false, null, 0);
        feed.add(KillFeed.Kind.KILL, UUID.randomUUID(), "B", false, null, false, null, 1_000);
        feed.add(KillFeed.Kind.TOTEM, UUID.randomUUID(), "C", false, null, false, null, 2_000);
        List<KillFeed.Entry<String>> live = feed.live(2_500, 8_000, 5);
        assertEquals(List.of("C", "B", "A"), live.stream().map(KillFeed.Entry::victim).toList());
        assertEquals(2, feed.live(2_500, 8_000, 2).size());
        assertEquals(List.of("C"), feed.live(9_500, 8_000, 5).stream().map(KillFeed.Entry::victim).toList());
        feed.prune(100_000, 8_000);
        assertTrue(feed.isEmpty());
    }

    @Test
    void twoReportsOfOneDeathMerge() {
        KillFeed<String> feed = new KillFeed<>();
        UUID victim = UUID.randomUUID();
        feed.add(KillFeed.Kind.KILL, victim, "Enemy", false, null, false, null, 1_000);
        // The second report knows the killer: it fills the unknown one instead of adding a row.
        feed.add(KillFeed.Kind.KILL, victim, "Enemy", false, "Guess", false, "sword", 1_200);
        List<KillFeed.Entry<String>> live = feed.live(1_500, 8_000, 5);
        assertEquals(1, live.size());
        assertEquals("Guess", live.getFirst().killer());
        // The kill attribution to me overrides a guess.
        assertTrue(feed.setKiller(victim, "Me", true, "axe", 1_300));
        assertEquals("Me", live.getFirst().killer());
        assertTrue(live.getFirst().killerMe());
        assertEquals("axe", live.getFirst().weapon());
        // A later death of the same player is a new row.
        feed.add(KillFeed.Kind.KILL, victim, "Enemy", false, null, false, null, 60_000);
        assertEquals(2, feed.live(60_001, 100_000, 5).size());
        assertFalse(feed.setKiller(UUID.randomUUID(), "Me", true, null, 1_000));
    }

    @Test
    void totemAndDeathRowsAreSeparate() {
        KillFeed<String> feed = new KillFeed<>();
        UUID victim = UUID.randomUUID();
        feed.add(KillFeed.Kind.TOTEM, victim, "Enemy", false, null, false, null, 1_000);
        feed.add(KillFeed.Kind.KILL, victim, "Enemy", false, null, false, null, 1_500);
        assertEquals(2, feed.live(2_000, 8_000, 5).size());
        assertNull(feed.live(2_000, 8_000, 5).getFirst().killer());
    }

    @Test
    void rowsFadeAtTheEnd() {
        assertEquals(1f, KillFeed.alpha(1_000, 8_000, 600));
        assertEquals(0.5f, KillFeed.alpha(7_700, 8_000, 600), 1e-6);
        assertEquals(0f, KillFeed.alpha(8_000, 8_000, 600));
        assertEquals(1f, KillFeed.alpha(7_999, 8_000, 0));
    }

    @Test
    void boundedHistory() {
        KillFeed<String> feed = new KillFeed<>();
        for (int i = 0; i < KillFeed.KEPT + 10; i++) {
            feed.add(KillFeed.Kind.KILL, UUID.randomUUID(), "P" + i, false, null, false, null, i);
        }
        assertEquals(KillFeed.KEPT, feed.live(100, 1_000_000, 1_000).size());
    }
}
