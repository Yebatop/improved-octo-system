package dev.skirmish.module.friends;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FriendListTest {
    private static final UUID A = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID B = UUID.fromString("66666666-7777-8888-9999-000000000000");

    @Test
    void addMatchesByNameIgnoringCase() {
        FriendList list = FriendList.EMPTY.add("Notch_", null);
        assertTrue(list.contains(null, "notch_"));
        assertTrue(list.contains(A, "NOTCH_"), "a nick-only entry matches any UUID with that nick");
        assertFalse(list.contains(null, "Notch"));
    }

    @Test
    void uuidDecidesWhenBothAreKnown() {
        FriendList list = FriendList.EMPTY.add("Old", A);
        assertTrue(list.contains(A, "Renamed"), "a renamed friend stays a friend");
        assertFalse(list.contains(B, "Old"), "someone else who took the old nick is not a friend");
        assertTrue(list.containsUuid(A));
        assertFalse(list.containsUuid(B));
    }

    @Test
    void addingAgainUpdatesInsteadOfDuplicating() {
        FriendList list = FriendList.EMPTY.add("Nick", null);
        list = list.add("Nick", A);
        assertEquals(1, list.size());
        assertEquals(A, list.entries().getFirst().uuid(), "UUID learned later is stored");
        list = list.add("NewNick", A);
        assertEquals(List.of(new FriendList.Entry("NewNick", A)), list.entries(), "rename refreshes the nick");
        assertSame(list, list.add("NewNick", A), "no change, same instance");
    }

    @Test
    void invalidNicksAreIgnored() {
        assertSame(FriendList.EMPTY, FriendList.EMPTY.add("", null));
        assertSame(FriendList.EMPTY, FriendList.EMPTY.add("has space", null));
        assertSame(FriendList.EMPTY, FriendList.EMPTY.add("way_too_long_nick_name", null));
        assertSame(FriendList.EMPTY, FriendList.EMPTY.add("§cRed", null));
        assertTrue(FriendList.isValidName("A_b9"));
    }

    @Test
    void removeByNickOrUuid() {
        FriendList list = FriendList.EMPTY.add("One", A).add("Two", null);
        assertEquals(List.of("Two"), list.remove("one").names());
        assertEquals(List.of("Two"), list.remove(A.toString()).names());
        assertSame(list, list.remove("Nobody"));
        assertEquals(List.of("One"), list.remove(null, "TWO").names());
        assertEquals(List.of("Two"), list.remove(A, "Whatever").names());
    }

    @Test
    void jsonRoundTrip() {
        FriendList list = FriendList.EMPTY.add("Zed", A).add("alpha", null);
        FriendList back = FriendList.fromJson(JsonParser.parseString(list.toJson().toString()));
        assertEquals(list, back);
        assertEquals(List.of("alpha", "Zed"), back.names(), "display order ignores case");
    }

    @Test
    void readsHandEditedAndBrokenJson() {
        FriendList list = FriendList.fromJson(JsonParser.parseString(
                "[\"Plain\", {\"name\":\"WithId\",\"uuid\":\"" + B + "\"}, {\"name\":\"BadId\",\"uuid\":\"nope\"},"
                        + " {\"uuid\":\"" + A + "\"}, null, [], {\"name\":\"bad nick\"}, \"plain\"]"));
        assertEquals(3, list.size());
        assertTrue(list.contains(null, "plain"));
        assertEquals(B, list.find(null, "WithId").uuid());
        assertNull(list.find(null, "BadId").uuid());
        assertSame(FriendList.EMPTY, FriendList.fromJson(JsonParser.parseString("{\"not\":\"a list\"}")));
        assertSame(FriendList.EMPTY, FriendList.fromJson(null));
    }

    @Test
    void sizeIsCapped() {
        FriendList list = FriendList.EMPTY;
        for (int i = 0; i < FriendList.MAX_SIZE + 10; i++) {
            list = list.add("p" + i, null);
        }
        assertEquals(FriendList.MAX_SIZE, list.size());
    }

    @Test
    void settingPersistsAndSurvivesReset() {
        FriendListSetting setting = new FriendListSetting("list");
        setting.set(FriendList.EMPTY.add("Keep", A));
        FriendListSetting loaded = new FriendListSetting("list");
        loaded.fromJson(setting.toJson());
        assertEquals(setting.get(), loaded.get());
        loaded.reset();
        assertEquals(1, loaded.get().size(), "«reset module» does not wipe friends");
        loaded.fromJson(JsonParser.parseString("\"garbage\""));
        assertEquals(1, loaded.get().size(), "invalid JSON keeps the current list");
        assertFalse(loaded.isVisible(), "no menu row");
        loaded.clear();
        assertTrue(loaded.get().isEmpty());
    }
}
