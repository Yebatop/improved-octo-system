package dev.skirmish.module.market;

import dev.skirmish.debug.DebugLog;
import dev.skirmish.module.market.parse.PriceHistory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** config/skirmish/prices.json: loaded once at start, written on a background thread when changed. */
final class PriceStore {
    static final int MAX_PER_ITEM = 40;
    static final int MAX_ITEMS = 600;

    private final Path file;
    private final PriceHistory history = new PriceHistory(MAX_PER_ITEM, MAX_ITEMS);
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Skirmish prices.json");
        t.setDaemon(true);
        return t;
    });
    private boolean dirty;

    PriceStore(Path file) {
        this.file = file;
    }

    PriceHistory history() {
        return history;
    }

    void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            history.loadJson(Files.readString(file, StandardCharsets.UTF_8));
            DebugLog.log(MarketModule.ID, "prices.json loaded: " + history.size() + " items");
        } catch (IOException | RuntimeException e) {
            DebugLog.error(MarketModule.ID, "could not read " + file, e);
        }
    }

    void markDirty() {
        dirty = true;
    }

    /** Client thread: snapshots the history and writes it off-thread. */
    void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        String json = history.toJson();
        try {
            writer.execute(() -> write(json));
        } catch (RejectedExecutionException e) {
            write(json);
        }
    }

    /** At client shutdown: writes synchronously. */
    void saveNow() {
        if (dirty) {
            dirty = false;
            write(history.toJson());
        }
    }

    private void write(String json) {
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            DebugLog.error(MarketModule.ID, "could not write " + file, e);
        }
    }
}
