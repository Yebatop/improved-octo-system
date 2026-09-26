package dev.skirmish.module.analytics;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure helpers of the fight analytics: reach, CPS window, combo, killer guess, time ago, damage types. */
class AnalyticsLogicTest {
    // ---- reach ----

    @Test
    void reachIsDistanceToTheNearestPointOfTheHitbox() {
        // Player hitbox 0.6 × 1.8 at the origin; eye 3 blocks away along x at chest height.
        assertEquals(2.7, ReachMath.distanceToBox(3.0, 1.0, 0, -0.3, 0, -0.3, 0.3, 1.8, 0.3), 1e-9);
        // Diagonal: dx = 2, dz = 1 → √5.
        assertEquals(Math.sqrt(5), ReachMath.distanceToBox(2.3, 1.62, 1.3, -0.3, 0, -0.3, 0.3, 1.8, 0.3), 1e-9);
        // Above the head: only y counts.
        assertEquals(0.5, ReachMath.distanceToBox(0, 2.3, 0, -0.3, 0, -0.3, 0.3, 1.8, 0.3), 1e-9);
        // Eye inside the box.
        assertEquals(0, ReachMath.distanceToBox(0.1, 1.0, 0.1, -0.3, 0, -0.3, 0.3, 1.8, 0.3), 1e-9);
    }

    @Test
    void reachAverageAndMaxSkipUnknownValues() {
        double[] values = {2.5, Double.NaN, 3.1, 2.8};
        assertEquals((2.5 + 3.1 + 2.8) / 3, ReachMath.average(values), 1e-9);
        assertEquals(3.1, ReachMath.max(values), 1e-9);
        assertTrue(Double.isNaN(ReachMath.average(new double[]{Double.NaN})));
        assertTrue(Double.isNaN(ReachMath.max(new double[0])));
    }

    // ---- CPS ----

    @Test
    void clickWindowCountsTheLastSecond() {
        ClickWindow w = new ClickWindow(1_000);
        assertEquals(0, w.count(0));
        for (int i = 0; i < 10; i++) {
            w.click(i * 100L);
        }
        assertEquals(10, w.count(950));
        assertEquals(10.0, w.perSecond(950), 1e-9);
        // At 1000 the click at 0 falls out of (0, 1000].
        assertEquals(9, w.count(1_000));
        assertEquals(5, w.count(1_450));
        assertEquals(0, w.count(3_000));
        assertEquals(900, w.lastMs());
    }

    @Test
    void clickWindowSurvivesMoreClicksThanItsRing() {
        ClickWindow w = new ClickWindow(1_000);
        for (int i = 0; i < ClickWindow.CAPACITY * 3; i++) {
            w.click(i * 10L);
        }
        long last = (ClickWindow.CAPACITY * 3 - 1) * 10L;
        assertEquals(ClickWindow.CAPACITY, w.count(last));
        w.clear();
        assertEquals(0, w.count(last));
        assertEquals(-1, w.lastMs());
    }

    // ---- combo ----

    @Test
    void comboCountsMyHitsUntilIAmHit() {
        ComboCounter c = new ComboCounter(3_000);
        assertEquals(1, c.hit(0));
        assertEquals(2, c.hit(500));
        assertEquals(3, c.hit(900));
        assertEquals(3, c.current(1_000));
        c.taken(1_100);
        assertEquals(0, c.current(1_200));
        assertEquals(1, c.hit(1_300));
        assertEquals(3, c.best());
    }

    @Test
    void comboResetsAfterAPause() {
        ComboCounter c = new ComboCounter(2_000);
        c.hit(0);
        c.hit(1_000);
        assertEquals(2, c.current(2_900));
        assertEquals(0, c.current(3_100));
        assertEquals(1, c.hit(3_200));
        c.setResetAfterMs(0);
        assertEquals(1, c.current(100_000));
        c.reset();
        assertEquals(0, c.best());
    }

    // ---- killer guess ----

    @Test
    void killerGuessPicksTheLatestSwingInRange() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID far = UUID.randomUUID();
        KillerGuess.Candidate best = KillerGuess.best(List.of(
                new KillerGuess.Candidate(a, "A", 3.0, 400),
                new KillerGuess.Candidate(b, "B", 4.0, 100),
                new KillerGuess.Candidate(far, "Far", 12.0, 10)));
        assertNotNull(best);
        assertEquals("B", best.name());
        // Tie on swing age → nearest.
        best = KillerGuess.best(List.of(new KillerGuess.Candidate(a, "A", 3.0, 100), new KillerGuess.Candidate(b, "B", 2.0, 100)));
        assertEquals("B", best.name());
    }

    @Test
    void killerGuessNeedsARecentSwing() {
        UUID a = UUID.randomUUID();
        assertNull(KillerGuess.best(List.of(new KillerGuess.Candidate(a, "A", 2.0, -1))));
        assertNull(KillerGuess.best(List.of(new KillerGuess.Candidate(a, "A", 2.0, KillerGuess.SWING_WINDOW_MS + 1))));
        assertNull(KillerGuess.best(List.of()));
    }

    // ---- time ago ----

    @Test
    void timeAgoPicksTheLargestUnit() {
        assertEquals(new TimeAgo.Value(TimeAgo.Unit.NOW, 0), TimeAgo.of(30_000));
        assertEquals(new TimeAgo.Value(TimeAgo.Unit.NOW, 0), TimeAgo.of(-5));
        assertEquals(new TimeAgo.Value(TimeAgo.Unit.MINUTES, 5), TimeAgo.of(5 * 60_000 + 59_000));
        assertEquals(new TimeAgo.Value(TimeAgo.Unit.HOURS, 2), TimeAgo.of(2 * 3_600_000 + 1));
        assertEquals(new TimeAgo.Value(TimeAgo.Unit.DAYS, 3), TimeAgo.of(3 * 86_400_000L + 7));
    }

    // ---- damage types ----

    @Test
    void damageTypesStripInferenceAndLabel() {
        assertEquals("minecraft:generic", DamageTypes.base("minecraft:generic (inferred)"));
        assertTrue(DamageTypes.inferred("minecraft:generic (inferred)"));
        assertFalse(DamageTypes.inferred("minecraft:arrow"));
        assertEquals("arrow", DamageTypes.path("minecraft:arrow"));
        assertEquals("melee", DamageTypes.label("minecraft:player_attack"));
        assertEquals("fall", DamageTypes.label("minecraft:fall"));
        assertNull(DamageTypes.label("somemod:laser"));
    }

    @Test
    void onlyAttacksBreakTheCombo() {
        assertTrue(DamageTypes.isHit("minecraft:generic", false), "HolyWorld sends hits as generic without a source");
        assertTrue(DamageTypes.isHit("minecraft:player_attack", false));
        assertTrue(DamageTypes.isHit("minecraft:fall", true));
        assertFalse(DamageTypes.isHit("minecraft:fall", false));
        assertFalse(DamageTypes.isHit("minecraft:on_fire", false));
        assertFalse(DamageTypes.isHit("minecraft:magic", false));
        assertTrue(DamageTypes.isHit("somemod:laser", false));
    }

    @Test
    void chatKillerLine() {
        assertEquals("Enemy_3", AnalyticsHub.chatKillerName("▶ Вы были убиты игроком Enemy_3 на координатах -541 53 -193"));
        assertNull(AnalyticsHub.chatKillerName("▶ Вы вошли в режим PVP!"));
    }
}
