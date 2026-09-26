package dev.skirmish.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.damagenumbers.DamageNumbersModule;
import dev.skirmish.module.killfx.KillFxModule;
import dev.skirmish.module.playermenu.PlayerMenuModule;
import dev.skirmish.module.runewindow.RuneWindowModule;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.setting.Setting;
import dev.skirmish.ui.Theme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rune window, damage numbers, kill FX, player menu: Feature Control ids, categories, strings, theme tokens. */
class CombatFxModulesTest {
    private record Case(Module module, String namespace, String featureId, Category category, String englishName) {
    }

    private static List<Case> cases() {
        return List.of(
                new Case(new RuneWindowModule(), "skirmish_rune_window", "rune_window", Category.COMBAT, "Rune Window"),
                new Case(new DamageNumbersModule(), "skirmish_damage_numbers", "damage_numbers", Category.COMBAT, "Damage Numbers"),
                new Case(new KillFxModule(), "skirmish_kill_fx", "kill_fx", Category.VISUAL, "Kill FX"),
                new Case(new PlayerMenuModule(), "skirmish_player_menu", "player_menu", Category.UTILITY, "Player Menu"));
    }

    private static JsonObject lang(String namespace, String code) throws IOException {
        try (InputStream in = CombatFxModulesTest.class.getResourceAsStream("/assets/" + namespace + "/lang/" + code + ".json")) {
            assertNotNull(in, namespace + " " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void featureIdsAndCategories() {
        for (Case c : cases()) {
            assertEquals(c.featureId(), c.module().featureId(), c.module().id());
            assertEquals(c.featureId(), c.module().id());
            assertEquals(c.category(), c.module().category(), c.module().id());
            assertTrue(c.module().canToggle());
        }
    }

    @Test
    void stringsInBothLanguages() throws IOException {
        for (Case c : cases()) {
            JsonObject ru = lang(c.namespace(), "ru_ru");
            JsonObject en = lang(c.namespace(), "en_us");
            assertEquals(en.keySet(), ru.keySet(), c.namespace());
            Module module = c.module();
            // Module and HUD element names are English in every language.
            assertEquals(c.englishName(), ru.get(module.nameKey()).getAsString());
            assertEquals(c.englishName(), en.get(module.nameKey()).getAsString());
            assertTrue(ru.has(module.descriptionKey()));
            String hud = "skirmish.hud.element." + module.id();
            if (ru.has(hud)) {
                assertEquals(c.englishName(), ru.get(hud).getAsString());
                assertEquals(c.englishName(), en.get(hud).getAsString());
            }
            for (Setting<?> setting : module.settings()) {
                assertTrue(ru.has(setting.translationKey()), setting.translationKey());
                assertTrue(ru.has(setting.translationKey() + ".tooltip"), setting.translationKey());
                if (setting instanceof EnumSetting<?> e) {
                    for (Enum<?> value : e.values()) {
                        assertTrue(ru.has(valueKey(e, value)), valueKey(e, value));
                    }
                }
                if (setting instanceof KeySetting key) {
                    assertTrue(ru.has(key.mappingName()), key.mappingName());
                }
                if (setting instanceof ActionSetting) {
                    assertTrue(ru.has(setting.translationKey() + ".button"));
                }
            }
        }
        assertTrue(lang("skirmish_rune_window", "ru_ru").has("skirmish.hud.element.rune_window"));
        assertTrue(lang("skirmish_kill_fx", "ru_ru").has("skirmish.hud.element.kill_fx"));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueKey(EnumSetting setting, Enum value) {
        return setting.valueTranslationKey(value);
    }

    @Test
    void themeTokens() {
        Theme theme = Theme.get();
        for (String style : List.of("rw_title", "rw_name", "rw_value", "dn_number", "kfx_title", "kfx_name", "kfx_chip",
                "pm_title", "pm_sub", "pm_row", "pm_row_hint", "pm_badge", "pm_hint")) {
            assertNotNull(theme.text(style), style);
        }
        for (String color : List.of("rw_invulnerable", "rw_guess", "dn_hit", "dn_crit", "dn_totem", "dn_heal", "dn_outline",
                "kfx_accent", "kfx_chip_fill", "kfx_bolt")) {
            theme.color(color);
        }
        for (String n : List.of("default_dx", "min_width", "pad_y", "stripe", "stripe_gap", "value_gap", "bar", "bar_gap", "row_gap", "restored_ms")) {
            assertTrue(theme.num("layout.rune_window." + n) > 0, n);
        }
        for (String n : List.of("world_scale", "lift", "spread", "rise", "fade_start", "pop_scale", "pop_part")) {
            assertTrue(theme.num("layout.damage_numbers." + n) > 0, n);
        }
        for (String n : List.of("min_width", "pad_x", "pad_y", "chip_gap", "chip_height", "chip_pad_x", "bar", "bar_gap", "bar_width",
                "bolt_ms", "bolt_height")) {
            assertTrue(theme.num("layout.kill_fx." + n) > 0, n);
        }
        for (String n : List.of("width", "pad_x", "pad_y", "header_gap", "row_height", "row_gap", "row_pad_x", "badge", "badge_gap",
                "field_height", "picker_rows")) {
            assertTrue(theme.num("layout.player_menu." + n) > 0, n);
        }
        assertTrue(theme.num("layout.damage_numbers.fade_start") < 1f);
    }

    /**
     * Default places, design px from the screen center: the rune chip sits right of the crosshair (clear of the
     * combat tag above and the target card below); the kill banner's bottom edge is above the survival banner's
     * (which is above the combat tag).
     */
    @Test
    void defaultPlacesStayClearOfNeighbours() {
        Theme theme = Theme.get();
        assertTrue(theme.num("layout.rune_window.default_dx") > 16, "right of the crosshair");
        assertTrue(theme.num("layout.kill_fx.default_dy") < theme.num("layout.awareness.alert_default_dy") - 48,
                "kill banner above the survival banner");
    }

    @Test
    void noMixinConfigNeeded() throws IOException {
        try (InputStream in = CombatFxModulesTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertFalse(text.contains("skirmish-combatfx"), "these modules use Fabric events only");
        }
    }
}
