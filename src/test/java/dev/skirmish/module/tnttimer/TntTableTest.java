package dev.skirmish.module.tnttimer;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TntTableTest {
    private static final TntTable TABLE = TntTable.bundled();

    private static String id(String name) {
        TntTable.TntType type = TABLE.identify(TntTable.normalizeName(name), "minecraft:tnt", 80);
        return type == null ? null : type.id();
    }

    @Test
    void bundledTableReadsCleanly() {
        assertTrue(TABLE.problems().isEmpty(), TABLE.problems().toString());
        assertEquals(80, TABLE.vanillaFuseTicks());
        for (TntTable.TntType type : TABLE.types()) {
            assertFalse(type.wiki().isBlank(), type.id());
            assertFalse(type.match().isEmpty(), type.id());
        }
    }

    @Test
    void namesInLatinCyrillicAndSmallCaps() {
        assertEquals("tnt_a", id("Динамит A"));
        assertEquals("tnt_a", id("§6Динамит А"));
        assertEquals("tnt_b", id("Динамит B"));
        assertEquals("tnt_b", id("Динамит В"));
        assertEquals("tnt_b", id("Динамит ʙ"));
        assertEquals("b2", id("Динамит Б2"));
        assertEquals("b2", id("Динамит B2"));
        assertEquals("c4", id("C4 Взрывчатка"));
        assertEquals("c4", id("С4 взрывчатка"));
        assertEquals("blast_wave", id("Разрывная волна"));
        assertEquals("ice_wave", id("Ледяная волна"));
        assertEquals("reliable_stealer", id("Надёжный стиллер"));
        assertEquals("stealer", id("Стиллер"));
        assertEquals("long", id("Удлиненный динамит"));
        assertEquals("long", id("Удлинённый динамит"));
        assertNull(id("Динамит"));
        assertNull(id(""));
        assertNull(id("Динамитный бур"));
    }

    @Test
    void startingFuseTellsTheLongTntApart() {
        assertEquals("long", TABLE.identify("", "minecraft:tnt", 160).id());
        assertEquals("long", TABLE.identify("", "minecraft:tnt", 152).id());
        assertNull(TABLE.identify("", "minecraft:tnt", 80));
        assertNull(TABLE.identify("", "minecraft:tnt", 84));
        assertNull(TABLE.identify("", "minecraft:tnt", 120), "no type with a 6 s fuse");
    }

    @Test
    void blockStateFromAHandEditedTable() {
        TntTable table = TntTable.parse(JsonParser.parseString("""
                {"types": [{"id": "painted", "match": ["краска"], "blocks": ["minecraft:red_wool"], "radius": 6}]}
                """).getAsJsonObject());
        assertEquals("painted", table.identify("", "minecraft:red_wool", 80).id());
        assertEquals(6.0, table.ring(table.types().getFirst()).radius());
    }

    @Test
    void ringsFollowTheWiki() {
        assertEquals(4.0, TABLE.ring(null).radius(), "vanilla TNT");
        assertEquals(12.0, TABLE.ring(TABLE.identify("динамит a", "", 80)).radius(), "3× vanilla");
        assertEquals(40.0, TABLE.ring(TABLE.identify("динамит b", "", 80)).radius(), "10× vanilla");
        TntTable.Ring b2 = TABLE.ring(TABLE.identify("динамит б2", "", 80));
        assertTrue(b2.square());
        assertEquals(12.5, b2.radius(), "a 25-block cube");
        assertNull(TABLE.ring(TABLE.identify("стиллер", "", 80)), "stealers do not blow up terrain");
        assertEquals(2.0, TABLE.ring(TABLE.identify("ледяная волна", "", 80)).radius(), "the ice sphere");
    }
}
