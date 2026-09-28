package dev.skirmish.module.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.skirmish.debug.DebugLog;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * The event clocks ({@code config/skirmish/event_clock.json}): live events with their start and the lengths learned
 * per kind, so a relog keeps both. Saved at most every {@link #SAVE_EVERY} ms while polling, and on leaving.
 */
final class ClockStore {
    static final long SAVE_EVERY = 120_000;

    /** What the file holds. */
    static final class Data {
        EventClock lite = new EventClock();
        EventClock prime = new EventClock();
    }

    private @Nullable Path file;
    private final Gson gson = new GsonBuilder().create();
    private Data data = new Data();
    private boolean dirty;
    private long savedAt;

    ClockStore() {
        seed();
    }

    EventClock lite() {
        return data.lite;
    }

    EventClock prime() {
        return data.prime;
    }

    void lite(List<EventClock.Seen> seen, long at) {
        dirty |= data.lite.update(seen, at);
        saveSoon(at);
    }

    void prime(List<EventClock.Seen> seen, long at) {
        dirty |= data.prime.update(seen, at);
        saveSoon(at);
    }

    private void saveSoon(long now) {
        if (dirty && now - savedAt >= SAVE_EVERY) {
            save();
        }
    }

    void load(Path file) {
        this.file = file;
        try {
            if (Files.exists(file)) {
                Data read = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Data.class);
                if (read != null) {
                    data = read;
                    if (data.lite == null) {
                        data.lite = new EventClock();
                    }
                    if (data.prime == null) {
                        data.prime = new EventClock();
                    }
                    data.lite.sanitize();
                    data.prime.sanitize();
                }
            }
        } catch (IOException | RuntimeException e) {
            DebugLog.error(EventsModule.ID, "event_clock.json unreadable", e);
        }
        seed();
    }

    private void seed() {
        data.lite.seeds(EventLengths.LITE);
        data.prime.seeds(EventLengths.PRIME);
    }

    void save() {
        dirty = false;
        savedAt = System.currentTimeMillis();
        Path file = this.file;
        if (file == null) {
            return;
        }
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, gson.toJson(data), StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException | RuntimeException e) {
            DebugLog.error(EventsModule.ID, "event_clock.json not saved", e);
        }
    }
}
