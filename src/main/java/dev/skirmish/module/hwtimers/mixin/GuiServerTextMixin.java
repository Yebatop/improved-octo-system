package dev.skirmish.module.hwtimers.mixin;

import dev.skirmish.module.hwtimers.ItemTimersModule;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only: hands the title, subtitle and action-bar text the server just set to the item timers (after vanilla
 * stored it; nothing is changed or cancelled).
 */
@Mixin(Gui.class)
public abstract class GuiServerTextMixin {
    @Inject(method = "setTitle", at = @At("TAIL"))
    private void skirmish$hwTimersTitle(Component component, CallbackInfo ci) {
        ItemTimersModule module = ItemTimersModule.instance();
        if (module != null) {
            module.onServerText("title", component);
        }
    }

    @Inject(method = "setSubtitle", at = @At("TAIL"))
    private void skirmish$hwTimersSubtitle(Component component, CallbackInfo ci) {
        ItemTimersModule module = ItemTimersModule.instance();
        if (module != null) {
            module.onServerText("subtitle", component);
        }
    }

    @Inject(method = "setOverlayMessage", at = @At("TAIL"))
    private void skirmish$hwTimersActionBar(Component component, boolean animateColor, CallbackInfo ci) {
        ItemTimersModule module = ItemTimersModule.instance();
        if (module != null) {
            module.onServerText("actionbar", component);
        }
    }
}
