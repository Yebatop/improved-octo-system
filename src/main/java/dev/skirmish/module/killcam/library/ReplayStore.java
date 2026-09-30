package dev.skirmish.module.killcam.library;

import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The replay folder ({@code config/skirmish/replays}). Every operation runs on one background thread and returns a
 * future; files are written to a temporary name and moved into place, so a crash never leaves half a replay under a
 * real name. Files that cannot be read (damaged, other format version, foreign files) are skipped with a log line and
 * never deleted by the cleanup.
 */
public final class ReplayStore {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss", Locale.ROOT);
    private static final String TEMP_SUFFIX = ".tmp";
    private static final int MAX_NAME = 16;
    private static final int MAX_ATTEMPTS = 1000;

    /** Diagnostics sink (debug.log in game). */
    public interface Log {
        void info(String message);

        /** Always logged (unreadable files); defaults to {@link #info}. */
        default void warn(String message) {
            info(message);
        }

        void error(String message, Throwable error);
    }

    /** A readable replay file. */
    public record Entry(Path file, long bytes, ReplayHeader header) {
        public String id() {
            return file.getFileName().toString();
        }
    }

    /** A listing: readable replays (newest first) and how many files were skipped. */
    public record Listing(List<Entry> entries, int skipped) {
        public long totalBytes() {
            long total = 0;
            for (Entry entry : entries) {
                total += entry.bytes();
            }
            return total;
        }
    }

    public record Limits(int maxCount, long maxBytes) {
    }

    /** Result of a save: the new file and the replays the cleanup deleted to stay within the limits. */
    public record Saved(Entry entry, List<Entry> deleted) {
    }

    private final Path directory;
    private final Log log;
    private final Executor executor;
    /** Files already reported as unreadable (logged once per session, not on every listing). */
    private final Set<String> reported = new HashSet<>();

    public ReplayStore(Path directory, Log log, Executor executor) {
        this.directory = directory;
        this.log = log;
        this.executor = executor;
    }

