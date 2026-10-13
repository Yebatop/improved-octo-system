package dev.skirmish.module.market;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.Module;
import dev.skirmish.module.alerts.AlertsModule;
import dev.skirmish.setting.Setting;
import dev.skirmish.ui.Theme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Lang files and theme tokens of the market and alerts modules. */
class MarketAlertsResourcesTest {
    private static JsonObject lang(String namespace, String code) throws IOException {
        try (InputStream in = MarketAlertsResourcesTest.class.getResourceAsStream("/assets/" + namespace + "/lang/" + code + ".json")) {
            assertNotNull(in, namespace + " " + code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void languagesCoverEverySetting() throws IOException {
        check("skirmish_market", new MarketModule());
        check("skirmish_alerts", new AlertsModule());
    }

    private static void check(String namespace, Module module) throws IOException {
        Set<String> en = lang(namespace, "en_us").keySet();
        Set<String> ru = lang(namespace, "ru_ru").keySet();
        // Russian adds ".few" plural forms; everything English has must exist in Russian.
        assertTrue(ru.containsAll(en), namespace + ": ru_ru misses " + en.stream().filter(k -> !ru.contains(k)).toList());
        for (String key : ru) {
            assertTrue(en.contains(key) || key.endsWith(".few"), namespace + ": en_us misses " + key);
        }
        List<String> base = List.of(module.nameKey(), module.descriptionKey());
        for (String key : base) {
            assertTrue(en.contains(key), key);
        }
        for (Setting<?> setting : module.settings()) {
            assertTrue(en.contains(setting.translationKey()), setting.translationKey());
            assertTrue(en.contains(setting.translationKey() + ".tooltip"), setting.translationKey() + ".tooltip");
        }
    }

    @Test
    void featureIds() {
        MarketModule market = new MarketModule();
        assertTrue(market.settings().stream().anyMatch(s -> "auction_helper".equals(s.featureId())));
        AlertsModule alerts = new AlertsModule();
        assertTrue(alerts.settings().stream().anyMatch(s -> "logout_warning".equals(s.featureId())));
        assertTrue(alerts.settings().stream().anyMatch(s -> "region_alerts".equals(s.featureId())));
    }

    @Test
    void themeTokensExist() {
        Theme t = Theme.get();
        assertDoesNotThrow(() -> {
            for (String color : List.of("market_chip", "market_chip_cheap", "market_chip_dear", "market_best", "market_best_fill",
                    "alert_warn_stroke", "alert_bad_stroke")) {
                t.color(color);
            }
            for (String style : List.of("market_chip", "market_chip_cheap", "market_chip_dear", "alert_title", "alert_body",
                    "alert_meta", "alert_history", "alert_icon", "confirm_line")) {
                t.text(style);
            }
            for (String num : List.of("market.chip_pad_x", "market.chip_height", "market.chip_offset", "market.best_pad",
                    "market.best_width", "alerts.banner_width", "alerts.toast_width", "alerts.icon", "alerts.icon_gap",
                    "alerts.line_gap", "alerts.history_gap", "alerts.history_row_gap", "alerts.toast_ms",
                    "alerts.confirm_width", "alerts.confirm_line_gap", "alerts.confirm_icon")) {
                t.num("layout." + num);
            }
        });
    }
}
