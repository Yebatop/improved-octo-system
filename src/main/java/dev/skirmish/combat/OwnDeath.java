package dev.skirmish.combat;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * @param message death message exactly as the server sent it (shown on the death screen); null in unit tests
 * @param killer  attacker of the last damage on me within the kill window, if any
 * @param fights  fights that were active at the moment of death (they end with OWN_DEATH right after)
 */
public record OwnDeath(@Nullable Component message, @Nullable Combatant killer, List<Fight> fights, long timeMs) {
}
