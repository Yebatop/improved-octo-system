package dev.skirmish.module.killfx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KillStreakTest {
    @Test
    void countsUntilMyDeath() {
        KillStreak streak = new KillStreak();
        assertEquals(1, streak.kill("A", 0));
        assertEquals(2, streak.kill("B", 5_000));
        assertEquals(3, streak.kill("A", 10_000));
        streak.death();
        assertEquals(0, streak.current());
        assertEquals(1, streak.kill("C", 20_000));
        assertEquals(3, streak.best());
        streak.reset();
        assertEquals(0, streak.best());
        assertEquals(0, streak.current());
    }

    @Test
    void duplicateReportIsOneKill() {
        KillStreak streak = new KillStreak();
        assertEquals(1, streak.kill("Enemy", 1_000));
        assertEquals(1, streak.kill("enemy", 1_500));
        assertEquals(2, streak.kill("Enemy", 1_000 + KillStreak.DUPLICATE_MS));
    }
}
