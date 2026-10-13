package dev.skirmish.module.gearinspector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DurabilityTest {
    private static DamageFacts sent(int max, int damage) {
        return new DamageFacts(false, false, true, max, damage, true);
    }

    @Test
    void emptySlotIsNoData() {
        Durability d = Durability.classify(DamageFacts.EMPTY, true);
        assertEquals(Durability.Kind.NO_DATA, d.kind());
        assertEquals(Durability.Reason.EMPTY_SLOT, d.reason());
        assertEquals("", d.percentText());
        assertEquals(GearFormat.NO_DATA, d.color());
    }

    @Test
    void sentDamageGivesPercent() {
        Durability d = Durability.classify(sent(363, 47), false);
        assertEquals(Durability.Kind.PERCENT, d.kind());
        assertEquals(316, d.remaining());
        assertEquals(363, d.max());
        assertEquals("87%", d.percentText());
        assertTrue(d.describe().contains("damage 47 of 363"));
    }

    @Test
    void damageBeyondMaxIsClampedToZero() {
        Durability d = Durability.classify(sent(100, 250), false);
        assertEquals(0, d.remaining());
        assertEquals("0%", d.percentText());
        assertEquals(GearFormat.RED, d.color());
    }

    @Test
    void unbreakableWinsOverDamage() {
        Durability d = Durability.classify(new DamageFacts(false, true, true, 363, 47, true), false);
        assertEquals(Durability.Kind.UNBREAKABLE, d.kind());
        assertEquals(GearFormat.UNBREAKABLE, d.color());
    }

    @Test
    void itemWithoutDurabilityIsNotDamageable() {
        Durability d = Durability.classify(new DamageFacts(false, false, false, null, null, false), true);
        assertEquals(Durability.Kind.NOT_DAMAGEABLE, d.kind());
        assertFalse(d.hasValue());
    }

    @Test
    void strippedMaxDamageIsNoDataNotNotDamageable() {
        Durability d = Durability.classify(new DamageFacts(false, false, true, null, 0, false), true);
        assertEquals(Durability.Kind.NO_DATA, d.kind());
        assertEquals(Durability.Reason.MAX_DAMAGE_REMOVED, d.reason());
    }

    @Test
    void removedDamageIsNoData() {
        Durability d = Durability.classify(new DamageFacts(false, false, true, 363, null, false), true);
        assertEquals(Durability.Reason.DAMAGE_REMOVED, d.reason());
    }

    @Test
    void damageNotSentIsNoDataUnlessAssumed() {
        DamageFacts facts = new DamageFacts(false, false, true, 363, 0, false);
        Durability strict = Durability.classify(facts, false);
        assertEquals(Durability.Kind.NO_DATA, strict.kind());
        assertEquals(Durability.Reason.DAMAGE_NOT_SENT, strict.reason());

        Durability assumed = Durability.classify(facts, true);
        assertEquals(Durability.Kind.ASSUMED_FULL, assumed.kind());
        assertEquals("100%?", assumed.percentText());
        assertEquals(GearFormat.ASSUMED, assumed.color());
    }

    @Test
    void customDamageableItemWithSentDamage() {
        Durability d = Durability.classify(new DamageFacts(false, false, false, 50, 25, true), false);
        assertEquals(Durability.Kind.PERCENT, d.kind());
        assertEquals("50%", d.percentText());
    }

    @Test
    void invalidMaxDamageIsNoData() {
        Durability d = Durability.classify(new DamageFacts(false, false, true, 0, 0, true), false);
        assertEquals(Durability.Reason.INVALID_MAX_DAMAGE, d.reason());
    }

    @Test
    void percentRoundsDownAndMarksSlivers() {
        assertEquals("100%", GearFormat.percentText(363, 363));
        assertEquals("99%", GearFormat.percentText(362, 363));
        assertEquals("<1%", GearFormat.percentText(1, 363));
        assertEquals("0%", GearFormat.percentText(0, 363));
        assertEquals("0%", GearFormat.percentText(5, 0));
        assertEquals(50, GearFormat.percent(1, 2));
    }

    @Test
    void gradientGoesRedYellowGreen() {
        assertEquals(GearFormat.RED, GearFormat.gradient(0));
        assertEquals(GearFormat.YELLOW, GearFormat.gradient(0.5));
        assertEquals(GearFormat.GREEN, GearFormat.gradient(1));
        assertEquals(GearFormat.GREEN, GearFormat.gradient(2));
        assertEquals(GearFormat.RED, GearFormat.gradient(Double.NaN));
        int quarter = GearFormat.gradient(0.25);
        assertEquals(0xFF, quarter >>> 24);
        assertEquals(0xFF, quarter >>> 16 & 0xFF);
        assertEquals(0xAA, quarter >>> 8 & 0xFF);
    }

    @Test
    void backgroundAndDistance() {
        assertEquals(0, GearFormat.background(0));
        assertEquals(0xFF000000, GearFormat.background(100));
        assertEquals(0x80000000, GearFormat.background(50));
        assertEquals("7.4", GearFormat.distance(7.42));
    }

    @Test
    void enchantmentStatus() {
        assertEquals(EnchantmentStatus.NOT_APPLICABLE, EnchantmentStatus.classify(true, 0, true, true));
        assertEquals(EnchantmentStatus.LISTED, EnchantmentStatus.classify(false, 2, false, false));
        assertEquals(EnchantmentStatus.GLINT_ONLY, EnchantmentStatus.classify(false, 0, true, true));
        assertEquals(EnchantmentStatus.NO_DATA, EnchantmentStatus.classify(false, 0, true, false));
        assertEquals(EnchantmentStatus.NOT_APPLICABLE, EnchantmentStatus.classify(false, 0, false, false));
    }

    /** HolyWorld sends other players' armour as "x64" stacks with damage 123 of any max (captured 2026-09). */
    @Test
    void placeholderStackHasNoDurability() {
        Durability d = Durability.classify(new DamageFacts(false, false, true, 240, 123, true, true), false);
        assertEquals(Durability.Kind.NO_DATA, d.kind());
        assertEquals(Durability.Reason.PLACEHOLDER, d.reason());
    }

    @Test
    void compactEnchantCount() {
        assertEquals(4, GearFormat.enchantCount(EnchantmentStatus.LISTED, 3, 1));
        // Vanilla names only count when they were received; lore enchantments always do.
        assertEquals(2, GearFormat.enchantCount(EnchantmentStatus.GLINT_ONLY, 5, 2));
        assertEquals(0, GearFormat.enchantCount(EnchantmentStatus.NO_DATA, 0, 0));
        assertEquals(0, GearFormat.enchantCount(EnchantmentStatus.NOT_APPLICABLE, 0, 0));
    }
}
