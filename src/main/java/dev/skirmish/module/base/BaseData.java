package dev.skirmish.module.base;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What Base OS keeps per server in {@code config/skirmish/base.json}: the base region, the containers you opened in
 * it with what was inside, stock minimums, the base log and the region's timers. Plain fields for Gson. Pure Java.
 */
final class BaseData {
    Map<String, Server> servers = new HashMap<>();

    Server server(String key) {
        return servers.computeIfAbsent(key, k -> new Server());
    }

    static final class Server {
        @Nullable Region region;
        Map<String, Chest> chests = new LinkedHashMap<>();
        /** Item key → warn when the total drops below this. */
        Map<String, Integer> minimums = new HashMap<>();
        /** Item key → name, for minimums of items that are all gone. */
        Map<String, String> names = new HashMap<>();
        List<LogEntry> log = new ArrayList<>();
        List<Timer> timers = new ArrayList<>();
    }

    /** The base: the region block, the protected box (inclusive block bounds) and where it came from. */
    static final class Region {
        String dim = "";
        int x;
        int y;
        int z;
        int x0;
        int y0;
        int z0;
        int x1;
        int y1;
        int z1;
        /** Region kind id from the region table, or "manual". */
        String type = "";
        String holo = "";
        boolean manual;
        /** A Lite region protects the whole height; y0..y1 is then only the window the model shows. */
        boolean column;
        long found;

        boolean contains(String dim, double px, double py, double pz) {
            return this.dim.equals(dim) && px >= x0 && px < x1 + 1 && pz >= z0 && pz < z1 + 1
                    && (column || py >= y0 && py < y1 + 1);
        }

        int sizeX() {
            return x1 - x0 + 1;
        }

        int sizeY() {
            return y1 - y0 + 1;
        }

        int sizeZ() {
            return z1 - z0 + 1;
        }
    }

    /** A container you opened: where, what block, when, what was inside (merged by item). */
    static final class Chest {
        String dim = "";
        int x;
        int y;
        int z;
        String block = "";
        long seen;
        List<Stack> items = new ArrayList<>();

        static String key(String dim, int x, int y, int z) {
            return dim + "|" + x + "," + y + "," + z;
        }

        String key() {
            return key(dim, x, y, z);
        }
    }

    /** An item kind in a container: market key (id plus custom name), registry id, shown name, count. */
    static final class Stack {
        String key = "";
        String id = "";
        String name = "";
        int count;

        Stack() {
        }

        Stack(String key, String id, String name, int count) {
            this.key = key;
            this.id = id;
            this.name = name;
            this.count = count;
        }
    }

    static final class LogEntry {
        long at;
        /** chest, intruder, stock, farm, base, timer */
        String kind = "";
        String text = "";

        LogEntry() {
        }

        LogEntry(long at, String kind, String text) {
            this.at = at;
            this.kind = kind;
            this.text = text;
        }
    }

    /** A countdown read off the region's hologram: its label and when it runs out. */
    static final class Timer {
        String label = "";
        long endsAt;
        long seen;

        Timer() {
        }

        Timer(String label, long endsAt, long seen) {
            this.label = label;
            this.endsAt = endsAt;
            this.seen = seen;
        }
    }
}
