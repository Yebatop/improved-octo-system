package dev.skirmish.module.evtimers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTimersTest {
    @Test
    void sidebarMineLines() {
        assertEquals(252, MineClock.parseSidebar("§7Обновление шахты: §f4:12"));
        assertEquals(185, MineClock.parseSidebar("Шахта обновится через 3 мин 5 сек"));
        assertEquals(420, MineClock.parseSidebar("ᴀᴠᴛᴏшахта: 7 мин"));
        assertEquals(42, MineClock.parseSidebar("До обновления шахты 42 сек."));
        assertEquals(-1, MineClock.parseSidebar("Онлайн: 12:30"));
        assertEquals(-1, MineClock.parseSidebar("Шахта"));
    }

    @Test
    void mineClockExtrapolatesAndLearns() {
        MineClock clock = new MineClock();
        assertFalse(clock.known());
        clock.onSidebar(0, 60);
        assertEquals(60_000, clock.remainingMs(0));
        assertFalse(clock.estimate(1_000));
        // Past the predicted refill with nothing new seen: the next default period is assumed.
        assertEquals(MineClock.DEFAULT_PERIOD_MS - 10_000, clock.remainingMs(70_000));
        assertTrue(clock.estimate(70_000));

        MineClock learned = new MineClock();
        learned.onRefill(1_000);
        learned.onRefill(1_000 + 7 * 60_000);
        assertTrue(learned.learnedPeriod());
        assertEquals(7 * 60_000, learned.periodMs());
        assertEquals(7 * 60_000, learned.remainingMs(1_000 + 7 * 60_000));
        // A gap outside 1–30 min does not change the period.
        learned.onRefill(1_000 + 7 * 60_000 + 5_000);
        assertEquals(7 * 60_000, learned.periodMs());
    }

    @Test
    void burstNeedsManyBlocksInsideTheMine() {
        MineClock.Burst burst = new MineClock.Burst(300, 0L, 40, 24);
        for (int x = 0; x < 21; x++) {
            for (int z = 0; z < 21; z++) {
                burst.add(0, 100 + x, 60, 200 + z);
            }
        }
        int[] c = burst.poll(0);
        assertNotNull(c);
        assertEquals(110, c[0]);
        assertEquals(210, c[2]);

        for (int i = 0; i < 50; i++) {
            burst.add(10, i, 60, 0);
        }
        assertNull(burst.poll(10));

        for (int i = 0; i < 400; i++) {
            burst.add(20, i, 60, 0);
        }
        assertNull(burst.poll(20), "too wide for the mine");
    }

    @Test
    void sunCoreDropsGroupAndWindow() {
        DropClock clock = new DropClock();
        assertTrue(clock.onItem(1_000));
        assertFalse(clock.onItem(1_400), "same drop");
        assertTrue(clock.onItem(31_000));
        assertEquals(2, clock.drops());
        assertEquals(30_000, clock.meanGapMs());
        assertEquals(0f, DropClock.window(10_000), 1e-6);
        assertEquals(0.5f, DropClock.window(40_000), 1e-6);
        assertEquals(1f, DropClock.window(90_000), 1e-6);
    }

    @Test
    void castleRarities() {
        assertEquals(CastleShulkers.Rarity.COMMON, CastleShulkers.rarity("gray"));
        assertEquals(CastleShulkers.Rarity.RARE, CastleShulkers.rarity("light_blue"));
        assertEquals(CastleShulkers.Rarity.EPIC, CastleShulkers.rarity("purple"));
        assertNull(CastleShulkers.rarity("red"));
        assertNull(CastleShulkers.rarity(null));
        assertEquals(12, CastleShulkers.Rarity.EPIC.breaks);
        assertEquals(4, CastleShulkers.left(CastleShulkers.Rarity.RARE, 3));
        assertEquals(1, CastleShulkers.left(CastleShulkers.Rarity.COMMON, 9));
        assertTrue(CastleShulkers.nearCastle(50, -60, 96));
        assertFalse(CastleShulkers.nearCastle(90, 90, 96));
    }

    @Test
    void pandoraTone() {
        assertEquals("good", PandoraLabels.tone(8_000));
        assertEquals("warn", PandoraLabels.tone(3_000));
        assertEquals("bad", PandoraLabels.tone(1_000));
    }
}
