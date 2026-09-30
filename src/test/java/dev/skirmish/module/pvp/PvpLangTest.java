package dev.skirmish.module.pvp;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.setting.Setting;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PvpLangTest {
    private static JsonObject lang(String code) throws IOException {
        try (InputStream in = PvpLangTest.class.getResourceAsStream("/assets/skirmish_pvp/lang/" + code + ".json")) {
            assertNotNull(in, code);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws IOException {
        assertEquals(lang("en_us").keySet(), lang("ru_ru").keySet());
    }

    @Test
    void everyVisibleStringIsTranslated() throws IOException {
        Set<String> keys = lang("ru_ru").keySet();
        PvpModule module = new PvpModule();
        assertTrue(keys.contains(module.nameKey()));
        assertTrue(keys.contains(module.descriptionKey()));
        for (Setting<?> setting : module.settings()) {
            assertTrue(keys.contains(setting.translationKey()), setting.translationKey());
            assertTrue(keys.contains(setting.translationKey() + ".tooltip"), setting.translationKey());
        }
        for (PvpModule.Source source : PvpModule.Source.values()) {
            String key = "skirmish.pvp.source." + source.name().toLowerCase(Locale.ROOT);
            assertTrue(keys.contains(key), key);
        }
    }
}
