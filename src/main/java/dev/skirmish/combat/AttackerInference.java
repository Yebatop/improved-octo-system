package dev.skirmish.combat;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Who hit whom when a damage packet names no attacker. HolyWorld sends every hit as {@code minecraft:generic}
 * without a source entity (captured 2026-09), so without this no fight, kill or killer is ever seen there.
 * Uses only what the client already knows: my own attack click and its target, arm swings of nearby players,
 * distances and the fights in progress. Pure Java (unit tested); {@link CombatTracker} gathers the inputs.
 */
final class AttackerInference {
    /** My attack click to the server's damage event on that target: round trip plus a tick or two. */
    static final long MY_HIT_WINDOW_MS = 700;
    /** A player's swing to the damage event on me. */
    static final long SWING_WINDOW_MS = 700;
    /** Melee reach (3 blocks) plus movement and latency. */
    static final double MELEE_RANGE = 6.0;

    /**
     * A player near me when I took damage.
     *
     * @param swingAgeMs ms since this player's last arm swing, or -1 if none was seen
     * @param inFight    an active fight with this player exists
     */
    record Candidate(Combatant who, double distance, long swingAgeMs, boolean inFight) {
    }

    private AttackerInference() {
    }

    /** Another entity took damage: mine when I clicked exactly that entity a moment ago. */
    static boolean isMyHit(int victimId, int lastTargetId, long attackAgeMs) {
        return lastTargetId >= 0 && victimId == lastTargetId && attackAgeMs >= 0 && attackAgeMs <= MY_HIT_WINDOW_MS;
    }

    /**
     * I took damage: the player in melee range whose swing is the most recent (nearest on a tie); without swings,
     * the only fight opponent in range. Null when nothing points at anyone.
     */
    static @Nullable Combatant attackerOnMe(List<Candidate> candidates) {
        Candidate best = null;
        for (Candidate c : candidates) {
            if (c.distance() > MELEE_RANGE || c.swingAgeMs() < 0 || c.swingAgeMs() > SWING_WINDOW_MS) {
                continue;
            }
            if (best == null || c.swingAgeMs() < best.swingAgeMs()
                    || c.swingAgeMs() == best.swingAgeMs() && c.distance() < best.distance()) {
                best = c;
            }
        }
        if (best != null) {
            return best.who();
        }
        Candidate only = null;
        for (Candidate c : candidates) {
            if (c.inFight() && c.distance() <= MELEE_RANGE) {
                if (only != null) {
                    return null;
                }
                only = c;
            }
        }
        return only == null ? null : only.who();
    }
}
