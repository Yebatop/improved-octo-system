package dev.skirmish.module.hwitems.badges.mixin;

import dev.skirmish.module.hwitems.badges.BadgeRenderer;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code Gui.renderItemHotbar(GuiGraphics, DeltaTracker)}: HEAD/RETURN bracket the hotbar slots. Changes nothing. */
@Mixin(Gui.class)
abstract class GuiHotbarMixin {
    @Inject(method = "renderItemHotbar", at = @At("HEAD"))
    private void skirmish$badgesHotbarBegin(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        BadgeRenderer.enterHotbar();
    }

    @Inject(method = "renderItemHotbar", at = @At("RETURN"))
    private void skirmish$badgesHotbarEnd(GuiGraphics graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        BadgeRenderer.exitHotbar();
    }
}
