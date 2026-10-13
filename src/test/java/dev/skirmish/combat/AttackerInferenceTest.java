package dev.skirmish.combat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AttackerInferenceTest {
    private static Combatant player(int id, String name) {
        return new Combatant(id, UUID.nameUUIDFromBytes(name.getBytes()), name, true, false);
    }

    @Test
    void myHitNeedsTheSameTargetAndARecentClick() {
        assertTrue(AttackerInference.isMyHit(42, 42, 150));
        assertFalse(AttackerInference.isMyHit(42, 7, 150));
        assertFalse(AttackerInference.isMyHit(42, 42, 2_000));
        assertFalse(AttackerInference.isMyHit(42, -1, 100));
    }

    @Test
    void mostRecentSwingInRangeWins() {
        Combatant a = player(1, "Foe_A");
        Combatant b = player(2, "Foe_B");
        Combatant far = player(3, "Enemy_3");
        Combatant found = AttackerInference.attackerOnMe(List.of(
                new AttackerInference.Candidate(a, 2.5, 400, false),
                new AttackerInference.Candidate(b, 3.0, 90, false),
                new AttackerInference.Candidate(far, 9.0, 10, true)));
        assertEquals(b, found);
    }

    @Test
    void withoutSwingsOnlyASingleFightOpponentCounts() {
        Combatant a = player(1, "Foe_A");
        Combatant b = player(2, "Foe_B");
        assertEquals(a, AttackerInference.attackerOnMe(List.of(
                new AttackerInference.Candidate(a, 2.0, -1, true),
                new AttackerInference.Candidate(b, 2.0, -1, false))));
        assertNull(AttackerInference.attackerOnMe(List.of(
                new AttackerInference.Candidate(a, 2.0, -1, true),
                new AttackerInference.Candidate(b, 2.0, -1, true))));
        assertNull(AttackerInference.attackerOnMe(List.of(new AttackerInference.Candidate(a, 2.0, 5_000, false))));
    }
}
