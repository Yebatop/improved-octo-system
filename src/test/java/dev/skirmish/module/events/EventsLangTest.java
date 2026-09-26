package dev.skirmish.module.events;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.FeatureGate;
import dev.skirmish.setting.Setting;
import dev.skirmish.ui.Theme;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventsLangTest {
    private static JsonObject lang(String code) throws IOException {
        try (InputStream in = EventsLangTest.class.getResourceAsStream("/assets/skirmish_events/lang/" + code + ".json")) {
            assertNotNull(in, code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        assertEquals(lang("en_us").keySet(), lang("ru_ru").keySet());
    }

    @Test
    void everySettingAndValueIsTranslated() throws IOException {
        Set<String> keys = lang("ru_ru").keySet();
        EventsModule module = new EventsModule();
        assertTrue(keys.contains(module.nameKey()));
        assertTrue(keys.contains(module.descriptionKey()));
        for (Setting<?> setting : module.settings()) {
            assertTrue(keys.contains(setting.translationKey()), setting.translationKey());
            assertTrue(keys.contains(setting.translationKey() + ".tooltip"), setting.translationKey());
            if (setting instanceof EnumSetting<?> e) {
                for (Object v : e.values()) {
                    assertTrue(keys.contains(setting.translationKey() + "." + ((Enum<?>) v).name().toLowerCase(java.util.Locale.ROOT)), v.toString());
                }
            }
        }
        for (Rarity r : Rarity.values()) {
            assertTrue(keys.contains("skirmish.events.rarity." + r.key()), r.key());
        }
        for (int d = 1; d <= 7; d++) {
            assertTrue(keys.contains("skirmish.events.weekday." + d));
        }
    }

    @Test
    void featureIdsAreDeclared() {
        new EventsModule();
        assertTrue(FeatureGate.declared().contains("event_hud"));
        assertTrue(FeatureGate.declared().contains("event_waypoints"));
    }

    @Test
    void themeTokensResolve() {
        Theme theme = Theme.get();
        for (Rarity r : Rarity.values()) {
            theme.color(theme.string("events.rarity." + r.key() + ".fg"));
            theme.color(theme.string("events.rarity." + r.key() + ".bg"));
        }
        for (String state : new String[]{"running", "pending", "soon", "countdown"}) {
            theme.color(theme.string("events.state." + state));
        }
        for (String style : new String[]{"event_section", "event_name", "event_sub", "event_coords", "event_time", "event_chip", "event_empty"}) {
            theme.text(style);
        }
        assertTrue(theme.num("layout.events.width") > 0);
    }
}
