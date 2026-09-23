package dev.skirmish.module.killcam.mixin;

import dev.skirmish.module.killcam.KillCamHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** No HUD (hotbar, hearts, crosshair, chat, HUD elements of all mods) while a replay runs. */
@Mixin(Gui.class)
public abstract class GuiMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void skirmish$killcamHideHud(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (KillCamHooks.active()) {
            ci.cancel();
        }
    }
}
