package dev.skirmish.module.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EffectTextTest {
    @Test
    void levelIsRomanAndLevelOneIsImplicit() {
        assertEquals("", EffectText.level(0));
        assertEquals("II", EffectText.level(1));
        assertEquals("IV", EffectText.level(3));
        assertEquals("IX", EffectText.level(8));
        assertEquals("XIV", EffectText.level(13));
        assertEquals("CCLVI", EffectText.level(255));
        assertEquals("MMMCMXCIX", EffectText.roman(3999));
        assertEquals("4000", EffectText.roman(4000));
    }

    @Test
    void durationRoundsUpToWholeSeconds() {
        assertEquals("0:00", EffectText.duration(0, false));
        assertEquals("0:01", EffectText.duration(1, false));
        assertEquals("0:01", EffectText.duration(20, false));
        assertEquals("1:35", EffectText.duration(20 * 95, false));
        assertEquals("59:59", EffectText.duration(20 * 3599, false));
        assertEquals("1:00:00", EffectText.duration(20 * 3600, false));
        assertEquals(EffectText.INFINITE, EffectText.duration(-1, false));
        assertEquals(EffectText.INFINITE, EffectText.duration(500, true));
    }

    @Test
    void durabilityPercentAndWear() {
        assertEquals(100, EffectText.percent(1561, 0));
        assertEquals(99, EffectText.percent(1561, 1));
        assertEquals(1, EffectText.percent(1561, 1560));
        assertEquals(0, EffectText.percent(1561, 1561));
        assertEquals(100, EffectText.percent(0, 0));
        assertEquals(0.5, EffectText.remaining(100, 50));
        assertEquals(1.0, EffectText.remaining(0, 5));
        assertEquals(EffectText.Wear.GOOD, EffectText.wear(0.5, 0.15));
        assertEquals(EffectText.Wear.WORN, EffectText.wear(0.2, 0.15));
        assertEquals(EffectText.Wear.CRITICAL, EffectText.wear(0.1, 0.15));
    }
}
