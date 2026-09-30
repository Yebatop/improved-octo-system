package dev.skirmish.module.bosscoach;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BossTableTest {
    private static final BossTable TABLE = BossTable.bundled();

    private static String id(String barName) {
        BossTable.Boss boss = TABLE.match(barName);
        return boss == null ? null : boss.id();
    }

    private static BossTable.Boss boss(String id) {
        return TABLE.bosses().stream().filter(b -> b.id().equals(id)).findFirst().orElseThrow();
    }

    @Test
    void bundledTableHasEveryWikiBoss() {
        assertTrue(TABLE.problems().isEmpty(), TABLE.problems().toString());
        assertEquals(16, TABLE.bosses().stream().filter(b -> b.mode().equals("lite")).count(), "lite/events/boss.md lists 16");
        assertEquals(6, TABLE.bosses().stream().filter(b -> b.mode().equals("prime")).count(), "5 Prime bosses + the Ancient City keeper");
        assertEquals(9000, boss("detonator").hp());
        assertEquals(12000, boss("air_lord").hp());
        assertEquals(7000, boss("tough_nuts").hp());
        assertEquals(6000, boss("devilry").hp());
        assertEquals(8000, boss("alien").hp());
        assertEquals(0, boss("keeper").hp(), "the wiki gives no health for the keeper");
    }

    @Test
    void matchesBarNames() {
        assertEquals("detonator", id("§c§lДетонатор §7[Босс]"));
        assertEquals("detonator", id("БОСС  ДЕТОНАТОР"));
        assertEquals("illusionist", id("Иллюзионист"));
        assertEquals("kung_fu", id("Мастер Кунг-фу По"));
        assertEquals("coat_horse", id("Конь в пальто"));
        assertEquals("tough_nuts", id("Железный голем"));
        assertEquals("tough_nuts", id("Медный Голем"));
        assertEquals("devilry", id("Чертовщина"));
        assertEquals("alien", id("Инопланетянин"));
        assertEquals("keeper", id("Зловещий Хранитель"));
        assertEquals("baba_yaga", id("Бабка Яга"));
        assertNull(id("Хранитель опыта"), "the XP keeper NPC is not a boss");
        assertNull(id("ᴘᴠᴘ 15"));
        assertNull(id("Элементный режим"));
        assertNull(id("Кровавая луна 12:00"));
    }

    @Test
    void phasesByHpPercent() {
        BossTable.Boss detonator = boss("detonator");
        assertEquals(-1, BossTable.phaseAt(detonator, 100).index(), "before 90 % nothing is described yet");
        assertEquals(90, BossTable.phaseAt(detonator, 95).phase().from());
        assertEquals(0, BossTable.phaseAt(detonator, 90).index());
        assertEquals(0, BossTable.phaseAt(detonator, 70).index());
        assertEquals(1, BossTable.phaseAt(detonator, 69.9).index());
        assertEquals(2, BossTable.phaseAt(detonator, 12).index());
        assertEquals(3, BossTable.phaseAt(detonator, 4.2).index());
        assertEquals(3, BossTable.phaseAt(detonator, 0).index());

        BossTable.Boss nuts = boss("tough_nuts");
        assertEquals(0, BossTable.phaseAt(nuts, 100).index());
        assertEquals(1, BossTable.phaseAt(nuts, 89.5).index(), "between the wiki's 90 and 89");
        assertEquals(1, BossTable.phaseAt(nuts, 50).index());
        assertEquals(2, BossTable.phaseAt(nuts, 49.5).index());

        assertEquals(1, BossTable.phaseAt(boss("leonardo"), 49).index(), "the turtles come at half HP");
        assertEquals(-1, BossTable.phaseAt(boss("illusionist"), 50).index());
        assertNull(BossTable.phaseAt(boss("illusionist"), 50).phase());
    }

    @Test
    void badPhasesAreReported() {
        BossTable table = BossTable.parse(JsonParser.parseString("""
                {"bosses": [
                  {"id": "x", "names": ["икс"], "phases": [{"from": 20, "to": 60}, {"from": 100, "to": 20}]},
                  {"id": "y"}
                ]}""").getAsJsonObject());
        assertEquals(1, table.bosses().size());
        assertEquals(1, table.bosses().getFirst().phases().size());
        assertEquals(2, table.problems().size(), table.problems().toString());
    }

    private static JsonObject lang(String code) throws IOException {
        try (InputStream in = BossTableTest.class.getResourceAsStream("/assets/skirmish_bosscoach/lang/" + code + ".json")) {
            assertNotNull(in, code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyBossAndPhaseHasText() throws IOException {
        for (String code : List.of("ru_ru", "en_us")) {
            JsonObject lang = lang(code);
            for (BossTable.Boss boss : TABLE.bosses()) {
                String base = "skirmish.bosscoach.boss." + boss.id();
                assertTrue(lang.has(base), code + " " + base);
                assertTrue(lang.has(base + ".tip"), code + " " + base + ".tip");
                for (int i = 1; i <= boss.phases().size(); i++) {
                    assertTrue(lang.has(base + ".phase." + i), code + " " + base + ".phase." + i);
                }
            }
        }
    }
}
