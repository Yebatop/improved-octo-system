package dev.skirmish.combat;

public enum FightEndReason {
    /** Opponent died within the kill window after my last hit. */
    KILL,
    /** Opponent died, but my last hit was too long ago (or never landed). */
    OPPONENT_DIED,
    /** I died. */
    OWN_DEATH,
    /** No hits either way for the fight timeout. */
    TIMEOUT,
    /** Disconnect or dimension/world change. */
    WORLD_CHANGE
}
