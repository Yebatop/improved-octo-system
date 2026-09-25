package dev.skirmish.module.analytics.dossier;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DossierStoreTest {
    private static final List<String> NETHERITE = List.of("minecraft:netherite_helmet", "minecraft:netherite_chestplate",
            "minecraft:netherite_leggings", "minecraft:netherite_boots", "minecraft:netherite_sword", "");
    private static final List<String> NOTHING = List.of("", "", "", "", "", "");

    private static DossierRecord.FightResult fight(String name, boolean kill, double dealt, List<String> gear, long time) {
        return new DossierRecord.FightResult(name, kill, dealt, 10, kill ? "KILL" : "TIMEOUT", gear, time);
    }

    @Test
    void fightsMergeIntoOneRecord() {
        DossierStore store = new DossierStore(100);
        UUID id = UUID.randomUUID();
        store.recordFight(id, fight("Nick", true, 30.5, NETHERITE, 1_000));
        store.recordFight(id, fight("Nick_Renamed", false, 12, NOTHING, 5_000));
        store.recordDeath(id, "Nick_Renamed", 6_000);
        store.recordFight(id, fight("Nick_Renamed", true, Double.NaN, NOTHING, 9_000));
        DossierRecord r = store.get(id);
        assertNotNull(r);
        assertEquals("Nick_Renamed", r.name());
        assertEquals(3, r.fights());
        assertEquals(2, r.kills());
        assertEquals(1, r.deaths());
        assertEquals(42.5, r.dealt(), 1e-9);
        assertEquals(30, r.taken(), 1e-9);
        assertEquals(9_000, r.lastFightMs());
        assertEquals("KILL", r.lastOutcome());
        assertEquals(NETHERITE, r.gear(), "gear is kept when a later fight saw none");
        assertEquals(1_000, r.firstSeenMs());
    }

    @Test
    void anOlderFightDoesNotMoveTheLastFightBack() {
        DossierStore store = new DossierStore(100);
        UUID id = UUID.randomUUID();
        store.recordFight(id, fight("A", true, 1, NETHERITE, 10_000));
        store.recordFight(id, fight("A", false, 1, NOTHING, 5_000));
        assertEquals(10_000, store.get(id).lastFightMs());
        assertEquals("KILL", store.get(id).lastOutcome());
    }

    @Test
    void deathWithoutAFightCreatesARecord() {
        DossierStore store = new DossierStore(100);
        UUID id = UUID.randomUUID();
        store.recordDeath(id, "Ganker", 500);
        DossierRecord r = store.get(id);
        assertEquals(0, r.fights());
        assertEquals(1, r.deaths());
        assertEquals(-1, r.lastFightMs());
    }

    @Test
    void pruneDropsThePlayersSeenLongestAgo() {
        DossierStore store = new DossierStore(3);
        UUID[] ids = new UUID[5];
        for (int i = 0; i < 5; i++) {
            ids[i] = UUID.randomUUID();
            store.recordFight(ids[i], fight("P" + i, false, 1, NOTHING, 1_000L * (i + 1)));
        }
        assertEquals(3, store.size());
        assertNull(store.get(ids[0]));
        assertNull(store.get(ids[1]));
        assertNotNull(store.get(ids[4]));
        // Seeing an old player again keeps them.
        store.recordDeath(ids[2], "P2", 100_000);
        store.recordFight(UUID.randomUUID(), fight("New", false, 1, NOTHING, 50_000));
        assertNotNull(store.get(ids[2]));
        assertNull(store.get(ids[3]));
    }

    @Test
    void jsonRoundTripAndBoundOnLoad() {
        DossierStore store = new DossierStore(10);
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.recordFight(a, fight("Alpha", true, 20.25, NETHERITE, 2_000));
        store.recordFight(b, fight("Beta", false, 5, NOTHING, 3_000));
        store.recordDeath(b, "Beta", 4_000);
        String json = store.toJson();

        DossierStore loaded = DossierStore.fromJson(json, 10);
        assertEquals(2, loaded.size());
        DossierRecord ra = loaded.get(a);
        assertEquals("Alpha", ra.name());
        assertEquals(1, ra.kills());
        assertEquals(20.3, ra.dealt(), 1e-9, "stored rounded to 0.1");
        assertEquals(NETHERITE, ra.gear());
        assertEquals(1, loaded.get(b).deaths());
        assertEquals(4_000, loaded.get(b).lastSeenMs());

        DossierStore small = DossierStore.fromJson(json, 1);
        assertEquals(1, small.size());
        assertNotNull(small.get(b), "the most recently seen player stays");
    }

    @Test
    void badEntriesAreSkipped() {
        String json = "{\"version\":1,\"players\":[{\"uuid\":\"not-a-uuid\",\"name\":\"X\"},"
                + "{\"uuid\":\"" + UUID.randomUUID() + "\",\"name\":\"Ok\",\"fights\":2}]}";
        DossierStore store = DossierStore.fromJson(json, 10);
        assertEquals(1, store.size());
        assertTrue(store.all().stream().anyMatch(r -> r.name().equals("Ok") && r.fights() == 2));
        assertEquals(0, DossierStore.fromJson("{\"version\":1}", 10).size());
    }
}
