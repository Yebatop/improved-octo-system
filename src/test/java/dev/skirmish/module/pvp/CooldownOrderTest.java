package dev.skirmish.module.pvp;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CooldownOrderTest {
    @Test
    void pvpItemsFirstThenById() {
        List<String> groups = new ArrayList<>(List.of("minecraft:goat_horn", "minecraft:nether_star", "custom:b",
                "minecraft:ender_pearl", "custom:a", "minecraft:popped_chorus_fruit"));
        groups.sort(CooldownOrder.BY_GROUP);
        assertEquals(List.of("minecraft:ender_pearl", "minecraft:popped_chorus_fruit", "minecraft:nether_star",
                "custom:a", "custom:b", "minecraft:goat_horn"), groups);
    }

    @Test
    void labelPrecision() {
        assertEquals(4.2, CooldownOrder.shownSeconds(83f), 1e-9);
        assertEquals(4.2, CooldownOrder.shownSeconds(84f), 1e-9);
        assertEquals(0.1, CooldownOrder.shownSeconds(0.5f), 1e-9);
        assertEquals(0.0, CooldownOrder.shownSeconds(-3f), 1e-9);
        assertEquals(12.0, CooldownOrder.shownSeconds(221f), 1e-9);
        assertEquals(1, CooldownOrder.digits(9.9));
        assertEquals(0, CooldownOrder.digits(10.0));
    }
}
