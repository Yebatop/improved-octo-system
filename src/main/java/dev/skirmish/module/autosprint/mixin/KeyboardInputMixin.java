package dev.skirmish.module.autosprint.mixin;

import dev.skirmish.module.autosprint.AutoSprintModule;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** The sprint flag of the key state built each tick reads as held while Auto Sprint is on. */
@Mixin(KeyboardInput.class)
abstract class KeyboardInputMixin {
    @ModifyArg(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Input;<init>(ZZZZZZZ)V"), index = 6)
    private boolean skirmish$autoSprint(boolean sprint) {
        return AutoSprintModule.sprintKey(sprint);
    }
}
