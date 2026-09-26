package dev.skirmish.module.alerts.parse;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertsParseTest {
    @Test
    void intrusionLine() {
        assertEquals("Griefer_228", IntrusionLine.intruder("Осторожно! В один из ваших регионов вторгся Griefer_228."));
        assertEquals("Steve", IntrusionLine.intruder("§c§lОсторожно! §fВ один из ваших регионов вторгся §eSteve§f."));
        assertEquals("Alex", IntrusionLine.intruder("[Приват] Осторожно! В один из ваших регионов вторглась Alex"));
        assertEquals("abc", IntrusionLine.intruder("осторожно в один из ваших регионов вторгся abc."));
        assertEquals("Nick", IntrusionLine.intruder("Осторожно! В ваш регион вторгся игрок Nick."));
        assertNull(IntrusionLine.intruder("Осторожно! В один из ваших регионов вторгся ."));
        assertNull(IntrusionLine.intruder("Вы вошли в регион spawn"));
        assertNull(IntrusionLine.intruder(""));
    }

    @Test
    void lossItems() {
        assertEquals(LossItems.Kind.SHULKER, LossItems.classify(true, "Шалкеровый ящик", List.of()));
        assertEquals(LossItems.Kind.BACKPACK, LossItems.classify(true, "Рюкзак (I уровень)", List.of()));
        assertEquals(LossItems.Kind.BACKPACK, LossItems.classify(true, "§a- Рюкзак Iɴғɪɴɪᴛʏ -", List.of()));
        assertEquals(LossItems.Kind.BACKPACK, LossItems.classify(true, "Розовый шалкеровый ящик", List.of("§7Рюкзак 2 уровня")));
        assertEquals(LossItems.Kind.ELEMENT, LossItems.classify(false, "§bЭлемент", List.of()));
        assertEquals(LossItems.Kind.ELEMENT, LossItems.classify(false, "Элементы", List.of()));
        assertEquals(LossItems.Kind.NONE, LossItems.classify(false, "Элементная бочка", List.of()));
        assertEquals(LossItems.Kind.NONE, LossItems.classify(false, "Элементный сундук", List.of()));
        assertEquals(LossItems.Kind.NONE, LossItems.classify(false, "Крупица элемента", List.of()));
        assertEquals(LossItems.Kind.NONE, LossItems.classify(false, "Алмаз", List.of()));

        LossItems.Tally t = LossItems.Tally.EMPTY;
        t = LossItems.add(t, LossItems.Kind.SHULKER, 1);
        assertFalse(t.atRisk(), "one shulker is safe");
        t = LossItems.add(t, LossItems.Kind.BACKPACK, 1);
        assertTrue(t.shulkerMode());
        assertEquals(2, t.containers());
        LossItems.Tally e = LossItems.add(LossItems.Tally.EMPTY, LossItems.Kind.ELEMENT, 16);
        assertTrue(e.atRisk(), "a single Element stack is at risk");
        assertEquals(16, e.elements());
    }
}
