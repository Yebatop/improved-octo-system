package dev.skirmish.module.analytics.review;

import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.FightEndReason;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Fight timeline bookkeeping and the numbers of the review screen. */
class FightLogTest {
    private static FightLog log() {
        return new FightLog(1, new Combatant(7, UUID.randomUUID(), "Enemy", true, false), 1_000);
    }

    @Test
    void longestComboIgnoresTotemsAndBreaksOnTheirHits() {
        FightLog log = log();
        log.hit(true, 1_100, 2.5);
        log.hit(true, 1_600, 2.9);
        log.totem(false, 1_700);
        log.hit(true, 2_100, 3.0);
        log.hit(false, 2_300, Double.NaN);
        log.hit(true, 2_800, 2.0);
        log.hit(true, 3_300, 2.2);
        assertEquals(3, FightStats.longestCombo(log.events()));
        assertEquals(5, FightStats.count(log.events(), FightLog.Kind.MY_HIT));
        assertEquals(1, FightStats.count(log.events(), FightLog.Kind.THEIR_TOTEM));
        assertEquals(Boolean.TRUE, FightStats.iHitFirst(log.events()));
        assertEquals(0, FightStats.longestCombo(List.of()));
        assertNull(FightStats.iHitFirst(List.of()));
    }

    @Test
    void reachStatsUseMyHitsOnly() {
        FightLog log = log();
        log.hit(true, 1_100, 2.0);
        log.hit(true, 1_200, Double.NaN);
        log.hit(true, 1_300, 3.0);
        log.hit(false, 1_400, 5.0);
        assertEquals(2.5, FightStats.averageReach(log.events()), 1e-9);
        assertEquals(3.0, FightStats.maxReach(log.events()), 1e-9);
        assertTrue(Double.isNaN(log.events().get(3).reach()), "their hits carry no reach");
    }

    @Test
    void critAfterTheHitMarksIt() {
        FightLog log = log();
        log.hit(true, 1_000, 2.8);
        assertTrue(log.crit(true, 1_050));
        assertTrue(log.events().getFirst().crit());
        // A second crit animation for the same hit does not mark anything else.
        assertFalse(log.crit(true, 1_060));
        assertEquals(1, FightStats.crits(log.events(), true));
        assertEquals(0, FightStats.crits(log.events(), false));
    }

    @Test
    void critBeforeTheHitIsKeptForIt() {
        FightLog log = log();
        assertFalse(log.crit(false, 2_000));
        log.hit(false, 2_100, Double.NaN);
        assertTrue(log.events().getFirst().crit());
        // Too late: not a crit.
        log.crit(true, 3_000);
        log.hit(true, 3_000 + FightLog.CRIT_WINDOW_MS + 1, 2.0);
        assertFalse(log.events().get(1).crit());
    }

    @Test
    void healthDropGoesToTheLatestHitWithinTheWindow() {
        FightLog log = log();
        log.hit(true, 1_000, 2.0);
        assertTrue(log.damage(true, 4.5f, 1_200));
        assertEquals(4.5f, log.events().getFirst().damage());
        assertFalse(log.damage(true, 1f, 1_250), "already has an amount");
        log.hit(true, 2_000, 2.0);
        assertFalse(log.damage(true, 1f, 2_000 + FightLog.DAMAGE_WINDOW_MS + 1));
        assertFalse(log.damage(false, 3f, 2_100), "no hit of theirs");
        assertFalse(log.damage(true, 0f, 2_100));
    }

    @Test
    void axisEndsAtTheDeathOrShortlyAfterTheLastHit() {
        assertEquals(9_000, FightStats.axisEnd(1_000, 9_000, 8_500, true));
        // Timeout 20 s after the last hit: the axis stops one second after it.
        assertEquals(6_000, FightStats.axisEnd(1_000, 25_000, 5_000, false));
        // World change right after the last hit.
        assertEquals(5_200, FightStats.axisEnd(1_000, 5_200, 5_000, false));
        // Never shorter than a second.
        assertEquals(2_000, FightStats.axisEnd(1_000, 1_100, 1_000, true));
    }

    @Test
    void activeDurationStopsAtTheLastHitForATimeout() {
        assertEquals(4_000, FightStats.activeDurationMs(1_000, 25_000, 5_000, false));
        assertEquals(8_000, FightStats.activeDurationMs(1_000, 9_000, 8_500, true));
    }

    @Test
    void outcomes() {
        assertEquals(Outcome.WIN, Outcome.of(FightEndReason.KILL, false));
        assertEquals(Outcome.LOSS, Outcome.of(FightEndReason.OWN_DEATH, true));
        assertEquals(Outcome.DIED, Outcome.of(FightEndReason.OWN_DEATH, false));
        assertEquals(Outcome.SPLIT, Outcome.of(FightEndReason.TIMEOUT, false));
        assertEquals(Outcome.INTERRUPTED, Outcome.of(FightEndReason.WORLD_CHANGE, false));
        assertEquals(Outcome.OPPONENT_DIED, Outcome.of(FightEndReason.OPPONENT_DIED, false));
        assertEquals(Outcome.DEATH, Outcome.of(null, false));
        assertTrue(Outcome.WIN.good());
        assertTrue(Outcome.LOSS.bad());
    }

