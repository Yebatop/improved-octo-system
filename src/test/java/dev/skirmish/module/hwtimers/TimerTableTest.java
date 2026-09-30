package dev.skirmish.module.hwtimers;

import com.google.gson.JsonParser;
import dev.skirmish.module.hwtimers.TimerTable.TimerDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimerTableTest {
    private static final TimerTable TABLE = TimerTable.bundled();

    private static List<String> ids(String raw) {
        return TABLE.matchText(TimerText.normalize(raw)).stream().map(TimerDef::id).toList();
    }

    @Test
    void bundledTableReadsCleanly() {
        assertTrue(TABLE.problems().isEmpty(), TABLE.problems().toString());
        assertEquals(List.of("stan", "ice_wave", "raid_block", "immortality", "jake_lamp", "snow_lump", "elements"),
                TABLE.timers().stream().map(TimerDef::id).toList());
    }

    @Test
    void wikiDurations() {
        assertEquals(15, TABLE.byId("stan").seconds());
        assertEquals(5, TABLE.byId("ice_wave").seconds());
        assertEquals(300, TABLE.byId("raid_block").seconds());
        assertEquals(3, TABLE.byId("immortality").seconds());
        assertEquals(15, TABLE.byId("jake_lamp").seconds());
        assertEquals(10, TABLE.byId("snow_lump").seconds());
        assertEquals("immortality", TABLE.byId("immortality").totemRune());
        for (TimerDef def : TABLE.timers()) {
            assertFalse(def.wiki().isBlank(), def.id() + " cites the wiki");
        }
    }

    @Test
    void chatMatchingIsTolerant() {
        assertEquals(List.of("stan"), ids("§cВы попали под §lСтан§c!"));
        assertEquals(List.of("stan"), ids("Вас оглушили станом"));
        assertEquals(List.of("stan"), ids("Эндер-жемчуг сейчас заблокирован"));
        assertEquals(List.of("stan"), ids("Нельзя использовать хорус здесь"));
        assertEquals(List.of("ice_wave"), ids("Ледяная Волна!"));
        assertEquals(List.of(), ids("Вас поймала трапка"), "the trap timer was removed");
        assertEquals(List.of("raid_block"), ids("Здесь действует рейд-блок, 3:12"));
        assertEquals(List.of("raid_block"), ids("РЕЙД БЛОК"));
        assertEquals(List.of(), ids("Станция метро"));
        assertEquals(List.of(), ids("Установлен приват"));
        assertEquals(List.of(), ids("Жемчуг: 16"));
    }

    @Test
    void effectSignatures() {
        assertEquals("stan", TABLE.matchEffect("minecraft:slowness", 0, 300).id());
        assertEquals("snow_lump", TABLE.matchEffect("minecraft:slowness", 5, 200).id());
        assertEquals("jake_lamp", TABLE.matchEffect("minecraft:glowing", 0, 299).id());
        assertEquals("minecraft:carved_pumpkin", TABLE.byId("jake_lamp").head());
        assertNull(TABLE.matchEffect("minecraft:slowness", 0, 1800), "a splash potion of slowness is not a Stan");
        assertNull(TABLE.matchEffect("minecraft:slowness", 1, 300));
        assertNull(TABLE.matchEffect("minecraft:speed", 0, 300));
    }

    @Test
    void bossBarStatus() {
        TimerDef elements = TABLE.byId("elements");
        assertTrue(elements.matchesBossBar(TimerText.normalize("§aЭлементный режим")));
        assertFalse(elements.matchesBossBar(TimerText.normalize("Детонатор")));
    }

    @Test
    void brokenEntriesAreSkippedNotFatal() {
        TimerTable table = TimerTable.parse(JsonParser.parseString("""
                {"timers": [
                  {"id": "ok", "seconds": 4, "chat": ["(broken", "fine"], "tone": "purple"},
                  {"id": "ok", "seconds": 9},
                  {"seconds": 1},
                  {"id": "Bad Id!"}
                ]}""").getAsJsonObject());
        assertEquals(1, table.timers().size());
        TimerDef ok = table.timers().getFirst();
        assertEquals(1, ok.chat().size());
        assertEquals("warn", ok.tone());
        assertEquals(4, table.problems().size(), table.problems().toString());
        assertNotNull(table.byId("ok"));
    }
}
