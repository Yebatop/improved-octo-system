package dev.skirmish.module.alerts;

import dev.skirmish.combat.CombatTracker;
import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Module;
import dev.skirmish.module.alerts.parse.IntrusionLine;
import dev.skirmish.module.alerts.parse.LossItems;
import dev.skirmish.module.market.parse.HwText;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Alerts: HolyWorld danger warnings.
 * <ul>
 *     <li>A HUD banner while logging out would drop items (shulker mode: 2+ shulker boxes or backpacks; Prime
 *     Elements), and a «Выйти всё равно / Остаться» dialog on the pause screen's «Отключиться» then or during a
 *     fight. The player always decides; nothing happens automatically.</li>
 *     <li>The region intrusion chat line becomes a toast with a sound and a short history.</li>
 * </ul>
 * Reads the local inventory and system chat only; sends nothing.
 */
public final class AlertsModule extends Module {
    public static final String ID = "alerts";
    private static final int SCAN_INTERVAL_TICKS = 10;

    final BoolSetting logoutWarning = (BoolSetting) add(new BoolSetting("logout_warning", true)).feature("logout_warning");
    final BoolSetting confirmDisconnect = (BoolSetting) add(new BoolSetting("confirm_disconnect", true)).feature("logout_warning");
    final BoolSetting confirmInCombat = (BoolSetting) add(new BoolSetting("confirm_in_combat", true)).feature("logout_warning");
    final BoolSetting regionAlerts = (BoolSetting) add(new BoolSetting("region_alerts", true)).feature("region_alerts");
    final BoolSetting regionSound = (BoolSetting) add(new BoolSetting("region_sound", true)).feature("region_alerts");
    final NumberSetting regionHistory = (NumberSetting) add(new NumberSetting("region_history", 3, 0, 10, 1)).feature("region_alerts");

    /** One intrusion: nick and local time it was seen. */
    record Intrusion(String nick, long time) {
    }

    private LossItems.Tally tally = LossItems.Tally.EMPTY;
    private int scanCountdown;
    private final Deque<Intrusion> intrusions = new ArrayDeque<>();

    public AlertsModule() {
        super(ID, true);
        confirmDisconnect.under(logoutWarning).visibleWhen(logoutWarning::get);
        regionSound.under(regionAlerts).visibleWhen(regionAlerts::get);
        regionHistory.under(regionAlerts).visibleWhen(regionAlerts::get);
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new LogoutBanner(this));
        Hud.get().register(new RegionToast(this));
        DisconnectGuard.install(this);
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> onSystemMessage(message, overlay));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            tally = LossItems.Tally.EMPTY;
            intrusions.clear();
        }));
    }

    @Override
    protected void onDisable() {
        tally = LossItems.Tally.EMPTY;
    }

    @Override
    public void tick() {
        if (--scanCountdown > 0) {
            return;
        }
        scanCountdown = SCAN_INTERVAL_TICKS;
        LossItems.Tally before = tally;
        tally = logoutWarning.get() && HolyWorld.isConnected() ? scan() : LossItems.Tally.EMPTY;
        if (!tally.equals(before)) {
            log("logout risk: %d shulker boxes, %d backpacks, %d Elements (at risk: %s)",
                    tally.shulkers(), tally.backpacks(), tally.elements(), tally.atRisk());
        }
    }

    private static LossItems.Tally scan() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return LossItems.Tally.EMPTY;
        }
        Inventory inventory = player.getInventory();
        LossItems.Tally t = LossItems.Tally.EMPTY;
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            boolean shulker = stack.is(ItemTags.SHULKER_BOXES);
            List<String> lore = List.of();
            if (shulker) {
                ItemLore itemLore = stack.get(DataComponents.LORE);
                if (itemLore != null) {
                    lore = new ArrayList<>();
                    for (Component line : itemLore.lines()) {
                        lore.add(line.getString());
                    }
                }
            }
            t = LossItems.add(t, LossItems.classify(shulker, stack.getHoverName().getString(), lore), stack.getCount());
        }
        return t;
    }

    /** What would drop on logout now (empty off HolyWorld or with the warning off). */
    LossItems.Tally tally() {
        return tally;
    }

    /** The banner condition: HolyWorld, warning on, something would drop. */
    boolean logoutRisk() {
        return isEnabled() && logoutWarning.get() && tally.atRisk();
    }

    /** Active fights the combat tracker knows of (0 when the dialog should not care about fights). */
    int fightsForConfirm() {
        if (!isEnabled() || !confirmInCombat.get()) {
            return 0;
        }
        return CombatTracker.get().activeFights().size();
    }

    boolean shouldConfirmDisconnect() {
        return isEnabled() && ((confirmDisconnect.get() && logoutRisk()) || fightsForConfirm() > 0);
    }

    /** Newest first. */
    List<Intrusion> intrusions() {
        return List.copyOf(intrusions);
    }

    private void onSystemMessage(Component message, boolean overlay) {
        if (!isEnabled()) {
            return;
        }
        String text = message.getString();
        if (overlay) {
            // Capture help: the shulker/element mode notice is not public yet (likely the action bar).
            if (isDebug()) {
                String key = HwText.normalize(text);
                if (key.contains("шалкер") || key.contains("элемент") || key.contains("рюкзак")) {
                    log("action bar candidate (shulker/element mode): %s", text);
                }
            }
            return;
        }
        if (!regionAlerts.get() || !HolyWorld.isConnected()) {
            return;
        }
        String nick = IntrusionLine.intruder(text);
        if (nick == null) {
            return;
        }
        log("region intrusion by %s", nick);
        intrusions.addFirst(new Intrusion(nick, System.currentTimeMillis()));
        while (intrusions.size() > 1 + regionHistory.getInt()) {
            intrusions.removeLast();
        }
        if (regionSound.get()) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f, 1.0f));
        }
    }
}
