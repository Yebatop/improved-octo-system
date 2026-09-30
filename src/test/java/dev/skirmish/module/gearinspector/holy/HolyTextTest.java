package dev.skirmish.module.gearinspector.holy;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HolyTextTest {
    @Test
    void smallCapsCodesAndCaseAreNormalised() {
        assertEquals("- рюкзак infinity -", HolyText.normalize("- Рюкзак Iɴғɪɴɪᴛʏ -"));
        assertEquals("сфера armortality", HolyText.normalize("Сфера ᴀʀᴍᴏʀᴛᴀʟɪᴛʏ"));
        assertEquals("лопата eternity", HolyText.normalize("Лопата ᴇᴛᴇʀɴɪᴛʏ"));
        assertEquals("шлем infinity", HolyText.normalize("§6§lШлем  §fInfinity "));
        assertEquals("зеленый", HolyText.normalize("Зелёный"));
        assertEquals("", HolyText.normalize(null));
    }

    @Test
    void wordsKeepInnerHyphens() {
        assertEquals(List.of("мега-бур", "i"), HolyText.words("Мега-Бур I"));
        assertEquals(List.of("рюкзак", "infinity"), HolyText.words("- Рюкзак Iɴғɪɴɪᴛʏ -"));
        assertEquals(List.of("макс", "здоровье", "ii"), HolyText.words("• Макс. здоровье II"));
    }

    @Test
    void levelsInRomanAndArabic() {
        assertEquals(1, HolyText.level("I"));
        assertEquals(2, HolyText.level("ii"));
        assertEquals(4, HolyText.level("IV"));
        assertEquals(9, HolyText.level("IX"));
        assertEquals(13, HolyText.level("XIII"));
        assertEquals(7, HolyText.level("7"));
        assertEquals(-1, HolyText.level("IIII"));
        assertEquals(-1, HolyText.level("VX"));
        assertEquals(-1, HolyText.level("0"));
        assertEquals(-1, HolyText.level("ур."));
        assertEquals(-1, HolyText.level(""));
    }

    @Test
    void romanOutput() {
        assertEquals("I", HolyText.roman(1));
        assertEquals("IV", HolyText.roman(4));
        assertEquals("VIII", HolyText.roman(8));
        assertEquals("XIII", HolyText.roman(13));
        assertEquals("40", HolyText.roman(40));
    }
}
