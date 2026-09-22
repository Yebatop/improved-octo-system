package dev.skirmish.waypoint;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaypointStoreTest {
    private static final String SERVER = "lite.holyworld.ru";
    private static final String OVERWORLD = "minecraft:overworld";
    private static final String NETHER = "minecraft:the_nether";

    @Test
    void contextFiltersByServerAndDimension() {
        WaypointStore store = new WaypointStore();
        store.add("base", 1, 64, 2, OVERWORLD, SERVER, "manual", 0);
        store.add("portal", 5, 70, 5, NETHER, SERVER, "manual", 0);
        store.add("elsewhere", 0, 0, 0, OVERWORLD, "other.server", "manual", 0);
        assertEquals(1, store.forContext(SERVER, OVERWORLD).size());
        assertEquals(2, store.forServer(SERVER).size());
        assertEquals("portal", store.forContext(SERVER, NETHER).getFirst().name());
    }

    @Test
    void jsonRoundTripKeepsSelection(@TempDir Path dir) throws Exception {
        WaypointStore store = new WaypointStore();
        Waypoint base = store.add("base", 1.5, 64, -2.5, OVERWORLD, SERVER, "clanshare:Nick", 42);
        store.select(base.id());
        Path file = dir.resolve("waypoints.json");
        WaypointStore.write(file, store.toJsonString());

        WaypointStore loaded = new WaypointStore();
        assertEquals(0, loaded.load(file));
        Waypoint copy = loaded.byId(base.id());
        assertEquals(base, copy);
        assertEquals(base.id(), loaded.selectedId());
    }

    @Test
    void brokenEntriesAreSkipped() {
        WaypointStore store = new WaypointStore();
        int skipped = store.fromJson(JsonParser.parseString("""
                {"version":1,"selected":"missing","waypoints":[
                  {"id":"a","name":"ok","x":1,"y":2,"z":3,"dimension":"minecraft:overworld","server":"s"},
                  {"id":"b","name":"no coords"}
                ]}"""));
        assertEquals(1, skipped);
        assertEquals(1, store.all().size());
        assertNull(store.selectedId(), "selection of a missing waypoint is dropped");
    }

    @Test
    void removingSelectedClearsSelection() {
        WaypointStore store = new WaypointStore();
        Waypoint w = store.add("x", 0, 0, 0, OVERWORLD, SERVER, "manual", 0);
        store.select(w.id());
        assertTrue(store.remove(w.id()));
        assertNull(store.selected());
    }

    @Test
    void namesAreSanitized() {
        assertEquals("Waypoint", WaypointStore.sanitizeName("  "));
        assertEquals("a?cb", WaypointStore.sanitizeName("a§c\u0007b"));
        assertEquals(WaypointStore.MAX_NAME_LENGTH, WaypointStore.sanitizeName("x".repeat(200)).length());
    }

    @Test
    void idPrefixMustBeUnique() {
        WaypointStore store = new WaypointStore();
        Waypoint a = store.add("a", 0, 0, 0, OVERWORLD, SERVER, "manual", 0);
        assertEquals(a, store.byIdPrefix(a.id().substring(0, 8)));
        assertNull(store.byIdPrefix("zzzzzzzzz"));
    }

    @Test
    void missingFileLoadsEmpty(@TempDir Path dir) throws Exception {
        WaypointStore store = new WaypointStore();
        assertEquals(0, store.load(dir.resolve("none.json")));
        assertTrue(store.all().isEmpty());
        assertTrue(Files.notExists(dir.resolve("none.json")));
    }
}
