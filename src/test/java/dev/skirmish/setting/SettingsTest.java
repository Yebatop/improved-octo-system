package dev.skirmish.setting;

import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsTest {
    enum Mode { ALPHA, BETA, GAMMA }

    @Test
    void numberIsClampedAndSnapped() {
        NumberSetting setting = new NumberSetting("n", 1.0, 0.5, 2.0, 0.1);
        setting.set(3.0);
        assertEquals(2.0, setting.get());
        setting.set(-1.0);
        assertEquals(0.5, setting.get());
        setting.set(1.234);
        assertEquals(1.2, setting.get());
        setting.set(Double.NaN);
        assertEquals(1.0, setting.get());
    }

    @Test
    void numberSliderMapping() {
        NumberSetting setting = new NumberSetting("n", 10, 0, 100, 5);
        setting.setFromSlider(0.52);
        assertEquals(50, setting.getInt());
        assertEquals(0.5, setting.toSlider(), 1e-9);
        assertEquals("50", setting.format());
        assertTrue(setting.isInteger());
    }

    @Test
    void enumCyclesAndParsesCaseInsensitive() {
        EnumSetting<Mode> setting = new EnumSetting<>("mode", Mode.ALPHA);
        setting.cycle();
        assertEquals(Mode.BETA, setting.get());
        setting.fromJson(new JsonPrimitive("gamma"));
        assertEquals(Mode.GAMMA, setting.get());
        setting.fromJson(new JsonPrimitive("nope"));
        assertEquals(Mode.GAMMA, setting.get());
        setting.cycle();
        assertEquals(Mode.ALPHA, setting.get());
    }

    @Test
    void stringIsTruncatedToMaxLength() {
        StringSetting setting = new StringSetting("s", "", 4, true);
        setting.set("abcdef");
        assertEquals("abcd", setting.get());
        assertTrue(setting.password());
    }

    @Test
    void listenersAndSaveHookOnlyOnRealChange() {
        BoolSetting setting = new BoolSetting("b", false);
        AtomicInteger saves = new AtomicInteger();
        AtomicInteger changes = new AtomicInteger();
        setting.bind("mod", saves::incrementAndGet);
        setting.onChange(v -> changes.incrementAndGet());
        setting.set(false);
        assertEquals(0, saves.get());
        setting.set(true);
        assertEquals(1, saves.get());
        assertEquals(1, changes.get());
        assertEquals("skirmish.module.mod.setting.b", setting.translationKey());
    }

    @Test
    void loadingFromJsonDoesNotScheduleSave() {
        BoolSetting setting = new BoolSetting("b", false);
        AtomicInteger saves = new AtomicInteger();
        setting.bind("mod", saves::incrementAndGet);
        setting.fromJson(new JsonPrimitive(true));
        assertTrue(setting.get());
        assertEquals(0, saves.get());
        setting.fromJson(new JsonPrimitive("not a boolean"));
        assertTrue(setting.get());
    }

    @Test
    void actionIsNotPersisted() {
        AtomicInteger runs = new AtomicInteger();
        ActionSetting action = new ActionSetting("go", runs::incrementAndGet);
        action.run();
        assertEquals(1, runs.get());
        assertFalse(action.isPersisted());
    }
}
