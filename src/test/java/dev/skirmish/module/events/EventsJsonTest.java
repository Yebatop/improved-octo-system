package dev.skirmish.module.events;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Parses the api.holyworld.me samples saved on 2026-09-23 (src/test/resources/events). */
class EventsJsonTest {
    static JsonElement fixture(String name) throws IOException {
        try (InputStream in = EventsJsonTest.class.getResourceAsStream("/events/" + name)) {
            assertNotNull(in, name);
            return JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    @Test
    void servers() throws IOException {
        Map<String, String> servers = EventsJson.servers(fixture("v1_servers.json"));
        assertEquals(39, servers.size());
        assertEquals("ДуоЛайт #17", servers.get("LITE_ANARCHY_17"));
        assertEquals("Лайт (1.20) #2", servers.get("LITE_NEW_ANARCHY_2"));
        assertTrue(EventsJson.servers(null).isEmpty());
    }

    @Test
    void liteEventsAllServers() throws IOException {
        List<EventsJson.LiteEvent> events = EventsJson.liteEvents(fixture("v1_events.json"), "");
        assertEquals(3, events.size());
        EventsJson.LiteEvent container = events.getFirst();
        assertEquals("LITE_NEW_ANARCHY_1", container.serverId());
        assertEquals("ca7e8751-a364-417c-8f3b-2a2c4d4c12f0", container.instanceId());
        assertEquals("CONTAINER", container.id());
        assertEquals("Контейнер", container.name());
        assertEquals(Rarity.LEGENDARY, container.rarity());
        assertEquals(Rarity.EPIC, events.get(1).rarity());
        assertEquals(Rarity.COMMON, events.get(2).rarity());
        assertEquals("normal_desert", events.get(2).rareRaw());
    }

    @Test
    void liveSampleWithRussianRarities() throws IOException {
        List<EventsJson.LiteEvent> events = EventsJson.liteEvents(fixture("v1_events_2026-09-24.json"), "");
        assertEquals(13, events.size());
        for (EventsJson.LiteEvent e : events) {
            assertTrue(e.rarity() != Rarity.UNKNOWN, e.rareRaw());
        }
        assertEquals(Rarity.LEGENDARY, Rarity.parse("Легендарный"));
        assertEquals(Rarity.EPIC, Rarity.parse("Эпический"));
        assertEquals(Rarity.COMMON, Rarity.parse("Обычный"));
        assertEquals(Rarity.LEGENDARY, Rarity.parse("LEGENDARY"));
    }

    @Test
    void liteEventsOneServerArray() throws IOException {
        List<EventsJson.LiteEvent> events = EventsJson.liteEvents(fixture("v1_events_one_server.json"), "LITE_NEW_ANARCHY_2");
        assertEquals(1, events.size());
        assertEquals("LITE_NEW_ANARCHY_2", events.getFirst().serverId());
        assertEquals("Опытный Тыпо", events.getFirst().name());
    }

    @Test
    void brokenEntriesAreSkipped() {
        JsonElement json = JsonParser.parseString("{\"A\":[1,{},{\"id\":\"SHIP\"}],\"B\":\"x\"}");
        List<EventsJson.LiteEvent> events = EventsJson.liteEvents(json, "");
        assertEquals(1, events.size());
        assertEquals("Ship", events.getFirst().name());
        assertEquals(Rarity.UNKNOWN, events.getFirst().rarity());
    }

    @Test
    void primeCurrent() throws IOException {
        List<EventsJson.PrimeEvent> current = EventsJson.primeCurrent(fixture("v2_prime_events_current.json"));
        assertEquals(4, current.size());
        EventsJson.PrimeEvent first = current.getFirst();
        assertEquals("0e771ae6-4b19-4ef2-a3f5-24f75325c89e", first.uuid());
        assertEquals("2", first.server());
        assertEquals("pandora_box", first.plugin());
        assertTrue(first.running());
        assertEquals(Instant.parse("2026-09-23T20:00:00Z"), first.scheduledAt());
        assertNotNull(first.startedAt());
    }

    @Test
    void primeTimetableSorted() throws IOException {
        List<EventsJson.PrimeSlot> slots = EventsJson.primeTimetable(fixture("v2_prime_events_timetable.json"));
        assertEquals(List.of("golden_bob", "bosses", "pandora_box", "ancient_city"), slots.stream().map(EventsJson.PrimeSlot::key).toList());
        assertEquals(Instant.parse("2026-09-23T21:00:00Z"), slots.getFirst().at());
    }

    @Test
    void votings() throws IOException {
        assertTrue(EventsJson.votings(fixture("v1_votings.json"), "").isEmpty());
        List<EventsJson.Voting> votings = EventsJson.votings(fixture("v1_votings_example.json"), "");
        assertEquals(1, votings.size());
        EventsJson.Voting v = votings.getFirst();
        assertEquals("LITE_ANARCHY_46", v.serverId());
        assertEquals(2, v.candidates().size());
        assertEquals("Таинственный корабль", v.candidates().getFirst().name());
        assertEquals(42, v.candidates().getFirst().votes());
        assertEquals(Rarity.EPIC, v.candidates().getFirst().rarity());
        assertEquals(Rarity.COMMON, v.candidates().get(1).rarity());
    }

    @Test
    void rarityTiers() {
        assertEquals(Rarity.LEGENDARY, Rarity.parse("legendary"));
        assertEquals(Rarity.EPIC, Rarity.parse("EPIC"));
        assertEquals(Rarity.RARE, Rarity.parse("rare_plains"));
        assertEquals(Rarity.COMMON, Rarity.parse("normal_desert"));
        assertEquals(Rarity.COMMON, Rarity.parse("default"));
        assertEquals(Rarity.EPIC, Rarity.parse("ship_roskoshni_f"));
        assertEquals(Rarity.UNKNOWN, Rarity.parse("weird"));
        assertEquals(Rarity.UNKNOWN, Rarity.parse(null));
    }

    @Test
    void instanceDiffBaselineThenNew() throws IOException {
        InstanceDiff<EventsJson.LiteEvent> diff = new InstanceDiff<>(EventsJson.LiteEvent::instanceId);
        List<EventsJson.LiteEvent> all = EventsJson.liteEvents(fixture("v1_events.json"), "");
        assertTrue(diff.update(all.subList(0, 2)).isEmpty(), "first update is the baseline");
        assertTrue(diff.update(all.subList(0, 2)).isEmpty());
        List<EventsJson.LiteEvent> fresh = diff.update(all);
        assertEquals(1, fresh.size());
        assertEquals("PARCELS", fresh.getFirst().id());
        diff.update(List.of());
        assertEquals(3, diff.update(all).size(), "events that ended and came back count as new");
        diff.reset();
        assertTrue(diff.update(all).isEmpty());
    }

    @Test
    void knownCoordsExpire() {
        KnownCoords coords = new KnownCoords(1000);
        ChatCoords.Coords c = new ChatCoords.Coords(1, 2, 3, 0, 5);
        coords.put("Контейнер", c, "minecraft:overworld", 0);
        assertNotNull(coords.get("контейнер", 500));
        assertNull(coords.get("Контейнер", 2000));
    }
}
