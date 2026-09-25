package dev.skirmish.module.evtimers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventTimersTest {
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
