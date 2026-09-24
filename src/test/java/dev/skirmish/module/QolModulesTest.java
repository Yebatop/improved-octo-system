package dev.skirmish.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.camera.CameraComfortModule;
import dev.skirmish.module.camera.LowFireModule;
import dev.skirmish.module.coords.CoordsHudModule;
import dev.skirmish.module.coords.DeathWaypointModule;
import dev.skirmish.module.effects.ArmorHudModule;
import dev.skirmish.module.effects.EffectHudModule;
import dev.skirmish.module.fullbright.FullbrightModule;
import dev.skirmish.module.sprint.ToggleSprintModule;
import dev.skirmish.module.zoom.ZoomModule;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The QoL modules: Feature Control ids, categories, and every visible string in both languages. */
class QolModulesTest {
    private record Case(Module module, String namespace, String featureId, Category category) {
    }

    private static List<Case> cases() {
        return List.of(
                new Case(new ToggleSprintModule(), "skirmish_sprint", "toggle_sprint", Category.UTILITY),
                new Case(new FullbrightModule(), "skirmish_fullbright", "fullbright", Category.VISUAL),
                new Case(new ZoomModule(), "skirmish_zoom", "zoom", Category.VISUAL),
                new Case(new EffectHudModule(), "skirmish_effects", "effect_hud", Category.COMBAT),
                new Case(new ArmorHudModule(), "skirmish_effects", "armor_hud", Category.COMBAT),
                new Case(new CameraComfortModule(), "skirmish_camera", "camera_comfort", Category.VISUAL),
                new Case(new LowFireModule(), "skirmish_camera", "low_fire", Category.VISUAL),
                new Case(new CoordsHudModule(), "skirmish_coords", "coords_hud", Category.WORLD),
                new Case(new DeathWaypointModule(), "skirmish_coords", "death_waypoint", Category.WORLD));
    }

    private static JsonObject lang(String namespace, String code) throws IOException {
        try (InputStream in = QolModulesTest.class.getResourceAsStream("/assets/" + namespace + "/lang/" + code + ".json")) {
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
            assertTrue(c.module().canToggle(), c.module().id());
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        for (String ns : List.of("skirmish_sprint", "skirmish_fullbright", "skirmish_zoom", "skirmish_effects", "skirmish_camera", "skirmish_coords")) {
            assertEquals(lang(ns, "en_us").keySet(), lang(ns, "ru_ru").keySet(), ns);
        }
    }

    @Test
    void everyVisibleStringIsTranslated() throws IOException {
        for (Case c : cases()) {
            var keys = lang(c.namespace(), "ru_ru").keySet();
            Module module = c.module();
            assertTrue(keys.contains(module.nameKey()), module.nameKey());
            assertTrue(keys.contains(module.descriptionKey()), module.descriptionKey());
            for (Setting<?> setting : module.settings()) {
                if (!setting.isVisible()) {
                    continue;
                }
                assertTrue(keys.contains(setting.translationKey()), setting.translationKey());
                assertTrue(keys.contains(setting.translationKey() + ".tooltip"), setting.translationKey());
                if (setting instanceof EnumSetting<?> e) {
                    for (Enum<?> value : e.values()) {
                        assertTrue(keys.contains(valueKey(e, value)), valueKey(e, value));
                    }
                }
                if (setting instanceof KeySetting key) {
                    assertTrue(keys.contains(key.mappingName()), key.mappingName());
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueKey(EnumSetting setting, Enum value) {
        return setting.valueTranslationKey(value);
    }

    @Test
    void themeHasTheHudTokens() {
        Theme theme = Theme.get();
        for (String style : List.of("qol_pill", "qol_effect_name", "qol_effect_time", "qol_durability", "qol_label", "qol_value")) {
            assertNotNull(theme.text(style), style);
        }
        for (String n : List.of("pill_height", "pill_pad_x", "dot", "dot_gap", "effect_icon", "effect_icon_gap", "effect_time_gap",
                "effect_row_gap", "effect_min_width", "armor_tile", "armor_icon", "armor_gap", "armor_bar", "armor_bar_width",
                "armor_bar_gap", "armor_label_gap", "coords_label_gap", "coords_row_gap")) {
            assertTrue(theme.num("layout.qol." + n) > 0, n);
        }
        for (String color : List.of("good", "warn", "bad", "text", "text_2", "track")) {
            theme.color(color);
        }
    }

    @Test
    void mixinConfigsAreListed() throws IOException {
        try (InputStream in = QolModulesTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String config : List.of("skirmish-fullbright.mixins.json", "skirmish-zoom.mixins.json", "skirmish-camera.mixins.json")) {
                assertTrue(text.contains(config), config);
                assertNotNull(QolModulesTest.class.getResourceAsStream("/" + config), config);
            }
        }
    }
}
