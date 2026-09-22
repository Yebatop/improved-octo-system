package dev.skirmish.combat;

import org.jspecify.annotations.Nullable;

/**
 * One fight between the local player and a single opponent. Created on the first hit either way,
 * mutated only on the client thread by {@link CombatLogic}. Listeners may keep references;
 * after {@link #isActive()} turns false the object no longer changes.
 */
public final class Fight {
    private final int id;
    private Combatant opponent;
    private final long startMs;
    private long endMs = -1;
    private @Nullable FightEndReason endReason;

    int hitsDealt;
    int hitsTaken;
    long lastHitDealtMs = -1;
    long lastHitTakenMs = -1;
    float damageDealt;
    float damageTaken;
    int healthDropsObserved;
    float opponentHealth = -1;
    float opponentAbsorption;
    float opponentMaxHealth = -1;
    int opponentTotems;
    int myTotems;
    @Nullable String lastDamageTypeDealt;

    Fight(int id, Combatant opponent, long startMs) {
        this.id = id;
        this.opponent = opponent;
        this.startMs = startMs;
    }

    public int id() {
        return id;
    }

    public Combatant opponent() {
        return opponent;
    }

    void updateOpponent(Combatant fresh) {
        this.opponent = fresh;
    }

    public long startMs() {
        return startMs;
    }

    /** End time, or -1 while active. */
    public long endMs() {
        return endMs;
    }

    public boolean isActive() {
        return endReason == null;
    }

    public @Nullable FightEndReason endReason() {
        return endReason;
    }

    void end(FightEndReason reason, long now) {
        this.endReason = reason;
        this.endMs = now;
    }

    public long durationMs(long now) {
        return (endMs >= 0 ? endMs : now) - startMs;
    }

    public long lastActivityMs() {
        return Math.max(startMs, Math.max(lastHitDealtMs, lastHitTakenMs));
    }

    public int hitsDealt() {
        return hitsDealt;
    }

    public int hitsTaken() {
        return hitsTaken;
    }

    public long lastHitDealtMs() {
        return lastHitDealtMs;
    }

    public long lastHitTakenMs() {
        return lastHitTakenMs;
    }

    /** Sum of opponent health + absorption drops that followed my hits. Only meaningful if {@link #isDamageKnown()}. */
    public float damageDealt() {
        return damageDealt;
    }

    public float damageTaken() {
        return damageTaken;
    }

    /**
     * True when at least one of my hits was followed by a visible health drop. False means the server does not
     * send the opponent's real health (or no hit landed), so {@link #damageDealt()} is not an estimate of anything.
     */
    public boolean isDamageKnown() {
        return healthDropsObserved > 0;
    }

    public int healthDropsObserved() {
        return healthDropsObserved;
    }

    /** Last seen opponent health, -1 if never received. */
    public float opponentHealth() {
        return opponentHealth;
    }

    public float opponentAbsorption() {
        return opponentAbsorption;
    }

    public float opponentMaxHealth() {
        return opponentMaxHealth;
    }

    /** Totems of undying the opponent popped during the fight. */
    public int opponentTotems() {
        return opponentTotems;
    }

    public int myTotems() {
        return myTotems;
    }

    public @Nullable String lastDamageTypeDealt() {
        return lastDamageTypeDealt;
    }

    @Override
    public String toString() {
        return "Fight#" + id + "[" + opponent.name() + ", dealt=" + hitsDealt + " hits/" + String.format(java.util.Locale.ROOT, "%.1f", damageDealt)
                + (isDamageKnown() ? "" : "(hp unknown)") + ", taken=" + hitsTaken + ", totems=" + opponentTotems
                + (endReason == null ? ", active" : ", " + endReason) + "]";
    }
}
