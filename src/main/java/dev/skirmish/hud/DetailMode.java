package dev.skirmish.hud;

/**
 * How much a HUD panel with a long list shows: a short version, the short version with the full one while the
 * «Подробности» key ({@code SkirmishKeys.DETAILS}) is held, or always the full one.
 */
public enum DetailMode {
    COMPACT, HOLD, FULL;

    /** Whether the full version is shown, given whether the details key is held right now. */
    public boolean expanded(boolean keyDown) {
        return switch (this) {
            case COMPACT -> false;
            case HOLD -> keyDown;
            case FULL -> true;
        };
    }
}
