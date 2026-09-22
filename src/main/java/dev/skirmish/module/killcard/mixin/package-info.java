/**
 * Mixins of the KillCard module, listed in skirmish-killcard.mixins.json. Do not target the combat packet handlers
 * hooked by dev.skirmish.mixin.ClientPacketListenerMixin (damage event, entity event, animate, entity data,
 * combat kill); subscribe to dev.skirmish.combat.CombatListener instead.
 */
package dev.skirmish.module.killcard.mixin;
