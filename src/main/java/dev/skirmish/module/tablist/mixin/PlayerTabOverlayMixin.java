package dev.skirmish.module.tablist.mixin;

import dev.skirmish.module.tablist.TabListRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla still decides when the list shows (the key held); what it draws is the Tab List module's panel when on. */
@Mixin(PlayerTabOverlay.class)
abstract class PlayerTabOverlayMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void skirmish$render(GuiGraphics graphics, int width, Scoreboard scoreboard, @Nullable Objective objective, CallbackInfo ci) {
        if (TabListRenderer.render((PlayerTabOverlay) (Object) this, graphics, width, scoreboard, objective)) {
            ci.cancel();
        }
    }
}
