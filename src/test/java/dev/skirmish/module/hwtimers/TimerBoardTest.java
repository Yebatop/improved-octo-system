package dev.skirmish.module.hwtimers;

import dev.skirmish.module.hwtimers.TimerBoard.Chip;
import dev.skirmish.module.hwtimers.TimerBoard.Source;
import dev.skirmish.module.hwtimers.TimerTable.TimerDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerBoardTest {
    private static final TimerTable TABLE = TimerTable.bundled();
    private static final TimerDef STAN = TABLE.byId("stan");
    private static final TimerDef RAID = TABLE.byId("raid_block");
    private static final TimerDef OPEN = TABLE.byId("elements");

    @Test
    void startsWithTheTableLength() {
        TimerBoard board = new TimerBoard();
        Chip chip = board.start(STAN, 1_000, null, Source.CHAT, true, false);
        assertEquals(16_000, chip.endMs());
        assertEquals(15_000, chip.remainingMs(1_000));
        assertEquals(0.5f, chip.fraction(8_500), 1e-4);
        assertTrue(chip.guess());
    }

    @Test
    void laterEndWinsAndAConfirmedSourceClearsTheGuess() {
        TimerBoard board = new TimerBoard();
        board.start(STAN, 0, null, Source.CHAT, true, false);
        Chip chip = board.start(STAN, 5_000, 14_000L, Source.EFFECT, false, false);
        assertEquals(19_000, chip.endMs());
        assertFalse(chip.guess());
        // A shorter, non-exact trigger does not cut the timer.
        board.start(STAN, 6_000, 1_000L, Source.CHAT, true, false);
        assertEquals(19_000, board.get("stan").endMs());
    }

    @Test
    void namedTimeLeftReplacesTheEnd() {
        TimerBoard board = new TimerBoard();
        board.start(RAID, 0, null, Source.EXPLOSION, true, false);
        Chip chip = board.start(RAID, 10_000, 192_000L, Source.CHAT, true, true);
        assertEquals(202_000, chip.endMs());
        // The ring keeps the full 5:00 as its total: 3:12 left is 64 %.
        assertEquals(0.64f, chip.fraction(10_000), 1e-3);
    }

    @Test
    void effectTimeLeftShowsAsAPartlyUsedCountdown() {
        TimerBoard board = new TimerBoard();
        Chip chip = board.start(STAN, 0, 12_000L, Source.EFFECT, true, true);
        assertEquals(12_000, chip.endMs());
        assertEquals(0.8f, chip.fraction(0), 1e-4);
    }

    @Test
    void openEndedTimersCountUpUntilTheirBound() {
        TimerBoard board = new TimerBoard();
        Chip chip = board.start(OPEN, 0, null, Source.BOSS_BAR, true, false);
        assertFalse(chip.countsDown());
        assertEquals(1f, chip.fraction(30_000));
        assertEquals(30_000, chip.elapsedMs(30_000));
        board.expire(59_999);
        assertEquals(1, board.active(59_999).size());
        board.expire(60_000);
        assertNull(board.get("elements"));
    }

    @Test
    void activeOrderIsSoonestFirstThenOpenEnded() {
        TimerBoard board = new TimerBoard();
        board.start(OPEN, 0, null, Source.BOSS_BAR, true, false);
        board.start(RAID, 0, null, Source.EXPLOSION, true, false);
        board.start(STAN, 0, null, Source.CHAT, true, false);
        List<String> order = board.active(1_000).stream().map(Chip::id).toList();
        assertEquals(List.of("stan", "raid_block", "elements"), order);
    }

    @Test
    void holdKeepsAStatusAlive() {
        TimerBoard board = new TimerBoard();
        TimerDef elements = TABLE.byId("elements");
        board.hold(elements, 0, 1_500, Source.BOSS_BAR, true);
        board.hold(elements, 1_000, 2_500, Source.BOSS_BAR, true);
        board.expire(2_000);
        assertEquals(0, board.get("elements").startMs());
        board.expire(2_500);
        assertNull(board.get("elements"));
    }

    @Test
    void trackedBlocksEndTheChipWhenMostlyGone() {
        TimerBoard board = new TimerBoard();
        Chip chip = board.start(OPEN, 0, null, Source.BLOCKS, true, false);
        board.track(chip, List.of(1L, 2L, 3L, 3L, 4L, 5L, 6L));
        assertEquals(6, chip.trackedPositions());
        assertFalse(TimerBoard.mostlyGone(2, 6));
        assertTrue(TimerBoard.mostlyGone(1, 6));
        assertTrue(TimerBoard.mostlyGone(0, 1));
        assertFalse(TimerBoard.mostlyGone(0, 0));
    }
}
