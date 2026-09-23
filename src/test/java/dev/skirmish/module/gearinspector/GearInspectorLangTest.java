package dev.skirmish.module.gearinspector;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.Setting;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GearInspectorLangTest {
    private static JsonObject lang(String code) throws IOException {
        try (InputStream in = GearInspectorLangTest.class.getResourceAsStream("/assets/skirmish_gearinspector/lang/" + code + ".json")) {
            assertNotNull(in, code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        assertEquals(lang("en_us").keySet(), lang("ru_ru").keySet());
    }

    @Test
    void everySettingAndEnumValueIsTranslated() throws IOException {
        Set<String> keys = lang("en_us").keySet();
        GearInspectorModule module = new GearInspectorModule();
        for (Setting<?> setting : module.settings()) {
            assertTrue(keys.contains(setting.translationKey()), setting.translationKey());
            if (setting instanceof EnumSetting<?> enumSetting) {
                assertEnumValues(keys, enumSetting);
            }
        }
    }

    private static <E extends Enum<E>> void assertEnumValues(Set<String> keys, EnumSetting<E> setting) {
        for (E value : setting.values()) {
            assertTrue(keys.contains(setting.valueTranslationKey(value)), setting.valueTranslationKey(value));
        }
    }

    @Test
    void slotLabelsExist() throws IOException {
        Set<String> keys = lang("en_us").keySet();
        for (String slot : new String[]{"head", "chest", "legs", "feet", "mainhand", "offhand"}) {
            assertTrue(keys.contains("skirmish.gearinspector.slot." + slot), slot);
        }
    }
}
