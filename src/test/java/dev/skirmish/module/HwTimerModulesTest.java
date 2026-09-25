package dev.skirmish.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.bosscoach.BossCoachModule;
import dev.skirmish.module.hwtimers.ItemTimersModule;
import dev.skirmish.module.hwtimers.JsonTables;
import dev.skirmish.module.hwtimers.TimerTable;
import dev.skirmish.module.tnttimer.TntTable;
import dev.skirmish.module.tnttimer.TntTimerModule;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.FeatureGate;
import dev.skirmish.setting.Setting;
import dev.skirmish.ui.Theme;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Item timers, TNT timer and boss coach: ids, categories, strings, theme tokens, mixins, default places, tables. */
class HwTimerModulesTest {
    private record Case(Module module, String namespace, String englishName, Category category) {
    }

    private static List<Case> cases() {
        return List.of(
                new Case(new ItemTimersModule(), "skirmish_hwtimers", "Item Timers", Category.COMBAT),
                new Case(new TntTimerModule(), "skirmish_tnttimer", "TNT Timer", Category.WORLD),
                new Case(new BossCoachModule(), "skirmish_bosscoach", "Boss Coach", Category.WORLD));
    }

    private static JsonObject lang(String namespace, String code) throws IOException {
        try (InputStream in = HwTimerModulesTest.class.getResourceAsStream("/assets/" + namespace + "/lang/" + code + ".json")) {
            assertNotNull(in, namespace + " " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void idsFeaturesAndCategories() {
        for (Case c : cases()) {
            assertEquals(c.module().id(), c.module().featureId());
            assertEquals(c.category(), c.module().category(), c.module().id());
        }
        assertEquals(List.of("hw_item_timers", "tnt_timer", "boss_coach"), cases().stream().map(c -> c.module().id()).toList());
        assertTrue(FeatureGate.declared().contains("tnt_timer_radius"));
        assertTrue(FeatureGate.declared().contains("boss_meter"));
    }

    @Test
    void radiusRingIsOffByDefaultAndGated() {
        TntTimerModule tnt = new TntTimerModule();
        Setting<?> ring = tnt.settings().stream().filter(s -> s.id().equals("radius_ring")).findFirst().orElseThrow();
        assertEquals(Boolean.FALSE, ring.defaultValue());
        assertEquals("tnt_timer_radius", ring.featureId());
    }

    @Test
    void englishTitleCaseNamesInBothLanguages() throws IOException {
        for (Case c : cases()) {
            for (String code : List.of("ru_ru", "en_us")) {
                JsonObject lang = lang(c.namespace(), code);
                assertEquals(c.englishName(), lang.get(c.module().nameKey()).getAsString(), code);
            }
        }
        for (String code : List.of("ru_ru", "en_us")) {
            assertEquals("Item Timers", lang("skirmish_hwtimers", code).get("skirmish.hud.element.hw_item_timers").getAsString());
            assertEquals("Boss Coach", lang("skirmish_bosscoach", code).get("skirmish.hud.element.boss_coach").getAsString());
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        for (String ns : List.of("skirmish_hwtimers", "skirmish_tnttimer", "skirmish_bosscoach")) {
            assertEquals(lang(ns, "en_us").keySet(), lang(ns, "ru_ru").keySet(), ns);
        }
    }

    @Test
    void everySettingIsTranslated() throws IOException {
        for (Case c : cases()) {
            var keys = lang(c.namespace(), "ru_ru").keySet();
            assertTrue(keys.contains(c.module().descriptionKey()));
            for (Setting<?> setting : c.module().settings()) {
                assertTrue(keys.contains(setting.translationKey()), setting.translationKey());
                assertTrue(keys.contains(setting.translationKey() + ".tooltip"), setting.translationKey());
                assertFalse(setting instanceof EnumSetting<?>, "no enum values to translate");
            }
        }
    }

    @Test
    void everyTableEntryHasText() throws IOException {
        for (String code : List.of("ru_ru", "en_us")) {
            JsonObject timers = lang("skirmish_hwtimers", code);
            for (TimerTable.TimerDef def : TimerTable.bundled().timers()) {
                assertTrue(timers.has("skirmish.hwtimers.timer." + def.id()), code + " " + def.id());
                assertTrue(timers.has("skirmish.hwtimers.timer." + def.id() + ".note"), code + " " + def.id());
            }
            JsonObject tnt = lang("skirmish_tnttimer", code);
            for (TntTable.TntType type : TntTable.bundled().types()) {
                assertTrue(tnt.has("skirmish.tnttimer.type." + type.id()), code + " " + type.id());
            }
        }
    }

    @Test
    void themeTokens() {
        Theme theme = Theme.get();
        for (String style : List.of("hwt_title", "hwt_time", "hwt_note", "bc_line", "bc_phase", "bc_text", "bc_tip", "bc_meter")) {
            assertNotNull(theme.text(style), style);
        }
        for (String color : List.of("tnt_ring", "bad", "warn", "good", "accent", "track", "panel", "stroke")) {
            theme.color(color);
        }
        for (String n : List.of("default_dx", "max_chips", "chip_width", "chip_pad_x", "chip_pad_y", "chip_gap", "chip_line_gap",
                "chip_accent", "chip_accent_gap", "chip_bar", "chip_bar_gap")) {
            assertTrue(theme.num("layout.hwtimers." + n) > 0, n);
        }
        for (String n : List.of("default_y", "width", "row_gap", "section_gap", "max_lines")) {
            assertTrue(theme.num("layout.bosscoach." + n) > 0, n);
        }
    }

    /**
     * Default places in design px. The chips sit left of the crosshair with their right edge further out than half
     * of the widest centred neighbour (combat tag 256 px above, target card 200 px below). The boss panel starts under
     * three vanilla boss bars: bar n ends 17 + 19n GUI px down, and a design px is at least half a GUI px.
     */
    @Test
    void defaultPlacesStayClearOfNeighbours() {
        Theme theme = Theme.get();
        float halfWidest = Math.max(theme.num("layout.pvp.tag_width"), theme.num("layout.hud.target_width")) / 2f;
        assertTrue(theme.num("layout.hwtimers.default_dx") > halfWidest);
        float threeBarsGui = 17 + 19 * 2;
        assertTrue(theme.num("layout.bosscoach.default_y") * 0.5f > threeBarsGui);
        assertTrue(theme.num("layout.bosscoach.default_y") > 60 + 44, "below the logout banner's top and the waypoint pill");
    }

    @Test
    void mixinConfigsAreListed() throws IOException {
        try (InputStream in = HwTimerModulesTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String config : List.of("skirmish-hwtimers.mixins.json", "skirmish-tnttimer.mixins.json", "skirmish-bosscoach.mixins.json")) {
                assertTrue(text.contains(config), config);
                assertNotNull(HwTimerModulesTest.class.getResourceAsStream("/" + config), config);
            }
        }
    }

    @Test
    void overrideFileWinsAndABrokenOneFallsBack(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir.resolve("tables"));
        Files.writeString(dir.resolve("tables/hw_item_timers.json"), "{\"timers\": [{\"id\": \"mine\", \"seconds\": 7}]}");
        JsonTables.Loaded loaded = JsonTables.load(dir, TimerTable.OVERRIDE_NAME, TimerTable.RESOURCE);
        assertNull(loaded.problem());
        assertEquals(List.of("mine"), TimerTable.parse(loaded.root()).timers().stream().map(TimerTable.TimerDef::id).toList());

        Files.writeString(dir.resolve("tables/hw_item_timers.json"), "{ not json");
        loaded = JsonTables.load(dir, TimerTable.OVERRIDE_NAME, TimerTable.RESOURCE);
        assertNotNull(loaded.problem());
        assertEquals(TimerTable.RESOURCE, loaded.source());
        assertEquals(8, TimerTable.parse(loaded.root()).timers().size());
    }
}
