package dev.skirmish.module.playermenu;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.friends.PlayerPick;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.KeySetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

/**
 * «Меню игрока»: a key opens a small menu for the player under the crosshair (never an invisible one, never through
 * blocks), or a picker over the tab list when nobody is aimed at. Actions: copy the nick, put a HolyWorld command
 * about the player into the chat box ({@code /ah player}, {@code /pay}, {@code /msg}, {@code /clan invite}), add or
 * remove a friend, see the dossier line. Nothing is ever sent: a command only fills the chat box and the user
 * presses Enter.
 */
public final class PlayerMenuModule extends Module {
    public static final String ID = "player_menu";
    /** How far the key looks for a player when the vanilla crosshair target (attack reach) has none. */
    static final double PICK_RANGE = 64;

    final KeySetting key = add(new KeySetting("key", "key.skirmish.player_menu.open"));
    final BoolSetting auction = add(new BoolSetting("auction", true));
    final BoolSetting pay = add(new BoolSetting("pay", true));
    final BoolSetting message = add(new BoolSetting("message", true));
    final BoolSetting clanInvite = add(new BoolSetting("clan_invite", false));
    final BoolSetting picker = add(new BoolSetting("picker", true));

    public PlayerMenuModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.UTILITY;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        // Polled while the module is off too, so presses made while it was off do not fire later.
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (SkirmishKeys.PLAYER_MENU.consumeClick()) {
                if (isEnabled() && mc.player != null && mc.screen == null) {
                    open(mc);
                }
            }
        });
    }

    private void open(Minecraft mc) {
        Player target = target(mc);
        if (target != null) {
            log("menu for %s", target.getGameProfile().name());
            mc.setScreen(new PlayerMenuScreen(this, target.getGameProfile().name(), target.getUUID()));
        } else if (picker.get()) {
            log("no player aimed at: tab-list picker");
            mc.setScreen(new PlayerMenuScreen(this, null, null));
        } else if (mc.player != null) {
            mc.player.displayClientMessage(net.minecraft.network.chat.Component.translatable("skirmish.player_menu.no_target"), true);
        }
    }

    /** The vanilla crosshair target if it is a visible player, else the first visible player along the view ray. */
    static @Nullable Player target(Minecraft mc) {
        Entity aimed = mc.crosshairPickEntity;
        if (aimed instanceof Player p && p != mc.player && mc.player != null && !p.isInvisibleTo(mc.player) && !p.isSpectator()) {
            return p;
        }
        return PlayerPick.find(mc, PICK_RANGE);
    }
}
