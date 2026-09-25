package dev.skirmish;

import dev.skirmish.combat.CombatTrackerModule;
import dev.skirmish.command.SkirmishCommand;
import dev.skirmish.config.ConfigManager;
import dev.skirmish.config.RemovedModules;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.holyworld.FeatureControl;
import dev.skirmish.holyworld.HolyApi;
import dev.skirmish.hud.Hud;
import dev.skirmish.hud.InterfaceModule;
import dev.skirmish.gui.SkirmishScreen;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.alerts.AlertsModule;
import dev.skirmish.module.anvilcalc.AnvilCalcModule;
import dev.skirmish.module.camera.CameraComfortModule;
import dev.skirmish.module.camera.LowFireModule;
import dev.skirmish.module.coords.CoordsHudModule;
import dev.skirmish.module.coords.DeathWaypointModule;
import dev.skirmish.module.effects.ArmorHudModule;
import dev.skirmish.module.effects.EffectHudModule;
import dev.skirmish.module.fullbright.FullbrightModule;
import dev.skirmish.module.friends.FriendsModule;
import dev.skirmish.module.lag.LagMeterModule;
import dev.skirmish.module.nametag.NametagHpModule;
import dev.skirmish.module.survival.ItemCounterModule;
import dev.skirmish.module.survival.SurvivalAlertsModule;
import dev.skirmish.module.zoom.ZoomModule;
import dev.skirmish.module.clanshare.ClanShareModule;
import dev.skirmish.module.events.EventsModule;
import dev.skirmish.module.gearinspector.GearInspectorModule;
import dev.skirmish.module.killcam.KillCamModule;
import dev.skirmish.module.killcard.KillCardModule;
import dev.skirmish.module.pvp.PvpModule;
import dev.skirmish.module.market.MarketModule;
import dev.skirmish.waypoint.WaypointsModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.nio.file.Path;
import java.util.function.Supplier;

public final class SkirmishClient implements ClientModInitializer {
    public static final String MOD_ID = "skirmish";

    private static @Nullable ConfigManager config;
    private static @Nullable Supplier<Screen> pendingScreen;

    @Override
    public void onInitializeClient() {
        Path dir = configDir();
        DebugLog.init(dir);

        ModuleManager modules = ModuleManager.get();
        modules.register(new CombatTrackerModule());
        modules.register(new WaypointsModule());
        modules.register(new KillCamModule());
        modules.register(new ClanShareModule());
        modules.register(new GearInspectorModule());
        modules.register(new AnvilCalcModule());
        modules.register(new KillCardModule());
        modules.register(new PvpModule());
        modules.register(new EventsModule());
        modules.register(new MarketModule());
        modules.register(new AlertsModule());
        modules.register(new EffectHudModule());
        modules.register(new ArmorHudModule());
        modules.register(new FullbrightModule());
        modules.register(new dev.skirmish.module.invhighlight.InvisibleHighlightModule());
        modules.register(new dev.skirmish.module.autosprint.AutoSprintModule());
        modules.register(new ZoomModule());
        modules.register(new CameraComfortModule());
        modules.register(new LowFireModule());
        modules.register(new CoordsHudModule());
        modules.register(new DeathWaypointModule());
        modules.register(new dev.skirmish.module.regions.RegionBoundsModule());
        modules.register(new dev.skirmish.module.evtimers.EventTimersModule());
        modules.register(new NametagHpModule());
        modules.register(new FriendsModule());
        modules.register(new SurvivalAlertsModule());
        modules.register(new ItemCounterModule());
        modules.register(new LagMeterModule());
        modules.register(new dev.skirmish.module.analytics.review.FightReviewModule());
        modules.register(new dev.skirmish.module.analytics.combo.ComboHudModule());
        modules.register(new dev.skirmish.module.analytics.feed.KillFeedModule());
        modules.register(new dev.skirmish.module.analytics.dossier.DossierModule());
        modules.register(new dev.skirmish.module.hwtimers.ItemTimersModule());
        modules.register(new dev.skirmish.module.tnttimer.TntTimerModule());
        modules.register(new dev.skirmish.module.bosscoach.BossCoachModule());
        modules.register(new dev.skirmish.module.runewindow.RuneWindowModule());
        modules.register(new dev.skirmish.module.damagenumbers.DamageNumbersModule());
        modules.register(new dev.skirmish.module.killfx.KillFxModule());
        modules.register(new dev.skirmish.module.playermenu.PlayerMenuModule());
        modules.register(new dev.skirmish.module.hwitems.badges.TalismanBadgesModule());
        modules.register(new dev.skirmish.module.hwitems.tooltips.HwTooltipsModule());
        modules.register(new dev.skirmish.module.recap.SessionRecapModule());
        modules.register(new dev.skirmish.module.scoreboard.ScoreboardModule());
        InterfaceModule iface = modules.register(new InterfaceModule());

        Runnable restoreRemoved = RemovedModules.read(dir.resolve("config.json"));
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> restoreRemoved.run());
        config = new ConfigManager(dir.resolve("config.json"), modules::all);
        modules.setConfig(config);
        config.load();
        if (dev.skirmish.config.Migrations.run(dir, modules.all())) {
            modules.markDirty();
        }

        SkirmishKeys.register();
        Hud.install(dir.resolve("hud.json"));
        FeatureControl.install(iface::holyworldSafeMode);
        HolyApi.install(iface::holyworldApi);
        modules.initializeAll();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> SkirmishCommand.register(dispatcher));
        ClientTickEvents.END_CLIENT_TICK.register(SkirmishClient::onEndTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            if (config != null) {
                config.saveNow();
            }
            DebugLog.shutdown();
        });
        DebugLog.log("core", "Skirmish initialized with " + modules.all().size() + " modules");
    }

    /** config/skirmish, created on first use. */
    public static Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
    }

    /** Opens a screen at the end of the current tick (safe from chat commands, which close the chat afterwards). */
    public static void openScreenNextTick(Supplier<Screen> screen) {
        pendingScreen = screen;
    }

    private static void onEndTick(Minecraft mc) {
        while (SkirmishKeys.OPEN_MENU.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new SkirmishScreen(null));
            }
        }
        Supplier<Screen> screen = pendingScreen;
        if (screen != null) {
            pendingScreen = null;
            mc.setScreen(screen.get());
        }
        ModuleManager.get().tickAll();
        if (config != null) {
            config.tick();
        }
    }
}
