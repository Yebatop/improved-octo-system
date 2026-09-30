package dev.skirmish.module.alerts;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.network.chat.CommonComponents;
import org.jspecify.annotations.Nullable;

/**
 * Puts a confirmation in front of the pause screen's «Отключиться» (multiplayer only) while logging out would drop
 * items or while a fight is on. No mixin: a Fabric screen event takes the click (or Enter/Space on the focused
 * button) before vanilla sees it and opens {@link LeaveConfirmScreen}; «Выйти всё равно» then presses the vanilla
 * button, so vanilla's own disconnect flow (draft report prompt included) runs unchanged.
 */
final class DisconnectGuard {
    private DisconnectGuard() {
    }

    static void install(AlertsModule module) {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof PauseScreen pause)) {
                return;
            }
            Button disconnect = findDisconnect(pause);
            if (disconnect == null) {
                return;
            }
            ScreenMouseEvents.allowMouseClick(pause).register((s, event) -> {
                if (event.button() != 0 || !disconnect.visible || !disconnect.active || !disconnect.isMouseOver(event.x(), event.y())) {
                    return true;
                }
                return !intercept(module, pause, disconnect);
            });
            ScreenKeyboardEvents.allowKeyPress(pause).register((s, event) -> {
                if (!event.isSelection() || s.getFocused() != disconnect || !disconnect.active) {
                    return true;
                }
                return !intercept(module, pause, disconnect);
            });
        });
    }

    /** Opens the dialog when needed; true when the press was taken. */
    private static boolean intercept(AlertsModule module, PauseScreen pause, Button disconnect) {
        if (!module.shouldConfirmDisconnect()) {
            return false;
        }
        module.log("disconnect pressed with logout risk %s and %d active fights: asking to confirm",
                module.tally(), module.fightsForConfirm());
        Minecraft.getInstance().setScreen(new LeaveConfirmScreen(module, pause, disconnect));
        return true;
    }

    /** The multiplayer «Отключиться» button ({@code menu.disconnect}); singleplayer's «Выйти в меню» is left alone. */
    private static @Nullable Button findDisconnect(PauseScreen pause) {
        for (AbstractWidget widget : Screens.getButtons(pause)) {
            if (widget instanceof Button button && CommonComponents.GUI_DISCONNECT.equals(button.getMessage())) {
                return button;
            }
        }
        return null;
    }
}
