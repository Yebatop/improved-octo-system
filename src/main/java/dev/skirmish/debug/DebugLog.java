package dev.skirmish.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * config/skirmish/debug.log. Callers only enqueue (no IO, no formatting on the render thread);
 * a daemon thread formats and appends once per second and rotates the file by size.
 * Per-module gating is done by {@code Module.log}; this class writes whatever it receives.
 */
public final class DebugLog {
    private static final Logger LOGGER = LoggerFactory.getLogger("skirmish");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneId.systemDefault());
    private static final int MAX_QUEUED = 20_000;

    private record Entry(long time, String tag, String message) {
    }

    private static final ConcurrentLinkedQueue<Entry> QUEUE = new ConcurrentLinkedQueue<>();
    private static final AtomicInteger QUEUED = new AtomicInteger();
    private static final AtomicInteger DROPPED = new AtomicInteger();

    private static volatile Path file;
    private static volatile long maxBytes = 2L * 1024 * 1024;
    private static volatile int keepFiles = 3;
    private static ScheduledExecutorService executor;

    private DebugLog() {
    }

    public static synchronized void init(Path directory) {
        if (executor != null) {
            return;
        }
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            LOGGER.warn("Cannot create {}", directory, e);
        }
        file = directory.resolve("debug.log");
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "Skirmish debug log");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(DebugLog::flushQuietly, 1, 1, TimeUnit.SECONDS);
        log("core", "debug.log opened, max " + maxBytes / 1024 + " KiB x " + keepFiles + " files");
    }

    /** Test/advanced hook: rotation thresholds. */
    public static void configureRotation(long maxBytesPerFile, int filesToKeep) {
        maxBytes = Math.max(1024, maxBytesPerFile);
        keepFiles = Math.max(1, filesToKeep);
    }

    /** Enqueues one line. Safe to call from any thread, never blocks on IO. */
    public static void log(String tag, String message) {
        if (QUEUED.get() >= MAX_QUEUED) {
            DROPPED.incrementAndGet();
            return;
        }
        QUEUE.add(new Entry(System.currentTimeMillis(), tag, message));
        QUEUED.incrementAndGet();
    }

    /** Errors are always written, independent of per-module debug toggles, and mirrored to the game log. */
    public static void error(String tag, String message, Throwable t) {
        LOGGER.error("[{}] {}", tag, message, t);
        log(tag, "ERROR " + message + (t == null ? "" : ": " + t));
    }

    public static synchronized void shutdown() {
        if (executor != null) {
            executor.shutdown();
            try {
                executor.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            executor = null;
        }
        flushQuietly();
    }

    private static void flushQuietly() {
        try {
            flush();
        } catch (Throwable t) {
            LOGGER.warn("debug.log flush failed", t);
        }
    }

    /** Writes queued entries now. Called by the background thread; public for tests. */
    public static synchronized void flush() throws IOException {
        Path target = file;
        if (target == null || QUEUE.isEmpty()) {
            return;
        }
        rotateIfNeeded(target);
        StringBuilder line = new StringBuilder(160);
        try (BufferedWriter out = Files.newBufferedWriter(target, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND, StandardOpenOption.WRITE)) {
            int dropped = DROPPED.getAndSet(0);
            if (dropped > 0) {
                out.write(TIME.format(Instant.now()) + " [core] " + dropped + " log lines dropped (queue full)");
                out.newLine();
            }
            long written = 0;
            Entry entry;
            while ((entry = QUEUE.poll()) != null) {
                QUEUED.decrementAndGet();
                line.setLength(0);
                line.append(TIME.format(Instant.ofEpochMilli(entry.time)))
                        .append(" [").append(entry.tag).append("] ").append(entry.message);
                out.write(line.toString());
                out.newLine();
                written += line.length() + 1;
                if (written > maxBytes) {
                    break;
                }
            }
        }
        if (!QUEUE.isEmpty()) {
            flush();
        }
    }

    private static void rotateIfNeeded(Path target) throws IOException {
        if (!Files.exists(target) || Files.size(target) < maxBytes) {
            return;
        }
        String base = target.getFileName().toString().replace(".log", "");
        Path dir = target.getParent();
        Files.deleteIfExists(dir.resolve(base + "." + (keepFiles - 1) + ".log"));
        for (int i = keepFiles - 2; i >= 1; i--) {
            Path from = dir.resolve(base + "." + i + ".log");
            if (Files.exists(from)) {
                Files.move(from, dir.resolve(base + "." + (i + 1) + ".log"), StandardCopyOption.REPLACE_EXISTING);
            }
        }
        if (keepFiles > 1) {
            Files.move(target, dir.resolve(base + ".1.log"), StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.delete(target);
        }
    }

    /** Test hook: point the log at a directory without starting the background thread. */
    public static synchronized void initForTests(Path directory) throws IOException {
        Files.createDirectories(directory);
        file = directory.resolve("debug.log");
        QUEUE.clear();
        QUEUED.set(0);
        DROPPED.set(0);
    }
}
