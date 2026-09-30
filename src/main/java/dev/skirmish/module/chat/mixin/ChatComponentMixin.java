package dev.skirmish.module.chat.mixin;

import dev.skirmish.module.chat.ChatModule;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Chat module's hooks: the drawing pass gets the one-panel background, new messages go through the module
 * (stacking, marks, event cards), the shown lines follow the open tab, and held cards leave on the chat's tick.
 * Clicks use vanilla's own layout pass, which is left alone.
 */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
    @Unique
    private @Nullable GuiGraphics skirmish$graphics;

    @Inject(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V", at = @At("HEAD"))
    private void skirmish$keepGraphics(GuiGraphics graphics, Font font, int ticks, int mouseX, int mouseY, boolean focused,
                                       boolean insertions, CallbackInfo ci) {
        skirmish$graphics = graphics;
    }

    @ModifyArg(method = "render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;IIIZZ)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;render(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IIZ)V"),
            index = 0)
    private ChatComponent.ChatGraphicsAccess skirmish$panel(ChatComponent.ChatGraphicsAccess access) {
        GuiGraphics graphics = skirmish$graphics;
        return graphics == null ? access : ChatModule.wrap(access, graphics);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void skirmish$onAdd(Component content, @Nullable MessageSignature signature, @Nullable GuiMessageTag tag, CallbackInfo ci) {
        if (ChatModule.onAdd((ChatComponent) (Object) this, content, signature, tag)) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("HEAD"), cancellable = true)
    private void skirmish$tabFilter(GuiMessage message, CallbackInfo ci) {
        if (!ChatModule.shows(message)) {
            ci.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void skirmish$tick(CallbackInfo ci) {
        ChatModule.tick((ChatComponent) (Object) this);
    }
}
