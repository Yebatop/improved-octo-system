package dev.skirmish.module.survival;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static dev.skirmish.module.survival.SurvivalRules.Alert.ARMOR;
import static dev.skirmish.module.survival.SurvivalRules.Alert.FOOD;
import static dev.skirmish.module.survival.SurvivalRules.Alert.GAPPLES;
import static dev.skirmish.module.survival.SurvivalRules.Alert.LOW_HP;
import static dev.skirmish.module.survival.SurvivalRules.Alert.NO_TOTEM;
import static dev.skirmish.module.survival.SurvivalRules.Alert.PEARLS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SurvivalRulesTest {
    private static final SurvivalRules.Config ALL = new SurvivalRules.Config(true, 8f, true, true, 0.10, true, 6, true, 2,
            true, 2, true);
    private static final Set<SurvivalRules.Alert> NONE = EnumSet.noneOf(SurvivalRules.Alert.class);

    private static SurvivalRules.Snapshot healthy(boolean pvp) {
        return new SurvivalRules.Snapshot(20f, 20, pvp, true, 0.9, 16, 16);
    }

    @Test
    void healthyPlayerHasNoWarnings() {
        assertEquals(NONE, SurvivalRules.evaluate(healthy(false), ALL, NONE));
        assertEquals(NONE, SurvivalRules.evaluate(healthy(true), ALL, NONE));
    }

    @Test
    void lowHealthWithHysteresis() {
        assertEquals(Set.of(LOW_HP), SurvivalRules.evaluate(new SurvivalRules.Snapshot(8f, 20, false, true, 1, 16, 16), ALL, NONE));
        assertEquals(NONE, SurvivalRules.evaluate(new SurvivalRules.Snapshot(9f, 20, false, true, 1, 16, 16), ALL, NONE));
        Set<SurvivalRules.Alert> on = Set.of(LOW_HP);
        assertEquals(on, SurvivalRules.evaluate(new SurvivalRules.Snapshot(9.5f, 20, false, true, 1, 16, 16), ALL, on),
                "stays on until a margin above the threshold");
        assertEquals(NONE, SurvivalRules.evaluate(new SurvivalRules.Snapshot(10f, 20, false, true, 1, 16, 16), ALL, on));
        assertEquals(NONE, SurvivalRules.evaluate(new SurvivalRules.Snapshot(0f, 20, false, true, 1, 16, 16), ALL, NONE),
                "dead: nothing to warn about");
    }

    @Test
    void totemOnlyMattersInPvp() {
        SurvivalRules.Snapshot noTotem = new SurvivalRules.Snapshot(20f, 20, false, false, 1, 16, 16);
        assertEquals(NONE, SurvivalRules.evaluate(noTotem, ALL, NONE));
        SurvivalRules.Snapshot inPvp = new SurvivalRules.Snapshot(20f, 20, true, false, 1, 16, 16);
        assertEquals(Set.of(NO_TOTEM), SurvivalRules.evaluate(inPvp, ALL, NONE));
    }

    @Test
    void armorAndFood() {
        assertEquals(Set.of(ARMOR), SurvivalRules.evaluate(new SurvivalRules.Snapshot(20f, 20, false, true, 0.05, 16, 16), ALL, NONE));
        assertEquals(NONE, SurvivalRules.evaluate(new SurvivalRules.Snapshot(20f, 20, false, true, 0.11, 16, 16), ALL, NONE));
        assertEquals(Set.of(ARMOR), SurvivalRules.evaluate(new SurvivalRules.Snapshot(20f, 20, false, true, 0.11, 16, 16), ALL, Set.of(ARMOR)));
        assertEquals(Set.of(FOOD), SurvivalRules.evaluate(new SurvivalRules.Snapshot(20f, 6, false, true, 1, 16, 16), ALL, NONE));
        assertEquals(NONE, SurvivalRules.evaluate(new SurvivalRules.Snapshot(20f, 7, false, true, 1, 16, 16), ALL, NONE));
        assertEquals(Set.of(FOOD), SurvivalRules.evaluate(new SurvivalRules.Snapshot(20f, 8, false, true, 1, 16, 16), ALL, Set.of(FOOD)));
    }

    @Test
    void suppliesOnlyInPvpByDefault() {
        SurvivalRules.Snapshot empty = new SurvivalRules.Snapshot(20f, 20, false, true, 1, 0, 1);
        assertEquals(NONE, SurvivalRules.evaluate(empty, ALL, NONE));
        SurvivalRules.Snapshot emptyPvp = new SurvivalRules.Snapshot(20f, 20, true, true, 1, 0, 1);
        assertEquals(Set.of(PEARLS, GAPPLES), SurvivalRules.evaluate(emptyPvp, ALL, NONE));
        SurvivalRules.Config always = new SurvivalRules.Config(true, 8f, true, true, 0.10, true, 6, true, 2, true, 2, false);
        assertEquals(Set.of(PEARLS, GAPPLES), SurvivalRules.evaluate(empty, always, NONE));
    }

    @Test
    void disabledWarningsNeverFire() {
        SurvivalRules.Config off = new SurvivalRules.Config(false, 8f, false, false, 0.10, false, 6, false, 2, false, 2, false);
        SurvivalRules.Snapshot worst = new SurvivalRules.Snapshot(1f, 0, true, false, 0.01, 0, 0);
        assertEquals(NONE, SurvivalRules.evaluate(worst, off, NONE));
        assertEquals(EnumSet.allOf(SurvivalRules.Alert.class), SurvivalRules.evaluate(worst, ALL, NONE));
    }

    @Test
    void priorityAndEdges() {
        assertEquals(List.of(LOW_HP, NO_TOTEM, ARMOR, FOOD, PEARLS, GAPPLES), List.of(SurvivalRules.Alert.values()));
        assertTrue(LOW_HP.critical());
        assertTrue(NO_TOTEM.critical());
        assertFalse(PEARLS.critical());
        assertEquals(Set.of(FOOD), SurvivalRules.started(Set.of(LOW_HP), EnumSet.of(LOW_HP, FOOD)));
        assertTrue(SurvivalRules.anyCritical(Set.of(FOOD, NO_TOTEM)));
        assertFalse(SurvivalRules.anyCritical(Set.of(FOOD, ARMOR)));
    }

    @Test
    void customItemIds() {
        assertEquals(List.of("minecraft:ender_eye", "minecraft:golden_carrot", "minecraft:cobweb", "holy:trap"),
                ItemIds.parse("ender_eye, minecraft:golden_carrot  Cobweb;holy:trap, ender_eye"));
        assertEquals(List.of(), ItemIds.parse(""));
        assertEquals(List.of(), ItemIds.parse(null));
        assertEquals(List.of("minecraft:stone"), ItemIds.parse("st@ne, stone, :bad, a:"));
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 20; i++) {
            many.append("item").append(i).append(',');
        }
        assertEquals(ItemIds.MAX, ItemIds.parse(many.toString()).size());
    }
}
