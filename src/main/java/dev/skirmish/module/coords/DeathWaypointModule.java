package dev.skirmish.module.coords;

import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Ui;
import dev.skirmish.util.ServerContext;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * On the local player's own death (the server's combat-kill packet, via {@link CombatListener#onOwnDeath}) a
 * waypoint with source {@value DeathWaypoints#SOURCE} is added at the death position; only the newest N per server
 * are kept. Waypoints are local data; nothing is sent.
 */
public final class DeathWaypointModule extends Module {
    public static final String ID = "death_waypoint";

    final NumberSetting keep = add(new NumberSetting("keep", 3, 1, 10, 1));
    final BoolSetting select = add(new BoolSetting("select", true));
    final BoolSetting announce = add(new BoolSetting("announce", true));

    public DeathWaypointModule() {
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
        CombatTracker.get().addListener(new CombatListener() {
            @Override
            public void onOwnDeath(OwnDeath death) {
                if (isEnabled()) {
                    mark();
                }
            }
        });
        keep.onChange(v -> {
            if (isEnabled() && Minecraft.getInstance().level != null) {
                prune();
            }
        });
    }

    private void mark() {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        WaypointManager manager = WaypointManager.get();
        String name = Ui.tr("skirmish.death_waypoint.name", new SimpleDateFormat("HH:mm").format(new Date()));
        Waypoint waypoint = manager.add(name, player.getX(), player.getY(), player.getZ(), ServerContext.dimension(), DeathWaypoints.SOURCE);
        log("death waypoint '%s' at %s %s", waypoint.name(), waypoint.coordsText(), waypoint.dimension());
        if (select.get()) {
            manager.select(waypoint.id());
        }
        prune();
        if (announce.get()) {
            player.displayClientMessage(Component.translatable("skirmish.death_waypoint.added", waypoint.name()), false);
        }
    }

    private void prune() {
        WaypointManager manager = WaypointManager.get();
        for (String id : DeathWaypoints.excess(manager.all(), ServerContext.serverKey(), keep.getInt())) {
            manager.remove(id);
            log("dropped old death waypoint %s", id);
        }
    }
}
