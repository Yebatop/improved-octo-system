package dev.skirmish.combat;

import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/** Core module: owns the {@link CombatTracker} settings. Always on (KillCam and KillCard depend on it). */
public final class CombatTrackerModule extends Module implements CombatLogic.Config {
    public static final String ID = "combat";

    private final NumberSetting killWindow = add(new NumberSetting("kill_window", 10, 1, 30, 1).unit(" s"));
    private final NumberSetting fightTimeout = add(new NumberSetting("fight_timeout", 20, 5, 120, 1).unit(" s"));
    private final BoolSetting playersOnly = add(new BoolSetting("players_only", true));
    private final CombatTracker tracker;

    public CombatTrackerModule() {
        super(ID, true);
        this.tracker = CombatTracker.install(this);
    }

    @Override
    public boolean canToggle() {
        return false;
    }

    @Override
    public void onInitialize() {
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) ->
                tracker.reset("world changed to " + level.dimension().identifier()));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> tracker.reset("disconnected"));
    }

    @Override
    public void tick() {
        tracker.tick();
    }

    @Override
    public long killWindowMs() {
        return Math.round(killWindow.get() * 1000);
    }

    @Override
    public long fightTimeoutMs() {
        return Math.round(fightTimeout.get() * 1000);
    }

    @Override
    public boolean playersOnly() {
        return playersOnly.get();
    }
}