    /** A store with its own low-priority daemon thread. */
    public static ReplayStore withOwnThread(Path directory, Log log) {
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Skirmish Replays");
            thread.setDaemon(true);
            thread.setPriority(Thread.MIN_PRIORITY + 1);
            return thread;
        });
        return new ReplayStore(directory, log, executor);
    }

    public Path directory() {
        return directory;
    }

    // ---- async API ----

    public CompletableFuture<Listing> list() {
        return CompletableFuture.supplyAsync(this::listNow, executor);
    }

    public CompletableFuture<Saved> save(ReplayHeader header, ReplayRecording recording, Limits limits) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return saveNow(header, recording, limits);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, executor);
    }

    public CompletableFuture<ReplayCodec.Decoded> load(Path file) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return loadNow(file);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, executor);
    }

    public CompletableFuture<Entry> update(Path file, ReplayHeader header) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return updateNow(file, header);
            } catch (IOException e) {
                throw new java.io.UncheckedIOException(e);
            }
        }, executor);
    }

    public CompletableFuture<Boolean> delete(Path file) {
        return CompletableFuture.supplyAsync(() -> deleteNow(file), executor);
    }

    // ---- blocking implementations (store thread; tests call them directly) ----

    public Listing listNow() {
        List<Entry> entries = new ArrayList<>();
        int skipped = 0;
        if (!Files.isDirectory(directory)) {
            return new Listing(entries, 0);
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*" + ReplayCodec.EXTENSION)) {
            for (Path file : files) {
                try (InputStream in = new BufferedInputStream(Files.newInputStream(file), 4096)) {
                    ReplayHeader header = ReplayCodec.readHeader(in);
                    entries.add(new Entry(file, Files.size(file), header));
                } catch (IOException | RuntimeException e) {
                    skipped++;
                    if (reported.add(file.getFileName().toString())) {
                        log.warn("skipped " + file.getFileName() + ": " + describe(e));
                    }
                }
            }
        } catch (IOException e) {
            log.error("cannot list " + directory, e);
        }
        entries.sort(Comparator.comparingLong((Entry e) -> e.header().createdMs).reversed().thenComparing(Entry::id));
        return new Listing(entries, skipped);
    }

    public Saved saveNow(ReplayHeader header, ReplayRecording recording, Limits limits) throws IOException {
        long started = System.nanoTime();
        byte[] bytes = ReplayCodec.encode(header, recording);
        Files.createDirectories(directory);
        Path target = uniqueName(baseName(header));
        write(target, bytes);
        Entry entry = new Entry(target, bytes.length, header.copy());
        log.info(String.format(Locale.ROOT, "saved %s: %s, %d players, %d frames, %d items, %d events, %.1f KiB in %.1f ms",
                target.getFileName(), header, recording.tracks.size(), recording.frameCount(), recording.items.size(),
                recording.events.size(), bytes.length / 1024.0, (System.nanoTime() - started) / 1e6));
        return new Saved(entry, cleanupNow(limits, target));
    }

    /** Deletes the oldest non-favourite replays beyond the limits; {@code keep} (the replay just saved) stays. */
    public List<Entry> cleanupNow(Limits limits, @Nullable Path keep) {
        Listing listing = listNow();
        List<ReplayRetention.Item> items = new ArrayList<>();
        for (Entry entry : listing.entries()) {
            items.add(new ReplayRetention.Item(entry.id(), entry.header().createdMs, entry.bytes(), entry.header().favourite));
        }
        List<ReplayRetention.Item> victims = ReplayRetention.select(items, limits.maxCount(), limits.maxBytes(),
                keep == null ? null : keep.getFileName().toString());
        List<Entry> deleted = new ArrayList<>();
        for (ReplayRetention.Item item : victims) {
            for (Entry entry : listing.entries()) {
                if (entry.id().equals(item.id()) && deleteNow(entry.file())) {
                    deleted.add(entry);
                }
            }
        }
        if (!deleted.isEmpty()) {
            log.info("cleanup: deleted " + deleted.size() + " old replay(s) to stay within " + limits.maxCount() + " replays / "
                    + limits.maxBytes() / (1024 * 1024) + " MB: " + deleted.stream().map(Entry::id).toList());
        }
        return deleted;
    }

    public ReplayCodec.Decoded loadNow(Path file) throws IOException {
        long started = System.nanoTime();
        ReplayCodec.Decoded decoded = ReplayCodec.decode(Files.readAllBytes(file));
        log.info(String.format(Locale.ROOT, "loaded %s: %s, %d players, %d frames, %d items, %d events in %.1f ms", file.getFileName(),
                decoded.header(), decoded.recording().tracks.size(), decoded.recording().frameCount(), decoded.recording().items.size(),
                decoded.recording().events.size(), (System.nanoTime() - started) / 1e6));
        return decoded;
    }

    /** Rewrites the header (rename, star); the recorded frames are copied unchanged. */
    public Entry updateNow(Path file, ReplayHeader header) throws IOException {
        byte[] bytes = ReplayCodec.withHeader(Files.readAllBytes(file), header);
        write(file, bytes);
        log.info("updated " + file.getFileName() + ": " + header);
        return new Entry(file, bytes.length, header.copy());
    }

    public boolean deleteNow(Path file) {
        try {
            Files.delete(file);
            log.info("deleted " + file.getFileName());
            return true;
        } catch (NoSuchFileException e) {
            return false;
        } catch (IOException e) {
            log.error("cannot delete " + file, e);
            return false;
        }
    }

    // ---- files ----

    private void write(Path target, byte[] bytes) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + TEMP_SUFFIX);
        Files.write(temp, bytes);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            Files.deleteIfExists(temp);
            throw e;
        }
    }

    private Path uniqueName(String base) throws IOException {
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            Path candidate = directory.resolve(i == 1 ? base + ReplayCodec.EXTENSION : base + "_" + i + ReplayCodec.EXTENSION);
            if (!Files.exists(candidate)) {
                return candidate;
            }
        }
        throw new IOException("no free file name for " + base);
    }

    /** {@code 2026-09-24_14-05-33_kill_Notch}. */
    static String baseName(ReplayHeader header) {
        String time = STAMP.format(Instant.ofEpochMilli(header.createdMs).atZone(ZoneId.systemDefault()));
        String who = sanitize(header.opponent);
        return time + "_" + header.kind.id() + (who.isEmpty() ? "" : "_" + who);
    }

    /** ASCII letters, digits, '-' and '_' only; at most 16 characters; "" when nothing is left. */
    static String sanitize(String name) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < name.length() && out.length() < MAX_NAME; i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_';
            out.append(ok ? c : '_');
        }
        String result = out.toString();
        return result.chars().allMatch(c -> c == '_') ? "" : result;
    }

    private static String describe(Throwable e) {
        if (e instanceof ReplayFormatException format) {
            return format.getMessage() + " (" + format.problem().name().toLowerCase(Locale.ROOT) + ")";
        }
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
