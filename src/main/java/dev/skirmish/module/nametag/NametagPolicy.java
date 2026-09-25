package dev.skirmish.module.nametag;

/**
 * Who gets extra nametag content (HP line, friend color). Pure so the rules are unit tested:
 * <ul>
 *     <li>only where vanilla draws the nametag itself (distance, sneaking, teams, F1 stay vanilla);</li>
 *     <li>never for a player invisible to the viewer — HolyWorld bans revealing invisibility in any form, so an
 *     invisible player's nametag is left exactly as the server's team rules make it;</li>
 *     <li>the HP line additionally follows the «когда показывать» mode.</li>
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
}
