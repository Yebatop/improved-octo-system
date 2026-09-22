package dev.skirmish.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.setting.StringSetting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerTest {
    static final class TestModule extends Module {
        final BoolSetting flag = add(new BoolSetting("flag", false));
        final NumberSetting radius = add(new NumberSetting("radius", 32, 8, 64, 1));
        final StringSetting password = add(new StringSetting("password", "", 64, true));

        TestModule(String id) {
            super(id, true);
        }
    }

    @Test
    void roundTrip(@TempDir Path dir) throws Exception {
        TestModule a = new TestModule("alpha");
        a.setEnabled(false);
        a.debugLog.set(true);
        a.flag.set(true);
        a.radius.set(40.0);
        a.password.set("hunter2");
        ConfigManager writer = new ConfigManager(dir.resolve("config.json"), () -> List.of(a));
        writer.saveNow();

        TestModule b = new TestModule("alpha");
        ConfigManager reader = new ConfigManager(dir.resolve("config.json"), () -> List.of(b));
        reader.load();
        assertFalse(b.isEnabled());
        assertTrue(b.debugLog.get());
        assertTrue(b.flag.get());
        assertEquals(40.0, b.radius.get());
        assertEquals("hunter2", b.password.get());
        assertFalse(reader.isDirty(), "loading must not schedule a save");
    }

    @Test
    void unknownAndInvalidValuesAreIgnored(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.json"), """
                {"version":1,"modules":{
                  "alpha":{"enabled":"yes","settings":{"radius":"far","flag":true,"unknown":5}},
                  "ghost":{"enabled":false}
                }}""");
        TestModule module = new TestModule("alpha");
        new ConfigManager(dir.resolve("config.json"), () -> List.of(module)).load();
        assertTrue(module.isEnabled());
        assertEquals(32.0, module.radius.get());
        assertTrue(module.flag.get());
    }

    @Test
    void brokenFileKeepsDefaultsAndBacksUp(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("config.json"), "{ not json");
        TestModule module = new TestModule("alpha");
        new ConfigManager(dir.resolve("config.json"), () -> List.of(module)).load();
        assertTrue(module.isEnabled());
        assertTrue(Files.exists(dir.resolve("config.json.broken")));
    }

    @Test
    void jsonHasVersionAndEveryModule(@TempDir Path dir) {
        TestModule module = new TestModule("alpha");
        ConfigManager config = new ConfigManager(dir.resolve("config.json"), () -> List.of(module));
        JsonObject json = config.toJson();
        assertTrue(json.getAsJsonObject("modules").has("alpha"));
        assertEquals(1, JsonParser.parseString(json.toString()).getAsJsonObject().get("version").getAsInt());
    }
}
