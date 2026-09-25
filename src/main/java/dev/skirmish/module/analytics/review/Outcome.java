package dev.skirmish.module.analytics.review;

import dev.skirmish.combat.FightEndReason;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/** How a reviewed fight (or a death without a fight) ended, with its colour token. */
public enum Outcome {
    WIN("good"),
    OPPONENT_DIED("text_2"),
    LOSS("bad"),
    DIED("bad"),
    SPLIT("text_2"),
    INTERRUPTED("text_2"),
    DEATH("bad");

    private final String color;

    Outcome(String color) {
        this.color = color;
    }

    public String color() {
        return color;
    }

    public boolean good() {
        return this == WIN;
    }

    public boolean bad() {
        return this == LOSS || this == DIED || this == DEATH;
    }

    public String langKey() {
        return "skirmish.analytics.outcome." + name().toLowerCase(Locale.ROOT);
    }

    /**
     * @param reason          why the fight ended; null for a death outside any fight
     * @param killedByOpponent whether my killer was this fight's opponent (for {@link FightEndReason#OWN_DEATH})
     */
    public static Outcome of(@Nullable FightEndReason reason, boolean killedByOpponent) {
        if (reason == null) {
            return DEATH;
        }
        return switch (reason) {
            case KILL -> WIN;
            case OPPONENT_DIED -> OPPONENT_DIED;
            case OWN_DEATH -> killedByOpponent ? LOSS : DIED;
            case TIMEOUT -> SPLIT;
            case WORLD_CHANGE -> INTERRUPTED;
        };
    }
}
