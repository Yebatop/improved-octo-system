package dev.skirmish.module.nametag;

import org.junit.jupiter.api.Test;

import static dev.skirmish.module.nametag.NametagPolicy.Show.ALWAYS;
import static dev.skirmish.module.nametag.NametagPolicy.Show.COMBAT;
import static dev.skirmish.module.nametag.NametagPolicy.Show.OPPONENTS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NametagTest {
    @Test
    void invisiblePlayersAreNeverDecorated() {
        for (NametagPolicy.Show mode : NametagPolicy.Show.values()) {
            for (boolean pvp : new boolean[]{false, true}) {
                for (boolean opponent : new boolean[]{false, true}) {
                    assertFalse(NametagPolicy.showHp(true, true, mode, pvp, opponent), mode + " " + pvp + " " + opponent);
                }
            }
        }
        assertFalse(NametagPolicy.decorate(true, true), "no friend color on an invisible player's nametag either");
    }

    @Test
    void onlyWhereVanillaShowsTheNametag() {
        assertFalse(NametagPolicy.showHp(false, false, ALWAYS, true, true));
        assertFalse(NametagPolicy.decorate(false, false));
        assertTrue(NametagPolicy.decorate(true, false));
    }

    @Test
    void showModes() {
        assertTrue(NametagPolicy.showHp(true, false, ALWAYS, false, false));
        assertFalse(NametagPolicy.showHp(true, false, COMBAT, false, false));
        assertTrue(NametagPolicy.showHp(true, false, COMBAT, true, false));
        assertTrue(NametagPolicy.showHp(true, false, COMBAT, false, true));
        assertFalse(NametagPolicy.showHp(true, false, OPPONENTS, true, false));
        assertTrue(NametagPolicy.showHp(true, false, OPPONENTS, false, true));
    }

    @Test
    void values() {
        assertEquals("20", HpText.value(20f, HpText.Units.HP, ','));
        assertEquals("17,5", HpText.value(17.5f, HpText.Units.HP, ','));
        assertEquals("17.3", HpText.value(17.25f, HpText.Units.HP, '.'));
        assertEquals("8,8", HpText.value(17.5f, HpText.Units.HEARTS, ','));
        assertEquals("10", HpText.value(20f, HpText.Units.HEARTS, ','));
        assertEquals("0,1", HpText.value(0.01f, HpText.Units.HP, ','), "alive never reads 0");
        assertEquals("0", HpText.value(0f, HpText.Units.HP, ','));
        assertEquals("0", HpText.value(Float.NaN, HpText.Units.HP, ','));
    }

    @Test
    void absorption() {
        assertEquals("", HpText.absorption(0f, HpText.Units.HP, ','));
        assertEquals("+4", HpText.absorption(4f, HpText.Units.HP, ','));
        assertEquals("+2", HpText.absorption(4f, HpText.Units.HEARTS, ','));
    }

    @Test
    void fractionAndColor() {
        assertEquals(0.5f, HpText.fraction(10f, 20f));
        assertEquals(1f, HpText.fraction(30f, 20f));
        assertEquals(0f, HpText.fraction(5f, 0f));
        int bad = 0xFFFF0000;
        int warn = 0xFFFFFF00;
        int good = 0xFF00FF00;
        assertEquals(bad, HpText.color(0f, bad, warn, good));
        assertEquals(warn, HpText.color(0.5f, bad, warn, good));
        assertEquals(good, HpText.color(1f, bad, warn, good));
        assertEquals(0xFF80FF00, HpText.color(0.75f, bad, warn, good));
    }
}
