package dev.skirmish.module.events.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the tab list header and footer the server sent (used to detect the HolyWorld sub-server). */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("header")
    @Nullable Component skirmish$header();

    @Accessor("footer")
    @Nullable Component skirmish$footer();
}
