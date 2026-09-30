package dev.skirmish.module.analytics.dossier;

import java.util.List;
import java.util.UUID;

/**
 * What I know about one player from our fights: count, my kills and deaths against them, damage both ways, when we
 * last fought and the gear they wore then (item ids head, chest, legs, feet, main hand, off hand; "" for empty).
 * Pure Java, unit tested.
 */
public final class DossierRecord {
    /** One finished fight, as merged into the record. */
    public record FightResult(String name, boolean kill, double dealt, double taken, String outcome, List<String> gear, long timeMs) {
    }

    private final UUID uuid;
    String name;
    int fights;
    int kills;
    int deaths;
    double dealt;
    double taken;
    long firstSeenMs;
    long lastFightMs = -1;
    long lastSeenMs;
    String lastOutcome = "";
    List<String> gear = List.of();

    DossierRecord(UUID uuid, String name, long nowMs) {
        this.uuid = uuid;
        this.name = name;
        this.firstSeenMs = nowMs;
        this.lastSeenMs = nowMs;
    }

    /** Adds a finished fight: counts, damage, time, latest name and (when any was seen) gear. */
    public void merge(FightResult r) {
        fights++;
        if (r.kill()) {
            kills++;
        }
        if (Double.isFinite(r.dealt()) && r.dealt() > 0) {
            dealt += r.dealt();
        }
        if (Double.isFinite(r.taken()) && r.taken() > 0) {
            taken += r.taken();
        }
        if (!r.name().isBlank()) {
            name = r.name();
        }
        if (r.timeMs() >= lastFightMs) {
            lastFightMs = r.timeMs();
            lastOutcome = r.outcome();
            if (r.gear().stream().anyMatch(id -> !id.isEmpty())) {
                gear = List.copyOf(r.gear());
            }
        }
        lastSeenMs = Math.max(lastSeenMs, r.timeMs());
        firstSeenMs = Math.min(firstSeenMs, r.timeMs());
    }

    /** They killed me (possibly outside a tracked fight). */
    public void death(String killerName, long timeMs) {
        deaths++;
        if (!killerName.isBlank()) {
            name = killerName;
        }
        lastSeenMs = Math.max(lastSeenMs, timeMs);
    }

    public UUID uuid() {
        return uuid;
    }

    public String name() {
        return name;
    }

    public int fights() {
        return fights;
    }

    public int kills() {
        return kills;
    }

    public int deaths() {
        return deaths;
    }

    public double dealt() {
        return dealt;
    }

    public double taken() {
        return taken;
    }

    public long firstSeenMs() {
        return firstSeenMs;
    }

    /** -1 before the first finished fight. */
    public long lastFightMs() {
        return lastFightMs;
    }

    public long lastSeenMs() {
        return lastSeenMs;
    }

    public String lastOutcome() {
        return lastOutcome;
    }

    public List<String> gear() {
        return gear;
    }
}
