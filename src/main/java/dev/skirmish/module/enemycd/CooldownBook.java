package dev.skirmish.module.enemycd;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What the Enemy Cooldowns module knows: how long each item cooldown lasts on this server (learned from the
 * cooldowns the server sends you for your own items) and which players were seen using what, when. Pure Java.
 */
final class CooldownBook {
    enum Kind {
        PEARL("minecraft:ender_pearl"), GAPPLE("minecraft:golden_apple"), EGAPPLE("minecraft:enchanted_golden_apple"),
        SHIELD("minecraft:shield");

        final String group;

        Kind(String group) {
            this.group = group;
        }
    }

    /** A use seen at {@code at}; {@code durationMs} below 0 when this server's cooldown is not known yet. */
    record Mark(Kind kind, long at, long durationMs) {
        long leftMs(long now) {
            return durationMs < 0 ? -1 : Math.max(0, at + durationMs - now);
        }

        long ageMs(long now) {
            return now - at;
        }
    }

    private final Map<String, Integer> learnedTicks = new HashMap<>();
    private final Map<UUID, EnumMap<Kind, Mark>> marks = new HashMap<>();

    static @Nullable Kind byGroup(String group) {
        for (Kind kind : Kind.values()) {
            if (kind.group.equals(group)) {
                return kind;
            }
        }
        return null;
    }

    /** Your own cooldown for {@code group} lasted {@code ticks}; true when that is news. */
    boolean learn(String group, int ticks) {
        if (byGroup(group) == null || ticks <= 0) {
            return false;
        }
        Integer old = learnedTicks.put(group, ticks);
        return old == null || old != ticks;
    }

    Map<String, Integer> learned() {
        return learnedTicks;
    }

    /** Cooldown length for a kind: learned, else {@code defaultTicks} (0 or less: unknown, -1). */
    long durationMs(Kind kind, int defaultTicks) {
        Integer ticks = learnedTicks.get(kind.group);
        int t = ticks != null ? ticks : defaultTicks;
        return t > 0 ? t * 50L : -1;
    }

    void mark(UUID player, Kind kind, long now, long durationMs) {
        marks.computeIfAbsent(player, id -> new EnumMap<>(Kind.class)).put(kind, new Mark(kind, now, durationMs));
    }

    /** Live marks of a player (expired ones dropped: a known cooldown ran out, an unknown one is older than keepMs). */
    List<Mark> active(UUID player, long now, long unknownKeepMs) {
        EnumMap<Kind, Mark> map = marks.get(player);
        if (map == null) {
            return List.of();
        }
        map.values().removeIf(m -> m.durationMs() >= 0 ? now >= m.at() + m.durationMs() : m.ageMs(now) > unknownKeepMs);
        if (map.isEmpty()) {
            marks.remove(player);
            return List.of();
        }
        return new ArrayList<>(map.values());
    }

    boolean hasMarks() {
        return !marks.isEmpty();
    }

    Iterable<UUID> players() {
        return List.copyOf(marks.keySet());
    }

    void clearMarks() {
        marks.clear();
    }
}
