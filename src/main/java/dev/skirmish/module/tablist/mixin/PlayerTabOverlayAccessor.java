package dev.skirmish.module.tablist.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** The player list's header and footer from the server, and its players in vanilla's order (at most 80). */
@Mixin(PlayerTabOverlay.class)
public interface PlayerTabOverlayAccessor {
    @Accessor("header")
    @Nullable Component skirmish$header();

    @Accessor("footer")
    @Nullable Component skirmish$footer();

    @Invoker("getPlayerInfos")
    List<PlayerInfo> skirmish$players();
}
