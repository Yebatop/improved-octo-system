/**
 * Mixins of the GearInspector module, listed in skirmish-gearinspector.mixins.json. Do not target the combat packet handlers
 * hooked by dev.skirmish.mixin.ClientPacketListenerMixin (damage event, entity event, animate, entity data,
 * combat kill); subscribe to dev.skirmish.combat.CombatListener instead.
 */
package dev.skirmish.module.gearinspector.mixin;
