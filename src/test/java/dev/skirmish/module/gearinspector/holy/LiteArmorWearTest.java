package dev.skirmish.module.gearinspector.holy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LiteArmorWearTest {
    @Test
    void wikiFormula() {
        // "шанс не потратить прочность = 47 + (уровень × 10)", Unbreaking V = 97 %.
        assertEquals(0, LiteArmorWear.savePercent(0));
        assertEquals(57, LiteArmorWear.savePercent(1));
        assertEquals(77, LiteArmorWear.savePercent(3));
        assertEquals(97, LiteArmorWear.savePercent(5));
        assertEquals(100, LiteArmorWear.savePercent(6));
        assertEquals(100, LiteArmorWear.savePercent(255));
    }

    @Test
    void hitsLeft() {
        // Undamaged netherite chestplate (592) with Unbreaking V: 592 / 0.03.
        assertEquals(19733, LiteArmorWear.hitsLeft(592, 5));
        assertEquals(363, LiteArmorWear.hitsLeft(363, 0));
        assertEquals(Math.round(100 * 100.0 / 23), LiteArmorWear.hitsLeft(100, 3));
        assertEquals(0, LiteArmorWear.hitsLeft(0, 5));
        assertEquals(-1, LiteArmorWear.hitsLeft(50, 6));
    }
}
