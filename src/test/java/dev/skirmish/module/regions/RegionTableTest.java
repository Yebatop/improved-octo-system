package dev.skirmish.module.regions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionTableTest {
    @Test
    void liteAndPrimeDiffer() {
        RegionTable.Type liteGold = RegionTable.lookup(RegionTable.Server.LITE, "minecraft:gold_block");
        RegionTable.Type primeGold = RegionTable.lookup(RegionTable.Server.PRIME, "minecraft:gold_block");
        assertNotNull(liteGold);
        assertNotNull(primeGold);
        assertEquals(7, liteGold.size());
        assertFalse(liteGold.cube());
        assertEquals(21, primeGold.size());
        assertTrue(primeGold.cube());
        assertEquals(31, RegionTable.lookup(RegionTable.Server.LITE, "minecraft:ancient_debris").size());
        assertEquals(55, RegionTable.lookup(RegionTable.Server.PRIME, "minecraft:ancient_debris").size());
    }

    @Test
    void serverSpecificBlocks() {
        assertNull(RegionTable.lookup(RegionTable.Server.PRIME, "minecraft:iron_block"));
        assertNull(RegionTable.lookup(RegionTable.Server.LITE, "minecraft:resin_block"));
        assertEquals(37, RegionTable.lookup(RegionTable.Server.PRIME, "minecraft:waxed_oxidized_copper_block").size());
        assertEquals(55, RegionTable.lookup(RegionTable.Server.PRIME, "minecraft:budding_amethyst").size());
        assertTrue(RegionTable.anyServer("minecraft:emerald_ore"));
        assertFalse(RegionTable.anyServer("minecraft:stone"));
    }

    @Test
    void boundsAreCentredOnTheBlock() {
        RegionTable.Type iron = RegionTable.lookup(RegionTable.Server.LITE, "minecraft:iron_block");
        int[] b = RegionTable.bounds(iron, 10, 64, -3);
        assertArrayEquals(new int[]{8, Integer.MIN_VALUE, -5, 13, Integer.MAX_VALUE, 0}, b);
        RegionTable.Type resin = RegionTable.lookup(RegionTable.Server.PRIME, "minecraft:resin_block");
        int[] c = RegionTable.bounds(resin, 0, 100, 0);
        assertArrayEquals(new int[]{-38, 62, -38, 39, 139, 39}, c);
        assertEquals(77, c[3] - c[0]);
        assertEquals(77, c[4] - c[1]);
    }

    @Test
    void edgeDistance() {
        RegionTable.Type diamond = RegionTable.lookup(RegionTable.Server.LITE, "minecraft:diamond_block");
        int[] b = RegionTable.bounds(diamond, 0, 64, 0); // x, z in [-5, 6)
        assertEquals(5.5, RegionTable.edgeDistance(b, 0.5, 300, 0.5), 1e-9);
        assertEquals(1.0, RegionTable.edgeDistance(b, 5, -40, 0.5), 1e-9);
        assertEquals(-4.0, RegionTable.edgeDistance(b, 10, 64, 0.5), 1e-9);
        assertEquals(-Math.sqrt(18), RegionTable.edgeDistance(b, 9, 64, 9), 1e-9);

        RegionTable.Type gold = RegionTable.lookup(RegionTable.Server.PRIME, "minecraft:gold_block");
        int[] c = RegionTable.bounds(gold, 0, 64, 0); // y in [54, 75)
        assertEquals(2.0, RegionTable.edgeDistance(c, 0.5, 56, 0.5), 1e-9);
        assertEquals(-3.0, RegionTable.edgeDistance(c, 0.5, 78, 0.5), 1e-9);
    }

    @Test
    void sizeText() {
        assertEquals("21×21", RegionTable.sizeText(RegionTable.lookup(RegionTable.Server.LITE, "minecraft:emerald_ore")));
        assertEquals("37×37×37", RegionTable.sizeText(RegionTable.lookup(RegionTable.Server.PRIME, "minecraft:copper_block")));
    }

    @Test
    void visibilityFadesInAndOut() {
        assertEquals(0f, RegionBoundsModule.visibility(1000, 1000, 1000, 20_000, 260, 1200), 1e-6);
        assertEquals(1f, RegionBoundsModule.visibility(2000, 1000, 2000, 20_000, 260, 1200), 1e-6);
        assertEquals(0.5f, RegionBoundsModule.visibility(20_400, 0, 1000, 20_000, 260, 1200), 1e-6);
        assertEquals(0f, RegionBoundsModule.visibility(30_000, 0, 1000, 20_000, 260, 1200), 1e-6);
    }
}
