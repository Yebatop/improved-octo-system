package dev.skirmish.module.worldmap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapShadeTest {
    @Test
    void landAndWater() {
        assertEquals(MapShade.HIGH, MapShade.land(70, 69));
        assertEquals(MapShade.LOW, MapShade.land(68, 69));
        assertEquals(MapShade.NORMAL, MapShade.land(69, 69));
        assertEquals(MapShade.HIGH, MapShade.water(1, 0, 0));
        assertEquals(MapShade.LOW, MapShade.water(10, 0, 0));
        assertEquals(MapShade.NORMAL, MapShade.water(5, 1, 0));
    }

    @Test
    void tiles() {
        assertEquals(0, MapShade.tileOf(31));
        assertEquals(-1, MapShade.tileOf(-1));
        assertEquals(1, MapShade.tileOf(32));
        assertEquals(511, MapShade.pixelOf(-1));
        assertEquals(0, MapShade.pixelOf(512));
        assertEquals("mc.holyworld.ru", MapShade.safe("mc.holyworld.ru"));
        assertEquals("minecraft_overworld", MapShade.safe("minecraft:overworld"));
    }
}
