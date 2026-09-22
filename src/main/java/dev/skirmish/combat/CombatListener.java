package dev.skirmish.combat;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;

/**
 * Subscribe with {@code CombatTracker.get().addListener(...)} (usually in {@code Module.onInitialize}).
 * All callbacks run on the client thread, right after vanilla handled the packet. Listeners are called even when
 * the subscribing module is disabled, so check {@code isEnabled()} first. Do not add mixins on the same packet
 * handlers: extend this interface in the core instead.
 */
public interface CombatListener {
    /** Server-confirmed damage to any entity in view (ClientboundDamageEventPacket). */
    default void onDamage(DamageInfo info) {
    }

    /** Health + absorption decrease of a player (or any living entity when "players only" is off). */
    default void onHealthChange(HealthChange change) {
    }

    /** Totem of undying used (entity event 35). {@code fight} is the fight it belongs to, if any. */
    default void onTotemPop(Combatant entity, @Nullable Fight fight) {
    }

    /** Another entity died (entity event 3, or health synced to 0). Not called for the local player. */
    default void onEntityDeath(Combatant entity, @Nullable Fight fight) {
    }

    default void onFightStart(Fight fight) {
    }

    /** Kill attributed to me: opponent died within the kill window after my last hit. Followed by {@link #onFightEnd}. */
    default void onKill(Fight fight) {
    }

    /** Fight finished; see {@link Fight#endReason()}. */
    default void onFightEnd(Fight fight) {
    }

    /** The local player died (ClientboundPlayerCombatKillPacket). Fired before the active fights end. */
    default void onOwnDeath(OwnDeath death) {
    }

    /** Arm swing of any living entity (ClientboundAnimatePacket 0/3). */
    default void onSwing(Entity entity, InteractionHand hand) {
    }

    /** Critical hit particles on {@code target} (ClientboundAnimatePacket 4 = crit, 5 = magic crit). */
    default void onCrit(Entity target, boolean magic) {
    }
}
