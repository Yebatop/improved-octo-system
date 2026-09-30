package dev.skirmish.module.nametag;

import org.junit.jupiter.api.Test;

import static dev.skirmish.module.nametag.NametagPolicy.Show.ALWAYS;
import static dev.skirmish.module.nametag.NametagPolicy.Show.COMBAT;
import static dev.skirmish.module.nametag.NametagPolicy.Show.OPPONENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HpPlateTest {
    @Test
    void plateNeedsLineOfSightAndRange() {
        assertTrue(NametagPolicy.showPlate(true, true, false, false, ALWAYS, false, false));
        assertFalse(NametagPolicy.showPlate(false, true, false, true, ALWAYS, true, true), "never through blocks");
        assertFalse(NametagPolicy.showPlate(true, false, false, true, ALWAYS, true, true), "not beyond the range");
    }

    @Test
    void invisiblePlayersOnlyWhenAllowed() {
        assertTrue(NametagPolicy.showPlate(true, true, true, true, ALWAYS, false, false));
        assertFalse(NametagPolicy.showPlate(true, true, true, false, ALWAYS, true, true));
    }

    @Test
    void platesFollowTheShowMode() {
        assertFalse(NametagPolicy.showPlate(true, true, false, false, COMBAT, false, false));
        assertTrue(NametagPolicy.showPlate(true, true, false, false, COMBAT, true, false));
        assertFalse(NametagPolicy.showPlate(true, true, false, false, OPPONENTS, true, false));
        assertTrue(NametagPolicy.showPlate(true, true, false, false, OPPONENTS, false, true));
    }

    @Test
    void trailHoldsThenDrains() {
        HpTrail trail = new HpTrail().update(1f, 0);
        assertEquals(1f, trail.fill(0), 1e-6);
        trail.update(0.6f, 1000);
        assertEquals(1f, trail.trail(1000), 1e-6, "the trail stays at the old value right after the hit");
        assertEquals(1f, trail.trail(1000 + HpTrail.HOLD_MS), 1e-6);
        assertEquals(0.6f, trail.fill(1000 + HpTrail.FILL_MS), 1e-6, "the fill catches up quickly");
        assertEquals(0.6f, trail.trail(1000 + HpTrail.HOLD_MS + HpTrail.DRAIN_MS), 1e-6, "then the trail drains");
        assertTrue(trail.hitFlash(1001) > 0.9f);
        assertEquals(0f, trail.hitFlash(1000 + HpTrail.HOLD_MS), 1e-6);
    }

    @Test
    void combosStackIntoOneTrail() {
        HpTrail trail = new HpTrail().update(1f, 0);
        trail.update(0.8f, 1000);
        trail.update(0.5f, 1100);
        assertEquals(1f, trail.trail(1100), 1e-6, "second hit keeps the trail from the first");
        assertEquals(0.5f, trail.trail(1100 + HpTrail.HOLD_MS + HpTrail.DRAIN_MS), 1e-6);
    }

    @Test
    void healRaisesFillWithoutTrail() {
        HpTrail trail = new HpTrail().update(0.4f, 0);
        trail.update(0.9f, 1000);
        assertEquals(0.9f, trail.fill(1000 + HpTrail.FILL_MS), 1e-6);
        assertEquals(0.9f, trail.trail(1000 + HpTrail.FILL_MS), 1e-6);
    }

    @Test
    void farPlatesGoCompact() {
        assertFalse(HpPlates.compact(10, 16f));
        assertTrue(HpPlates.compact(20, 16f));
    }
}
