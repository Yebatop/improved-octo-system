package dev.skirmish.module.analytics;

import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Who hit another player when the damage packet names nobody (HolyWorld): the player next to the victim whose arm
 * swing is the most recent, nearest on a tie. Only what the client already sees (positions and swing animations).
 * Pure Java, unit tested.
 */
public final class KillerGuess {
    /** Melee reach plus movement and latency. */
    public static final double MAX_DISTANCE = 6.0;
    /** A swing to the damage event on the victim. */
    public static final long SWING_WINDOW_MS = 700;

    /**
     * @param swingAgeMs ms since this player's last arm swing, or -1 if none was seen
     */
    public record Candidate(UUID uuid, String name, double distance, long swingAgeMs) {
    }

    private KillerGuess() {
    }

    public static @Nullable Candidate best(List<Candidate> candidates) {
        Candidate best = null;
        for (Candidate c : candidates) {
            if (c.distance() > MAX_DISTANCE || c.swingAgeMs() < 0 || c.swingAgeMs() > SWING_WINDOW_MS) {
                continue;
            }
            if (best == null || c.swingAgeMs() < best.swingAgeMs()
                    || c.swingAgeMs() == best.swingAgeMs() && c.distance() < best.distance()) {
                best = c;
            }
        }
        return best;
    }
}
