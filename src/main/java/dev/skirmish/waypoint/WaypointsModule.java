package dev.skirmish.waypoint;

import dev.skirmish.module.Category;
import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Module;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.text.SimpleDateFormat;
import java.util.Date;

/** Core module: waypoint labels in the world, HUD arrow, keybinds. The data lives in {@link WaypointManager}. */
public final class WaypointsModule extends Module {
    public static final String ID = "waypoints";

    final ActionSetting openList = add(new ActionSetting("open_list", () -> {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        mc.setScreen(new dev.skirmish.gui.WaypointListScreen(mc.screen));
    }));
    final BoolSetting showLabels = add(new BoolSetting("show_labels", true));
    final NumberSetting maxLabelDistance = add(new NumberSetting("max_label_distance", 0, 0, 10000, 50).unit(" m"));
    final NumberSetting labelScale = add(new NumberSetting("label_scale", 1.0, 0.5, 2.0, 0.1));
    final BoolSetting edgeArrow = add(new BoolSetting("edge_arrow", true));
    final BoolSetting showPill = add(new BoolSetting("show_pill", true));

    private final WaypointManager manager;

    @Override
    public int menuOrder() {
        return 10;
    }

    @Override
    public Category category() {
        return Category.WORLD;
    }

    public WaypointsModule() {
        super(ID, true);
        manager = WaypointManager.install(FabricLoader.getInstance().getConfigDir().resolve("skirmish").resolve("waypoints.json"), this);
    }

    @Override
    public void onInitialize() {
        manager.load();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("skirmish", "waypoints"),
                new WaypointHud(this, manager));
        dev.skirmish.hud.Hud.get().register(new WaypointHud.Pill(this, manager));
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (SkirmishKeys.WAYPOINT_ADD.consumeClick()) {
            String name = "WP " + new SimpleDateFormat("HH:mm:ss").format(new Date());
            Waypoint waypoint = manager.addHere(name);
            if (waypoint != null && mc.player != null) {
                mc.player.displayClientMessage(Component.translatable("skirmish.waypoint.added", waypoint.name(), waypoint.coordsText()), true);
            }
        }
        while (SkirmishKeys.WAYPOINT_CYCLE.consumeClick()) {
            Waypoint next = manager.cycleSelection();
            if (mc.player != null) {
                mc.player.displayClientMessage(next == null
                        ? Component.translatable("skirmish.waypoint.none_here")
                        : Component.translatable("skirmish.waypoint.selected", next.name()), true);
            }
        }
    }
}
