package dev.skirmish.module.damagenumbers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DamageNumbersLogicTest {
    @Test
    void changesInOneMomentMerge() {
        NumberField field = new NumberField();
        field.add(1, NumberField.Kind.HIT, 3, 0, 0, 0, 0, 150);
        field.add(1, NumberField.Kind.CRIT, 1.5, 5, 5, 5, 100, 150);
        assertEquals(1, field.live().size());
        NumberField.Num n = field.live().getFirst();
        assertEquals(4.5, n.amount, 1e-9);
        assertEquals(NumberField.Kind.CRIT, n.kind);
        assertEquals(0, n.x, 1e-9); // keeps its place
        assertEquals("4.5", n.text());
        // Later hits and other entities get their own number; heals never merge into damage.
        field.add(1, NumberField.Kind.HIT, 2, 0, 0, 0, 600, 150);
        field.add(2, NumberField.Kind.HIT, 2, 0, 0, 0, 600, 150);
        field.add(1, NumberField.Kind.HEAL, 1, 0, 0, 0, 610, 1_500);
        assertEquals(4, field.live().size());
        field.add(1, NumberField.Kind.HEAL, 0.5, 0, 0, 0, 1_500, 1_500);
        assertEquals(4, field.live().size());
        assertEquals("+1.5", field.live().getLast().text());
    }

    @Test
    void lateCritOrTotemRecolors() {
        NumberField field = new NumberField();
        field.add(7, NumberField.Kind.HIT, 6, 0, 0, 0, 1_000, 150);
        assertTrue(field.upgrade(7, NumberField.Kind.CRIT, 1_100, 400));
        assertEquals(NumberField.Kind.CRIT, field.live().getFirst().kind);
        assertTrue(field.upgrade(7, NumberField.Kind.TOTEM, 1_200, 1_000));
        // A weaker kind never downgrades.
        assertTrue(field.upgrade(7, NumberField.Kind.CRIT, 1_300, 400));
        assertEquals(NumberField.Kind.TOTEM, field.live().getFirst().kind);
        assertFalse(field.upgrade(7, NumberField.Kind.CRIT, 5_000, 400));
        assertFalse(field.upgrade(8, NumberField.Kind.CRIT, 1_100, 400));
    }

    @Test
    void lifetimeCapAndRemoval() {
        NumberField field = new NumberField();
        field.add(1, NumberField.Kind.HIT, 1, 0, 0, 0, 0, 0);
        field.add(2, NumberField.Kind.HIT, 1, 0, 0, 0, 500, 0);
        assertEquals(0.5f, field.live().getFirst().age(600, 1_200), 1e-6);
        field.prune(1_200, 1_200);
        assertEquals(1, field.live().size());
        field.removeEntity(2);
        assertTrue(field.isEmpty());
        for (int i = 0; i < NumberField.MAX + 10; i++) {
            field.add(i, NumberField.Kind.HIT, 1, 0, 0, 0, i, 0);
        }
        assertEquals(NumberField.MAX, field.live().size());
        assertEquals(10, field.live().getFirst().entityId);
    }

    @Test
    void formatting() {
        assertEquals("4", NumberField.format(4));
        assertEquals("4.5", NumberField.format(4.5));
        assertEquals("12", NumberField.format(12.04));
        assertEquals("0.3", NumberField.format(0.25));
        assertEquals("1", NumberField.format(0.96));
    }

    @Test
    void healthSamples() {
        DropRules rules = new DropRules();
        assertTrue(Float.isNaN(rules.sample(1, 20)));
        assertEquals(-4f, rules.sample(1, 16), 1e-6);
        assertEquals(0f, rules.sample(1, 16.01f), 1e-6);
        assertEquals(4f, rules.sample(1, 20.01f), 1e-3);
        rules.forget(1);
        assertTrue(Float.isNaN(rules.sample(1, 5)));
        rules.retain(id -> false);
        assertEquals(0, rules.size());
    }

    @Test
    void attributionRules() {
        assertEquals(NumberField.Kind.TOTEM, DropRules.kindOfDrop(1_000, 900, 950));
        assertEquals(NumberField.Kind.CRIT, DropRules.kindOfDrop(1_000, 900, -1));
        assertEquals(NumberField.Kind.HIT, DropRules.kindOfDrop(1_000, 100, -1));
        assertEquals(NumberField.Kind.HIT, DropRules.kindOfDrop(1_000, -1, -1));

        assertTrue(DropRules.showDrop(false, 1_000, -1));
        assertTrue(DropRules.showDrop(true, 1_000, 500));
        assertFalse(DropRules.showDrop(true, 1_000, -1));
        assertFalse(DropRules.showDrop(true, 5_000, 1_000));

        assertFalse(DropRules.showHeal(false, false, 1_000, 900));
        assertTrue(DropRules.showHeal(true, false, 1_000, -1));
        assertTrue(DropRules.showHeal(true, true, 10_000, 1_000));
        assertFalse(DropRules.showHeal(true, true, 20_000, 1_000));
    }
}
