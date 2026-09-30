package dev.skirmish.module.killcam.library;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Cleanup policy and the replay folder: limits, favourites, unreadable files. */
class ReplayStorageTest {
    private static ReplayRetention.Item item(String id, long created, long bytes, boolean favourite) {
        return new ReplayRetention.Item(id, created, bytes, favourite);
    }

    private static List<String> ids(List<ReplayRetention.Item> items) {
        return items.stream().map(ReplayRetention.Item::id).toList();
    }

    @Test
    void countLimitDeletesTheOldestFirst() {
        List<ReplayRetention.Item> items = List.of(item("c", 30, 10, false), item("a", 10, 10, false), item("b", 20, 10, false),
                item("d", 40, 10, false));
        assertEquals(List.of("a", "b"), ids(ReplayRetention.select(items, 2, Long.MAX_VALUE, null)));
        assertEquals(List.of(), ids(ReplayRetention.select(items, 4, Long.MAX_VALUE, null)));
    }

    @Test
    void sizeLimitDeletesUntilTheRestFits() {
        List<ReplayRetention.Item> items = List.of(item("a", 10, 400, false), item("b", 20, 300, false), item("c", 30, 200, false),
                item("d", 40, 100, false));
        assertEquals(List.of("a"), ids(ReplayRetention.select(items, 100, 600, null)), "600 B left after the oldest");
        assertEquals(List.of("a", "b", "c"), ids(ReplayRetention.select(items, 100, 150, null)));
    }

    @Test
    void favouritesAndTheNewReplayAreNeverDeleted() {
        List<ReplayRetention.Item> items = List.of(item("a", 10, 100, true), item("b", 20, 100, false), item("c", 30, 100, true),
                item("d", 40, 100, false));
        assertEquals(List.of("b"), ids(ReplayRetention.select(items, 3, Long.MAX_VALUE, null)));
        assertEquals(List.of("b"), ids(ReplayRetention.select(items, 1, Long.MAX_VALUE, "d")),
                "only favourites and the new replay are left, even though that is over the limit");
        assertEquals(List.of("b", "d"), ids(ReplayRetention.select(items, 0, 0, null)));
        assertEquals(List.of(), ids(ReplayRetention.select(List.of(item("a", 1, 1_000, true)), 0, 0, null)));
    }

    @Test
    void equalTimesAreOrderedByName() {
        List<ReplayRetention.Item> items = List.of(item("b", 10, 1, false), item("a", 10, 1, false));
        assertEquals(List.of("a"), ids(ReplayRetention.select(items, 1, Long.MAX_VALUE, null)));
    }

    // ---- store ----

    private static final class Messages implements ReplayStore.Log {
        final List<String> lines = new ArrayList<>();

        @Override
        public void info(String message) {
            lines.add(message);
        }

        @Override
        public void error(String message, Throwable error) {
            lines.add("ERROR " + message + ": " + error);
        }
    }

    private static ReplayHeader header(long created, ReplayKind kind, String opponent) {
        ReplayHeader h = ReplayCodecTest.header();
        h.createdMs = created;
        h.kind = kind;
        h.opponent = opponent;
        h.favourite = false;
        h.title = "";
        return h;
    }

