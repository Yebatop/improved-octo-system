package dev.skirmish.module.worldmap;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.KeySetting;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * «Minimap»: a small map on the HUD drawn from the World Map's tiles (the surface you have loaded), round or
 * square, north up or turned with you. On it: you, the players and mobs your client already has (the server sends
 * them only within its tracking range), your waypoints (pinned to the rim when farther) and the compass letters.
 * Invisible players are left off unless switched on; HolyWorld's rule 2.4 allows minimaps with players since
 * 14 July, and there are no lines or outlines to anyone. Read-only; Feature Control id {@code minimap}.
 */
public final class MinimapModule extends Module {
    public static final String ID = "minimap";
    private static @Nullable MinimapModule instance;

    public enum Shape {
        ROUND, SQUARE
    }

    /** How many blocks the map spans across. */
    public enum Zoom {
        NEAR(48), MID(96), FAR(192), WIDE(384);

        final int blocks;

        Zoom(int blocks) {
            this.blocks = blocks;
        }
    }

    final EnumSetting<Shape> shape = add(new EnumSetting<>("shape", Shape.ROUND));
    final BoolSetting rotate = add(new BoolSetting("rotate", true));
    final EnumSetting<Zoom> zoom = add(new EnumSetting<>("zoom", Zoom.MID));
    final BoolSetting players = add(new BoolSetting("players", true));
    final BoolSetting names = add(new BoolSetting("names", false));
    final BoolSetting hostile = add(new BoolSetting("hostile", true));
    final BoolSetting passive = add(new BoolSetting("passive", false));
    final BoolSetting invisible = add(new BoolSetting("invisible", false));
    final BoolSetting waypoints = add(new BoolSetting("waypoints", true));
    final BoolSetting coords = add(new BoolSetting("coords", true));
    private final KeySetting zoomKey = add(new KeySetting("zoom_key", "key.skirmish.minimap.zoom"));

    /** When the zoom was last changed with the key (the scale shows on the map for a moment). */
    long zoomChangedAt = -1;

    public MinimapModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        instance = this;
        Hud.get().register(new MinimapHud(this));
    }

    /** Whether the minimap is on (the World Map keeps drawing tiles for it even while switched off itself). */
    static boolean on() {
        MinimapModule m = instance;
        return m != null && m.isEnabled();
    }

    static @Nullable MinimapModule active() {
        MinimapModule m = instance;
        return m != null && m.isEnabled() ? m : null;
    }

    @Override
    public void tick() {
        while (SkirmishKeys.MINIMAP_ZOOM.consumeClick()) {
            Zoom[] all = Zoom.values();
            zoom.set(all[(zoom.get().ordinal() + 1) % all.length]);
            zoomChangedAt = Util.getMillis();
        }
        WorldMapModule map = WorldMapModule.instance();
        if (map != null && !map.isEnabled()) {
            map.scanTick();
        }
    }

    /** What kind of marker an entity gets on a map, or null for none (items, boats, you, the invisible). */
    enum Kind {
        PLAYER, HOSTILE, PASSIVE
    }

    @Nullable Kind kind(Entity e, Player self) {
        if (e == self || !e.isAlive() || e.isSpectator()) {
            return null;
        }
        if (e.isInvisibleTo(self) && !invisible.get()) {
            return null;
        }
        if (e instanceof Player p) {
            // Server NPCs are players that are not in the tab list.
            var connection = Minecraft.getInstance().getConnection();
            return players.get() && connection != null && connection.getPlayerInfo(p.getUUID()) != null ? Kind.PLAYER : null;
        }
        if (e instanceof Enemy) {
            return hostile.get() ? Kind.HOSTILE : null;
        }
        if (e instanceof Mob) {
            return passive.get() ? Kind.PASSIVE : null;
        }
        return null;
    }
}
