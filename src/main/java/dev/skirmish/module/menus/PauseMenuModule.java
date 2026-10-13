package dev.skirmish.module.menus;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * «Pause Menu»: Esc opens Skirmish's pause screen — session stats (kills, deaths, K/D, time played) and quick
 * buttons for the mod menu, HUD editor, waypoints, map, settings, advancements and statistics. Screen swap only
 * (F3+Esc still pauses without a menu); Feature Control id {@code pause_menu}.
 */
public final class PauseMenuModule extends Module {
    public static final String ID = "pause_menu";
    private static volatile @Nullable PauseMenuModule instance;

    final BoolSetting stats = add(new BoolSetting("stats", true));
    private long joinedAt = -1;

    public PauseMenuModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.INTERFACE;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        instance = this;
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> joinedAt = Util.getMillis());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> joinedAt = -1);
    }

    public static boolean active() {
        PauseMenuModule m = instance;
        return m != null && m.isEnabled();
    }

    static boolean statsOn() {
        PauseMenuModule m = instance;
        return m == null || m.stats.get();
    }

    /** Milliseconds since joining this server/world, or -1. */
    public static long playedMs() {
        PauseMenuModule m = instance;
        return m == null || m.joinedAt < 0 ? -1 : Util.getMillis() - m.joinedAt;
    }
}