    @Test
    void savedReplaysAreListedNewestFirstAndCleanedUp(@TempDir Path dir) throws IOException {
        Messages log = new Messages();
        ReplayStore store = new ReplayStore(dir.resolve("replays"), log, Runnable::run);
        assertTrue(store.listNow().entries().isEmpty(), "missing folder lists as empty");
        ReplayStore.Limits roomy = new ReplayStore.Limits(10, Long.MAX_VALUE);

        ReplayStore.Saved first = store.saveNow(header(1_000_000, ReplayKind.KILL, "Notch"), ReplayCodecTest.recording(), roomy);
        ReplayStore.Saved second = store.saveNow(header(2_000_000, ReplayKind.DEATH, "Herobrine"), ReplayCodecTest.recording(), roomy);
        store.saveNow(header(3_000_000, ReplayKind.CLIP, ""), ReplayCodecTest.recording(), roomy);
        assertTrue(first.entry().id().endsWith("_kill_Notch" + ReplayCodec.EXTENSION), first.entry().id());
        assertTrue(Files.exists(first.entry().file()));

        ReplayStore.Listing listing = store.listNow();
        assertEquals(List.of(ReplayKind.CLIP, ReplayKind.DEATH, ReplayKind.KILL),
                listing.entries().stream().map(e -> e.header().kind).toList());
        assertEquals(3 * first.entry().bytes(), listing.totalBytes());

        // Star the oldest; with room for two the middle one goes, not the favourite and not the new one.
        ReplayHeader starred = first.entry().header().copy();
        starred.favourite = true;
        starred.title = "Первый";
        store.updateNow(first.entry().file(), starred);
        ReplayStore.Saved fourth = store.saveNow(header(4_000_000, ReplayKind.KILL, "Jeb_"), ReplayCodecTest.recording(),
                new ReplayStore.Limits(2, Long.MAX_VALUE));
        assertEquals(2, fourth.deleted().size());
        assertFalse(Files.exists(second.entry().file()));
        List<String> left = store.listNow().entries().stream().map(e -> e.header().opponent).toList();
        assertEquals(List.of("Jeb_", "Notch"), left);
        assertEquals("Первый", store.loadNow(first.entry().file()).header().title);
        ReplayCodecTest.assertRecordingEquals(ReplayCodecTest.recording(), store.loadNow(fourth.entry().file()).recording());
    }

    @Test
    void unreadableFilesAreSkippedAndKept(@TempDir Path dir) throws IOException {
        Messages log = new Messages();
        ReplayStore store = new ReplayStore(dir, log, Runnable::run);
        store.saveNow(header(1_000, ReplayKind.KILL, "Notch"), ReplayCodecTest.recording(), new ReplayStore.Limits(10, Long.MAX_VALUE));
        Path junk = dir.resolve("junk" + ReplayCodec.EXTENSION);
        Files.writeString(junk, "definitely not a replay", StandardCharsets.UTF_8);
        byte[] future = ReplayCodec.encode(header(2_000, ReplayKind.CLIP, ""), ReplayCodecTest.recording());
        future[5] = (byte) (ReplayCodec.VERSION + 1);
        Path newer = dir.resolve("newer" + ReplayCodec.EXTENSION);
        Files.write(newer, future);
        Files.writeString(dir.resolve("half.skreplay.tmp"), "partial write", StandardCharsets.UTF_8);

        ReplayStore.Listing listing = store.listNow();
        assertEquals(1, listing.entries().size());
        assertEquals(2, listing.skipped());
        assertTrue(log.lines.stream().anyMatch(l -> l.contains("junk") && l.contains("not_a_replay")), log.lines.toString());
        assertTrue(log.lines.stream().anyMatch(l -> l.contains("newer") && l.contains("newer_version")), log.lines.toString());
        int lines = log.lines.size();
        store.listNow();
        assertEquals(lines, log.lines.size(), "each unreadable file is reported once");

        store.cleanupNow(new ReplayStore.Limits(0, 0), null);
        assertTrue(Files.exists(junk), "files the store cannot read are never deleted");
        assertTrue(Files.exists(newer));
        assertTrue(store.listNow().entries().isEmpty());
        assertThrows(ReplayFormatException.class, () -> store.loadNow(newer));
    }

    @Test
    void asyncOperationsCompleteOnTheStoreExecutor(@TempDir Path dir) throws Exception {
        ReplayStore store = ReplayStore.withOwnThread(dir, new Messages());
        ReplayStore.Saved saved = store.save(header(5_000, ReplayKind.DEATH, "Notch"), ReplayCodecTest.recording(),
                new ReplayStore.Limits(5, Long.MAX_VALUE)).get();
        assertEquals(1, store.list().get().entries().size());
        assertEquals("Notch", store.load(saved.entry().file()).get().header().opponent);
        assertTrue(store.delete(saved.entry().file()).get());
        assertTrue(store.list().get().entries().isEmpty());
    }

    @Test
    void fileNamesAreSafe() {
        assertEquals("Notch", ReplayStore.sanitize("Notch"));
        assertEquals("a_b_c", ReplayStore.sanitize("a/b\\c"));
        assertEquals("", ReplayStore.sanitize("Игрок"));
        assertEquals(16, ReplayStore.sanitize("x".repeat(40)).length());
    }
}
