package dev.skirmish.module.events;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * How long events go on. The Lite API only lists what runs now, so an event's start is the moment between the poll
 * that first had it and the one before (known only when that one was recent), and its end the moment between the
 * last poll that had it and the next. From events that ended while polling, a typical length per event kind is
 * learned (median of the latest ones) and kept across sessions; kinds never seen ending fall back to lengths
 * measured beforehand. Prime events carry their own start time. Lite events come in waves (all anarchies at once,
 * then one by one they end), so a poll where everything vanished at once is held back until the next one: if the
 * events are back it was a hiccup of the API and is dropped, otherwise they really ended. Pure Java; plain fields
 * for Gson.
 */
public final class EventClock {
    /** A poll farther than this from the one before cannot date an appearance or an end. */
    static final long MAX_GAP = 150_000;
    /** Lengths kept per kind. */
    static final int KEEP = 15;
    /** Shorter than this is not an event that ran (a glitch in the list). */
    static final long MIN_LENGTH = 30_000;

    /** One live event: its kind, when it started (or was first seen), whether that is its real start, when last seen. */
    static final class Live {
        String kind = "";
        long first;
        boolean startKnown;
        long last;
    }

    /** An event in one poll: its instance key, its kind and its start when the API gives it. */
    public record Seen(String key, String kind, @Nullable Long start) {
    }

    /**
     * What is known about a live event now: time since it started (or since it was first seen when the start is
     * unknown), the kind's typical length and the time left (−1 when not known; never below 0).
     */
    public record Estimate(long elapsed, boolean startKnown, long typical, long remaining) {
    }

    Map<String, Live> live = new HashMap<>();
    Map<String, List<Long>> lengths = new HashMap<>();
    long lastPoll;
    private transient Map<String, Long> seeds = Map.of();
    /** A poll that looked like a hiccup (everything gone at once), held until the next poll decides. */
    private transient @Nullable List<Seen> held;
    private transient long heldAt;

    void seeds(Map<String, Long> seeds) {
        this.seeds = seeds;
    }

    /** A new poll at {@code at}: dates new events, learns the lengths of those that ended; true when anything changed. */
    boolean update(List<Seen> now, long at) {
        boolean changed = false;
        if (held != null) {
            List<Seen> before = held;
            long beforeAt = heldAt;
            held = null;
            if (!mostlyBack(now)) {
                changed = apply(before, beforeAt);
            }
        } else if (suspicious(now)) {
            held = now;
            heldAt = at;
            return false;
        }
        return apply(now, at) || changed;
    }

    /** Everything live gone at once (or most of many): maybe the API's hiccup rather than the events ending. */
    private boolean suspicious(List<Seen> now) {
        if (live.isEmpty()) {
            return false;
        }
        int gone = live.size() - present(now);
        return now.isEmpty() || live.size() >= 5 && gone >= 0.6 * live.size();
    }

    /** At least half of what was live before the held poll is in this one. */
    private boolean mostlyBack(List<Seen> now) {
        return !live.isEmpty() && present(now) >= 0.5 * live.size();
    }

    private int present(List<Seen> now) {
        int n = 0;
        for (Seen s : now) {
            if (live.containsKey(s.key())) {
                n++;
            }
        }
        return n;
    }

    private boolean apply(List<Seen> now, long at) {
        boolean continuous = lastPoll > 0 && at - lastPoll <= MAX_GAP && at > lastPoll;
        Map<String, Seen> byKey = new HashMap<>();
        for (Seen s : now) {
            byKey.put(s.key(), s);
        }
        boolean changed = false;
        for (Iterator<Map.Entry<String, Live>> it = live.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Live> e = it.next();
            if (byKey.containsKey(e.getKey())) {
                continue;
            }
            Live l = e.getValue();
            if (continuous && l.startKnown && at - l.last <= MAX_GAP) {
                long end = (l.last + at) / 2;
                learn(l.kind, end - l.first);
            }
            it.remove();
            changed = true;
        }
        for (Seen s : now) {
            Live l = live.get(s.key());
            if (l == null) {
                l = new Live();
                l.kind = s.kind();
                if (s.start() != null) {
                    l.first = Math.min(at, s.start());
                    l.startKnown = true;
                } else {
                    l.first = continuous ? (lastPoll + at) / 2 : at;
                    l.startKnown = continuous;
                }
                live.put(s.key(), l);
                changed = true;
            }
            l.last = at;
        }
        lastPoll = at;
        return changed;
    }

    private void learn(String kind, long length) {
        if (length < MIN_LENGTH) {
            return;
        }
        List<Long> list = lengths.computeIfAbsent(kind, k -> new ArrayList<>());
        list.add(length);
        while (list.size() > KEEP) {
            list.removeFirst();
        }
    }

    /** The kind's typical length: the median of what was learned, else the measured seed, else −1. */
    long typical(String kind) {
        List<Long> list = lengths.get(kind);
        if (list != null && !list.isEmpty()) {
            List<Long> sorted = new ArrayList<>(list);
            sorted.sort(Long::compare);
            int n = sorted.size();
            return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
        }
        Long seed = seeds.get(kind);
        return seed == null ? -1 : seed;
    }

    /** The estimate for a live event, or null when it is not being tracked. */
    public @Nullable Estimate estimate(String key, long now) {
        Live l = live.get(key);
        if (l == null) {
            return null;
        }
        long elapsed = Math.max(0, now - l.first);
        long typical = typical(l.kind);
        long remaining = typical < 0 ? -1 : Math.max(0, typical - elapsed);
        return new Estimate(elapsed, l.startKnown, typical, remaining);
    }

    /** Drops what a broken file may hold. */
    void sanitize() {
        if (live == null) {
            live = new HashMap<>();
        }
        if (lengths == null) {
            lengths = new HashMap<>();
        }
        live.values().removeIf(l -> l == null || l.kind == null);
        lengths.values().removeIf(l -> l == null);
    }
}
