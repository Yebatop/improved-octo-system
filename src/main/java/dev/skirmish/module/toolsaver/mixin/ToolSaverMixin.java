package dev.skirmish.module.toolsaver.mixin;

import dev.skirmish.module.toolsaver.ToolSaverModule;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Holds back your own attack or mining click while the main-hand tool is about to break (Tool Saver). Nothing is
 * sent or done instead; with the module off both methods run untouched.
 */
@Mixin(Minecraft.class)
abstract class ToolSaverMixin {
    @Inject(method = "startAttack", at = @At("HEAD"), cancellable = true)
    private void skirmish$toolSaverStart(CallbackInfoReturnable<Boolean> cir) {
        ToolSaverModule module = ToolSaverModule.instance();
        if (module != null && module.holdStart((Minecraft) (Object) this)) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "continueAttack", at = @At("HEAD"), cancellable = true)
    private void skirmish$toolSaverContinue(boolean held, CallbackInfo ci) {
        ToolSaverModule module = ToolSaverModule.instance();
        if (module != null && module.holdContinue((Minecraft) (Object) this, held)) {
            ci.cancel();
        }
    }
}