    @Test
    void timelineIsBounded() {
        FightLog log = log();
        for (int i = 0; i < FightLog.MAX_EVENTS + 10; i++) {
            log.hit(i % 2 == 0, 1_000 + i, 2.0);
        }
        assertEquals(FightLog.MAX_EVENTS, log.events().size());
        assertNull(log.hit(true, 99_999, 2.0));
    }

    // ---- health series ----

    @Test
    void hpSeriesSkipsRepeatsAndKeepsChanges() {
        HpSeries s = new HpSeries(16, 500);
        s.add(0, 20f);
        s.add(50, 20f);
        s.add(100, 16f);
        s.add(150, 16f);
        s.add(700, 16f);
        assertEquals(3, s.size());
        assertEquals(20f, s.max());
        assertEquals(16f, s.at(120));
        assertEquals(20f, s.at(-5), "before the first sample: the first value");
        s.add(600, 1f);
        assertEquals(3, s.size(), "samples out of order are ignored");
    }

    @Test
    void hpSeriesDecimatesWhenFull() {
        HpSeries s = new HpSeries(8, 1);
        for (int i = 0; i < 100; i++) {
            s.add(i * 10L, i % 2 == 0 ? 20f : 10f);
        }
        assertTrue(s.size() <= 8);
        assertEquals(990, s.time(s.size() - 1), "the latest sample survives");
        for (int i = 1; i < s.size(); i++) {
            assertTrue(s.time(i) > s.time(i - 1));
        }
    }

    @Test
    void hpSeriesWindowStartsAtTheValueThen() {
        HpSeries s = new HpSeries(16, 1);
        s.add(0, 20f);
        s.add(1_000, 14f);
        s.add(5_000, 8f);
        HpSeries w = s.window(2_000, 6_000);
        assertEquals(2, w.size());
        assertEquals(2_000, w.time(0));
        assertEquals(14f, w.value(0));
        assertEquals(8f, w.value(1));
    }

    @Test
    void recentHpEndsAtZeroAtTheDeath() {
        RecentHp hp = new RecentHp(10_000);
        for (int i = 0; i <= 300; i++) {
            hp.add(i * 50L, 20f - i * 0.05f);
        }
        HpSeries s = hp.series(15_000 - 10_000, 15_000, 0f);
        assertEquals(5_000, s.time(0));
        assertEquals(0f, s.value(s.size() - 1));
        assertEquals(15_000, s.time(s.size() - 1));
        assertTrue(hp.size() <= 202, "keeps about the window: " + hp.size());
    }

    // ---- damage on me ----

    @Test
    void recentDamageAttachesAmountAndCrit() {
        RecentDamage d = new RecentDamage(15_000);
        UUID enemy = UUID.randomUUID();
        d.add(new DamageTaken(1_000, "Enemy", enemy, "minecraft:generic (inferred)", true));
        assertTrue(d.crit(1_020));
        assertTrue(d.amount(3.5f, 1_200));
        assertFalse(d.amount(1f, 1_300));
        d.crit(5_000);
        d.add(new DamageTaken(5_100, null, null, "minecraft:fall", false));
        assertFalse(d.amount(2f, 5_100 + FightLog.DAMAGE_WINDOW_MS + 1));
        List<DamageTaken> all = d.window(0, 6_000);
        assertEquals(2, all.size());
        assertTrue(all.get(0).crit());
        assertEquals(3.5f, all.get(0).amount());
        assertTrue(all.get(1).crit(), "crit animation just before the damage event");
        assertTrue(Float.isNaN(all.get(1).amount()));
        d.prune(30_000);
        assertEquals(0, d.size());
    }

    @Test
    void deathRecapTotals() {
        UUID killer = UUID.randomUUID();
        DamageTaken a = new DamageTaken(1_000, "K", killer, "minecraft:player_attack", false);
        a.amount = 4f;
        DamageTaken b = new DamageTaken(2_000, null, null, "minecraft:fall", false);
        b.amount = 2.5f;
        DamageTaken c = new DamageTaken(3_000, "K", killer, "minecraft:player_attack", false);
        DeathRecap recap = new DeathRecap(3_100, "K", killer, "", List.of(a, b, c), new HpSeries(4, 1), 20f);
        assertEquals(6.5f, recap.totalDamage(), 1e-6);
        assertEquals(2, recap.hitsByKiller());
        assertNotNull(recap.hp());
    }
}
