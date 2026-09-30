package dev.skirmish.module.survival;

import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Fight;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.pvp.PvpModule;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * «В бою»: the combat tag (КТ) of the PvP module runs, or the combat tracker has an active fight with a player.
 * Read-only; client thread.
 */
public final class PvpState {
    private static @Nullable PvpModule pvp;
    private static boolean looked;

    private PvpState() {
    }

    public static boolean inPvp() {
        PvpModule module = pvpModule();
        if (module != null && module.isTagged()) {
            return true;
        }
        try {
            for (Fight fight : CombatTracker.get().activeFights()) {
                if (fight.opponent().player()) {
                    return true;
                }
            }
        } catch (IllegalStateException e) {
            // Combat tracker not installed (unit tests, early init).
        }
        return false;
    }

    /** An active fight with this player. */
    public static boolean fightingWith(UUID player) {
        try {
            Fight fight = CombatTracker.get().fightWith(player);
            return fight != null && fight.isActive();
        } catch (IllegalStateException e) {
            return false;
        }
    }

    private static @Nullable PvpModule pvpModule() {
        if (!looked) {
            looked = true;
            try {
                pvp = ModuleManager.get().get(PvpModule.class);
            } catch (IllegalArgumentException e) {
                pvp = null;
            }
        }
        return pvp;
    }
}
