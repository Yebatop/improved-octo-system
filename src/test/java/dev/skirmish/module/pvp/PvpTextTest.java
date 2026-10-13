package dev.skirmish.module.pvp;

import org.junit.jupiter.api.Test;

import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PvpTextTest {
    @Test
    void normalizeStripsCodesAndMapsSmallCaps() {
        assertEquals("pvp: 15с", PvpText.normalize("§c§lᴘᴠᴘ§r:  §f15с"));
        assertEquals("режим pvp", PvpText.normalize("§x§f§f§0§0§0§0Режим PvP "));
        assertEquals("кт 12", PvpText.normalize("КТ　１２"));
        assertEquals("еще", PvpText.normalize("Ещё"));
    }

    @Test
    void explicitDurations() {
        assertEquals(OptionalInt.of(15), PvpText.explicitSeconds("pvp 15с"));
        assertEquals(OptionalInt.of(15), PvpText.explicitSeconds("pvp 15 сек."));
        assertEquals(OptionalInt.of(15), PvpText.explicitSeconds("combat 15s"));
        assertEquals(OptionalInt.of(9), PvpText.explicitSeconds("до выхода 9 секунд"));
        assertEquals(OptionalInt.of(75), PvpText.explicitSeconds("кт 1:15"));
        assertEquals(OptionalInt.of(65), PvpText.explicitSeconds("кт 1м 5с"));
        assertEquals(OptionalInt.of(120), PvpText.explicitSeconds("кт 2 мин"));
        assertEquals(OptionalInt.empty(), PvpText.explicitSeconds("pvp 15"));
        assertEquals(OptionalInt.empty(), PvpText.explicitSeconds("кт 1:75"));
    }

    @Test
    void bareNumberOnlyWhenUnambiguous() {
        assertEquals(OptionalInt.of(15), PvpText.seconds("pvp: 15"));
        assertEquals(OptionalInt.empty(), PvpText.seconds("pvp 15 из 30"));
        assertEquals(OptionalInt.empty(), PvpText.seconds("анархия #12 -◆-"));
        assertEquals(OptionalInt.empty(), PvpText.seconds("pvp 1.5"));
        assertEquals(OptionalInt.empty(), PvpText.seconds("pvp"));
    }
}
