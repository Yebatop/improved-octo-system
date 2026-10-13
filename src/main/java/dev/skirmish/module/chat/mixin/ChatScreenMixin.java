package dev.skirmish.module.chat.mixin;

import dev.skirmish.module.chat.ChatTabsBar;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.MouseButtonEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The Chat module's tab pills on the open chat: drawn after it, and clicked before vanilla looks at the click. */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void skirmish$tabs(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ChatTabsBar.render(graphics, mouseX, mouseY);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void skirmish$tabClick(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (ChatTabsBar.click(event.x(), event.y(), event.button())) {
            cir.setReturnValue(true);
        }
    }
}
