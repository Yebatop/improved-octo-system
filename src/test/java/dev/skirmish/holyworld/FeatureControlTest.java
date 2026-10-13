package dev.skirmish.holyworld;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.FeatureGate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureControlTest {
    @AfterEach
    void clear() {
        FeatureGate.setBlocked(Set.of());
    }

    @Test
    void requestMatchesTheDocumentedEnvelope() {
        byte[] body = FeatureProtocol.checkFeaturesRequest("ffffffff-ffff-ffff-ffff-ffffffffffff", List.of("killcam", "freecam"));
        assertEquals('{', body[0], "bare UTF-8 JSON, no length prefix");
        JsonObject root = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
        assertEquals("ffffffff-ffff-ffff-ffff-ffffffffffff", root.get("id").getAsString());
        assertEquals("checkFeatures", root.get("method").getAsString());
        assertEquals("skirmish", root.getAsJsonObject("payload").get("client").getAsString());
        assertEquals(2, root.getAsJsonObject("payload").getAsJsonArray("features").size());
    }

    @Test
    void parsesSuccessErrorAndPush() {
        FeatureProtocol.Response ok = FeatureProtocol.parse(utf8("{\"id\":\"a\",\"ok\":true,\"payload\":{\"blocklist\":[\"freecam\",\"killcam\"]}}"));
        assertTrue(ok.ok());
        assertEquals(List.of("freecam", "killcam"), ok.blocklist());

        FeatureProtocol.Response error = FeatureProtocol.parse(utf8("{\"id\":\"a\",\"ok\":false,\"error\":\"RATE_LIMITED\",\"message\":\"Too many requests\"}"));
        assertFalse(error.ok());
        assertEquals("RATE_LIMITED", error.error());
        assertNull(error.blocklist());

        FeatureProtocol.Response push = FeatureProtocol.parse(utf8("{\"event\":\"blocklistChanged\",\"payload\":{}}"));
        assertTrue(push.isPush());
    }

    @Test
    void toleratesAVarIntLengthPrefix() {
        byte[] json = utf8("{\"id\":\"a\",\"ok\":true,\"payload\":{\"blocklist\":[]}}");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(json.length);
        out.writeBytes(json);
        assertTrue(FeatureProtocol.parse(out.toByteArray()).ok());

        byte[] longJson = utf8("{\"id\":\"a\",\"ok\":true,\"payload\":{\"blocklist\":[\"" + "x".repeat(200) + "\"]}}");
        ByteArrayOutputStream prefixed = new ByteArrayOutputStream();
        prefixed.write((longJson.length & 0x7F) | 0x80);
        prefixed.write(longJson.length >>> 7);
        prefixed.writeBytes(longJson);
        assertEquals(1, FeatureProtocol.parse(prefixed.toByteArray()).blocklist().size());
    }

    @Test
    void recognisesHolyWorldAddresses() {
        assertTrue(HolyWorld.isHolyWorldAddress("mc.holyworld.ru"));
        assertTrue(HolyWorld.isHolyWorldAddress("HolyWorld.me:25565"));
        assertTrue(HolyWorld.isHolyWorldAddress("lite.holyworld.ru."));
        assertFalse(HolyWorld.isHolyWorldAddress("notholyworld.ru"));
        assertFalse(HolyWorld.isHolyWorldAddress("holyworld.ru.evil.com"));
        assertFalse(HolyWorld.isHolyWorldAddress("localhost"));
    }

    enum Mode { A, B, C }

    @Test
    void blockedSettingsHideAndReadAsOff() {
        BoolSetting walls = (BoolSetting) new BoolSetting("walls", true).feature("test_walls");
        EnumSetting<Mode> mode = new EnumSetting<>("mode", Mode.A).valueFeature(Mode.C, "test_c");
        mode.set(Mode.C);
        assertTrue(walls.get());
        assertEquals(Mode.C, mode.get());

        FeatureGate.setBlocked(Set.of("test_walls", "test_c"));
        assertFalse(walls.get());
        assertFalse(walls.isVisible());
        assertEquals("true", walls.toJson().toString(), "the saved value survives the block");
        assertEquals(Mode.A, mode.get());
        assertEquals(List.of(Mode.A, Mode.B), mode.visibleValues());
        assertEquals("\"C\"", mode.toJson().toString());

        FeatureGate.setBlocked(Set.of());
        assertTrue(walls.get());
        assertEquals(Mode.C, mode.get());
        assertTrue(FeatureGate.declared().containsAll(List.of("test_walls", "test_c")));
    }

    private static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
