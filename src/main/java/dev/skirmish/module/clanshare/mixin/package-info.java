/**
 * Mixins of the ClanShare module, listed in skirmish-clanshare.mixins.json. Do not target the combat packet handlers
 * hooked by dev.skirmish.mixin.ClientPacketListenerMixin (damage event, entity event, animate, entity data,
 * combat kill); subscribe to dev.skirmish.combat.CombatListener instead.
 */
package dev.skirmish.module.clanshare.mixin;
