package dev.skirmish.module.analytics.review;

import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * One damage event on me for the death recap: who (if known), the damage type, the health it cost (NaN until the
 * health update arrives) and whether it was a crit.
 */
public final class DamageTaken {
    private final long timeMs;
    private final @Nullable String source;
    private final @Nullable UUID sourceUuid;
    private final String type;
    private final boolean guessed;
    float amount = Float.NaN;
    boolean crit;

    public DamageTaken(long timeMs, @Nullable String source, @Nullable UUID sourceUuid, String type, boolean guessed) {
        this.timeMs = timeMs;
        this.source = source;
        this.sourceUuid = sourceUuid;
        this.type = type;
        this.guessed = guessed;
    }

    public long timeMs() {
        return timeMs;
    }

    /** Attacker name, null for environmental damage or an unknown attacker. */
    public @Nullable String source() {
        return source;
    }

    public @Nullable UUID sourceUuid() {
        return sourceUuid;
    }

    /** Damage type id as reported by the combat tracker. */
    public String type() {
        return type;
    }

    /** The attacker was inferred, not named by the server. */
    public boolean guessed() {
        return guessed;
    }

    public float amount() {
        return amount;
    }

    public boolean crit() {
        return crit;
    }
}
