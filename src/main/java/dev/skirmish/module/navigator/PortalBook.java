package dev.skirmish.module.navigator;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.skirmish.debug.DebugLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Portals you have gone through, per server: the Overworld and Nether ends of each, saved in
 * {@code config/skirmish/portals.json}. A new crossing near a known end updates that link instead of adding one.
 */
final class PortalBook {
    /** One saved crossing (Gson-friendly). */
    static final class Entry {
        String server = "";
        double ox;
        double oy;
        double oz;
        double nx;
        double ny;
        double nz;
        long seen;
        String name = "";
    }

    private static final double MERGE = 6.0;
    private final Path file;
    private final List<Entry> entries = new ArrayList<>();
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    PortalBook(Path file) {
        this.file = file;
    }

    void load() {
        try {
            if (Files.exists(file)) {
                List<Entry> list = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<Entry>>() { }.getType());
                if (list != null) {
                    entries.clear();
                    entries.addAll(list);
                }
            }
        } catch (IOException | RuntimeException e) {
            DebugLog.error("navigator", "portals.json unreadable", e);
        }
    }

    void save() {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, gson.toJson(entries), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            DebugLog.error("navigator", "portals.json not saved", e);
        }
    }

    /** Records a crossing; returns true when it was a new portal (false when an existing one was refreshed). */
    boolean record(String server, double ox, double oy, double oz, double nx, double ny, double nz, long now) {
        for (Entry e : entries) {
            if (e.server.equals(server) && (Math.hypot(e.ox - ox, e.oz - oz) < MERGE || Math.hypot(e.nx - nx, e.nz - nz) < MERGE)) {
                e.ox = ox;
                e.oy = oy;
                e.oz = oz;
                e.nx = nx;
                e.ny = ny;
                e.nz = nz;
                e.seen = now;
                save();
                return false;
            }
        }
        Entry e = new Entry();
        e.server = server;
        e.ox = ox;
        e.oy = oy;
        e.oz = oz;
        e.nx = nx;
        e.ny = ny;
        e.nz = nz;
        e.seen = now;
        entries.add(e);
        save();
        return true;
    }

    List<Entry> forServer(String server) {
        return entries.stream().filter(e -> e.server.equals(server)).toList();
    }

    List<RoutePlanner.Link> links(String server) {
        return forServer(server).stream().map(e -> new RoutePlanner.Link(e.ox, e.oz, e.nx, e.nz)).toList();
    }
}
