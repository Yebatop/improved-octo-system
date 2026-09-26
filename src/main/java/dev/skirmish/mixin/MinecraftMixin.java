package dev.skirmish.mixin;

import dev.skirmish.combat.CombatTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Counts my attack attempts for the combat panel's hit ratio and remembers their target (servers that send damage
 * without an attacker need it to tell my hits apart). Read-only: nothing is changed or sent.
 */
@Mixin(Minecraft.class)
abstract class MinecraftMixin {
    @Shadow
    public @Nullable HitResult hitResult;
    @Shadow
    public int missTime;

    @Inject(method = "startAttack", at = @At("HEAD"))
    private void skirmish$countAttack(CallbackInfoReturnable<Boolean> cir) {
        if (missTime <= 0 && hitResult != null && hitResult.getType() != HitResult.Type.BLOCK) {
            try {
                CombatTracker.get().onAttackAttempt(hitResult instanceof EntityHitResult hit ? hit.getEntity().getId() : -1);
            } catch (IllegalStateException ignored) {
                // Combat tracker not installed yet.
            }
        }
    }
}
