package dev.skirmish.module.events;

import dev.skirmish.module.events.ServerParser.Mode;
import dev.skirmish.module.events.ServerParser.ServerRef;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The patterns are guesses until real HolyWorld sidebar/tab/chat captures exist; these pin their behavior. */
class ServerParserTest {
    private static Map<String, String> servers;

    @BeforeAll
    static void load() throws IOException {
        servers = EventsJson.servers(EventsJsonTest.fixture("v1_servers.json"));
    }

    private static String id(String text, boolean bare) {
        ServerRef ref = ServerParser.parse(text, servers, bare, Mode.UNKNOWN, "test");
        return ref == null ? null : ref.apiId();
    }

    @Test
    void exactDisplayNames() {
        assertEquals("LITE_ANARCHY_17", id("§6§lДуоЛайт #17", false));
        assertEquals("LITE_ANARCHY_1", id("Вы на СолоЛайт #1!", false));
        assertEquals("LITE_ANARCHY_12", id("ДуоЛайт #12", false), "#1 must not match #12");
        assertEquals("LITE_NEW_ANARCHY_2", id("Сервер: Лайт (1.20) #2", false));
        assertEquals("LITE_ANARCHY_33", id("КЛАНЛАЙТ #33", false));
    }

    @Test
    void litePatterns() {
        assertEquals("LITE_ANARCHY_17", id("Лайт #17", false));
        assertEquals("LITE_ANARCHY_17", id("Анархия 17", false));
        assertEquals("LITE_ANARCHY_17", id("Лайт-Анархия №17", false));
        assertEquals("LITE_ANARCHY_5", id("Подключение к Lite-Anarchy-5...", false));
        assertEquals("LITE_ANARCHY_40", id("lanarchy40", false));
        assertEquals("LITE_ANARCHY_44", id("Дуолайт #44", false), "servers missing from /v1/servers still parse");
    }

    @Test
    void liteNewAndPrime() {
        assertEquals("LITE_NEW_ANARCHY_3", id("Лайт 1.20 #3", false));
        assertEquals("LITE_NEW_ANARCHY_2", id("1-20L-2", false));
        ServerRef prime = ServerParser.parse("Прайм Анархия #2", servers, false, Mode.UNKNOWN, "test");
        assertEquals(Mode.PRIME, prime.mode());
        assertEquals("2", prime.apiId());
        assertEquals("4", id("ᴘʀɪᴍᴇ 4", false));
        assertEquals("3", id("pr3", false));
    }

    @Test
    void bareNumberOnlyOnTheSidebar() {
        assertNull(id("Игрок #12 написал", false));
        assertEquals("LITE_ANARCHY_12", id("   #12 -◆-", true));
        assertEquals("5", ServerParser.parse("#5", servers, true, Mode.PRIME, "sidebar").apiId());
        assertNull(ServerParser.parse("#5", servers, true, Mode.ALPHA, "sidebar"));
    }

    @Test
    void nothingToFind() {
        assertNull(id("Баланс: 1 250 000", true));
        assertNull(id("", true));
        assertNull(id("Добро пожаловать на HolyWorld!", false));
    }

    @Test
    void modeHints() {
        assertEquals(Mode.PRIME, ServerParser.mode("HolyWorld | ПРАЙМ"));
        assertEquals(Mode.LITE, ServerParser.mode("ʟɪᴛᴇ"));
        assertEquals(Mode.HUB, ServerParser.mode("Лобби"));
        assertEquals(Mode.UNKNOWN, ServerParser.mode("HolyWorld"));
    }

    @Test
    void override() {
        assertNull(ServerParser.parseOverride("  ", servers));
        assertEquals("LITE_ANARCHY_17", ServerParser.parseOverride("17", servers).apiId());
        assertEquals("LITE_ANARCHY_17", ServerParser.parseOverride("#17", servers).apiId());
        assertEquals("LITE_NEW_ANARCHY_2", ServerParser.parseOverride("lite_new_anarchy_2", servers).apiId());
        assertEquals("LITE_NEW_ANARCHY_2", ServerParser.parseOverride("new 2", servers).apiId());
        ServerRef p = ServerParser.parseOverride("p2", servers);
        assertEquals(Mode.PRIME, p.mode());
        assertEquals("2", p.apiId());
        assertEquals("3", ServerParser.parseOverride("Прайм 3", servers).apiId());
        assertEquals("LITE_ANARCHY_30", ServerParser.parseOverride("КланЛайт #30", servers).apiId());
    }
}
