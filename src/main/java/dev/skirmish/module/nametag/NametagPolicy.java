package dev.skirmish.module.nametag;

/**
 * Who gets extra nametag content (HP line, friend color). Pure so the rules are unit tested:
 * <ul>
 *     <li>the vanilla-style line: only where vanilla draws the nametag itself (distance, sneaking, teams, F1 stay
 *     vanilla), and never on an invisible player's nametag (friend colour included);</li>
 *     <li>the Skirmish plate: only for players in plain line of sight within the chosen distance (never through
 *     blocks). Invisible players get a plate only when allowed: HolyWorld's rules allow HP indicators on invisible
 *     players, and the server can still switch that off through Feature Control ({@code hp_invisible});</li>
 *     <li>both follow the «когда показывать» mode.</li>
 * </ul>
 */
public final class NametagPolicy {
    /** When the HP line is shown. */
    public enum Show {
        /** Whenever vanilla shows the nametag. */
        ALWAYS,
        /** While I am in PvP (combat tag or an active fight). */
        COMBAT,
        /** Only players I am fighting right now. */
        OPPONENTS
    }

    private NametagPolicy() {
    }

    /** Friend color / marker: any visible nametag of a visible player. */
    public static boolean decorate(boolean vanillaNameTagShown, boolean invisibleToViewer) {
        return vanillaNameTagShown && !invisibleToViewer;
    }

    public static boolean showHp(boolean vanillaNameTagShown, boolean invisibleToViewer, Show mode, boolean inPvp,
                                 boolean opponent) {
        if (!decorate(vanillaNameTagShown, invisibleToViewer)) {
            return false;
        }
        return switch (mode) {
            case ALWAYS -> true;
            case COMBAT -> inPvp || opponent;
            case OPPONENTS -> opponent;
        };
    }

    /** The Skirmish plate over a player's head. */
    public static boolean showPlate(boolean inLineOfSight, boolean withinDistance, boolean invisibleToViewer,
                                    boolean allowInvisible, Show mode, boolean inPvp, boolean opponent) {
        if (!inLineOfSight || !withinDistance || invisibleToViewer && !allowInvisible) {
            return false;
        }
        return switch (mode) {
            case ALWAYS -> true;
            case COMBAT -> inPvp || opponent;
            case OPPONENTS -> opponent;
        };
    }
}
