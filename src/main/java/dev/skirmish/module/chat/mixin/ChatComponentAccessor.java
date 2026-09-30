package dev.skirmish.module.chat.mixin;

import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** The chat's kept messages (newest first) and the rebuild of its shown lines from them. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("allMessages")
    List<GuiMessage> skirmish$allMessages();

    @Invoker("refreshTrimmedMessages")
    void skirmish$refreshTrimmedMessages();
}
