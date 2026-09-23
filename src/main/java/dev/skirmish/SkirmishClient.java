package dev.skirmish;

import dev.skirmish.combat.CombatTrackerModule;
import dev.skirmish.command.SkirmishCommand;
import dev.skirmish.config.ConfigManager;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.holyworld.FeatureControl;
import dev.skirmish.holyworld.HolyApi;
import dev.skirmish.hud.Hud;
import dev.skirmish.hud.InterfaceModule;
import dev.skirmish.gui.SkirmishScreen;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.anvilcalc.AnvilCalcModule;
import dev.skirmish.module.clanshare.ClanShareModule;
import dev.skirmish.module.gearinspector.GearInspectorModule;
import dev.skirmish.module.killcam.KillCamModule;
import dev.skirmish.module.killcard.KillCardModule;
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
        InterfaceModule iface = modules.register(new InterfaceModule());

        config = new ConfigManager(dir.resolve("config.json"), modules::all);
        modules.setConfig(config);
        config.load();

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
