package dev.skirmish.module;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.friends.FriendsModule;
import dev.skirmish.module.lag.LagMeterModule;
import dev.skirmish.module.nametag.NametagHpModule;
import dev.skirmish.module.survival.ItemCounterModule;
import dev.skirmish.module.survival.SurvivalAlertsModule;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The awareness modules: Feature Control ids, categories, strings in both languages, theme tokens, mixins. */
class AwarenessModulesTest {
    private record Case(Module module, String namespace, String featureId, Category category) {
    }

    private static List<Case> cases() {
        return List.of(
                new Case(new NametagHpModule(), "skirmish_nametag", "nametag_hp", Category.VISUAL),
                new Case(new FriendsModule(), "skirmish_friends", "friends", Category.UTILITY),
                new Case(new SurvivalAlertsModule(), "skirmish_survival", "survival_alerts", Category.COMBAT),
                new Case(new ItemCounterModule(), "skirmish_survival", "item_counter", Category.COMBAT),
                new Case(new LagMeterModule(), "skirmish_lag", "lag_meter", Category.UTILITY));
    }

    private static JsonObject lang(String namespace, String code) throws IOException {
        try (InputStream in = AwarenessModulesTest.class.getResourceAsStream("/assets/" + namespace + "/lang/" + code + ".json")) {
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
        for (String ns : Set.of("skirmish_nametag", "skirmish_friends", "skirmish_survival", "skirmish_lag")) {
            assertEquals(lang(ns, "en_us").keySet(), lang(ns, "ru_ru").keySet(), ns);
        }
    }

    @Test
    void everySettingIsTranslated() throws IOException {
        for (Case c : cases()) {
            var keys = lang(c.namespace(), "ru_ru").keySet();
            Module module = c.module();
            assertTrue(keys.contains(module.nameKey()), module.nameKey());
            assertTrue(keys.contains(module.descriptionKey()), module.descriptionKey());
            for (Setting<?> setting : module.settings()) {
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
                if (setting instanceof ActionSetting) {
                    assertTrue(keys.contains(setting.translationKey() + ".button"), setting.translationKey());
                }
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueKey(EnumSetting setting, Enum value) {
        return setting.valueTranslationKey(value);
    }

    @Test
    void hudElementsAreNamed() throws IOException {
        assertTrue(lang("skirmish_survival", "ru_ru").has("skirmish.hud.element.survival_alerts"));
        assertTrue(lang("skirmish_survival", "ru_ru").has("skirmish.hud.element.item_counter"));
        assertTrue(lang("skirmish_lag", "ru_ru").has("skirmish.hud.element.lag_meter"));
        assertTrue(lang("skirmish_lag", "ru_ru").has("skirmish.hud.element.lag_warning"));
    }

    @Test
    void themeTokens() {
        Theme theme = Theme.get();
        for (String style : List.of("aw_alert_title", "aw_alert_line", "aw_alert_icon", "aw_count", "aw_lag", "aw_lag_sep")) {
            assertNotNull(theme.text(style), style);
        }
        for (FriendsModule.FriendColor color : FriendsModule.FriendColor.values()) {
            theme.color("friend_" + color.name().toLowerCase(java.util.Locale.ROOT));
        }
        for (String color : List.of("nametag_absorption", "nametag_bar_track", "aw_alert_stroke_bad", "aw_alert_stroke_warn",
                "aw_alert_tint_bad", "aw_alert_tint_warn", "aw_item_veil", "good", "warn", "bad")) {
            theme.color(color);
        }
        for (String n : List.of("alert_min_width", "alert_icon", "alert_icon_gap", "alert_line_gap", "alert_stroke",
                "alert_flash_min", "item_icon", "item_count_gap", "item_gap", "item_row_gap", "item_pad_x", "item_pad_y",
                "lag_height", "lag_pad_x", "lag_dot", "lag_gap", "items_default_x")) {
            assertTrue(theme.num("layout.awareness." + n) > 0, n);
        }
        for (String n : List.of("bar_width", "bar_height", "bar_gap")) {
            assertTrue(theme.num("layout.nametag." + n) > 0, n);
        }
        assertTrue(theme.num("motion.aw_flash_ms") > 0);
        assertEquals("★", theme.string("nametag.friend_marker"));
    }

    /**
     * Default places, in design px from the screen edge/center: the lag pill sits in the bottom-right corner below
     * the session score (bottom edge 70 px up), and the survival banner's bottom edge is above the tallest combat-tag
     * panel (bottom 44 px above the center, up to 136 px tall with four opponents).
     */
    @Test
    void defaultPlacesStayClearOfNeighbours() {
        Theme theme = Theme.get();
        float lagTopFromBottom = -theme.num("layout.awareness.lag_default_y") + theme.num("layout.awareness.lag_height");
        assertTrue(lagTopFromBottom < 70, "lag pill under the session panel");
        float tagTopAboveCenter = 44 + 136;
        assertTrue(-theme.num("layout.awareness.alert_default_dy") > tagTopAboveCenter, "banner above the combat tag");
    }

    @Test
    void mixinConfigsAreListed() throws IOException {
        try (InputStream in = AwarenessModulesTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in);
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String config : List.of("skirmish-nametag.mixins.json", "skirmish-lag.mixins.json")) {
                assertTrue(text.contains(config), config);
                assertNotNull(AwarenessModulesTest.class.getResourceAsStream("/" + config), config);
            }
        }
    }
}
