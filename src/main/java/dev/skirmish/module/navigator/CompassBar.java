package dev.skirmish.module.navigator;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * A compass strip at the top of the screen: cardinal and intercardinal letters, ticks every 15°, your waypoints as
 * diamonds (the selected one bright, with its distance), the next portal of the route and home. First in the
 * top-centre column; only your own markers, never players.
 */
final class CompassBar extends HudBlock {
    private static final String L = NavigatorModule.L;
    private static final String[] CARDINALS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};
    private final NavigatorModule module;

    CompassBar(NavigatorModule module) {
        super("compass", "skirmish.hud.element.compass", new Placement(0.5f, 0, 0.5f, 0, 0, 18));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.compass.get();
    }

    @Override
    public boolean shown() {
        return Minecraft.getInstance().player != null;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "compass_width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "compass_height");
    }

    /** Compass heading (0 = north, 90 = east) of a Minecraft yaw. */
    static float heading(float yaw) {
        return Mth.wrapDegrees(yaw + 180f) < 0 ? Mth.wrapDegrees(yaw + 180f) + 360f : Mth.wrapDegrees(yaw + 180f);
    }

    /** Compass heading from (px, pz) to (tx, tz). */
    static float bearing(double px, double pz, double tx, double tz) {
        return heading((float) Math.toDegrees(Math.atan2(-(tx - px), tz - pz)));
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.pill(ui, x, y, w, h);
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float facing = player == null ? 0f : heading(player.getViewYRot(partial));
        float span = ui.num(L + "compass_span");
        float pad = ui.num(L + "compass_pad");
        float cx = x + w / 2f;
        float half = w / 2f - pad;
        ui.pushAlpha(1f);
        for (int b = 0; b < 360; b += 15) {
            float rel = Mth.wrapDegrees(b - facing);
            if (Math.abs(rel) > span / 2f) {
                continue;
            }
            float px = cx + rel / (span / 2f) * half;
            float edge = 1f - Math.abs(rel) / (span / 2f);
            ui.pushAlpha(0.25f + 0.75f * edge);
            if (b % 45 == 0) {
                String label = Ui.tr("skirmish.navigator.dir." + CARDINALS[b / 45]);
                String style = b % 90 == 0 ? "nav_cardinal" : "nav_inter";
                ui.textCentered(style, label, px - ui.textWidth(style, label) / 2f, y, h, ui.color(b == 0 ? "bad" : "text"));
            } else {
                float th = ui.num(L + "compass_tick");
                ui.rect(px - 0.5f, y + (h - th) / 2f, 1f, th, 0f, ui.color("text_4"));
            }
            ui.popAlpha();
        }
        if (player != null) {
            Waypoint selected = WaypointManager.get().selected();
            float size = ui.num(L + "compass_marker");
            for (Waypoint wp : WaypointManager.get().current()) {
                boolean sel = selected != null && selected.id().equals(wp.id());
                if (!sel && module.beams.get() != NavigatorModule.Beams.ALL && !"home".equals(wp.source())) {
                    // Keep the strip readable: all waypoints only with «все лучи», else target and home.
                    if (WaypointManager.get().current().size() > Theme.get().integer(L + "compass_all_up_to")) {
                        continue;
                    }
                }
                float rel = Mth.wrapDegrees(bearing(player.getX(), player.getZ(), wp.x(), wp.z()) - facing);
                if (Math.abs(rel) > span / 2f) {
                    continue;
                }
                float px = cx + rel / (span / 2f) * half;
                int color = 0xFF000000 | (wp.color() & 0xFFFFFF);
                float s = sel ? size * 1.35f : size;
                float my = y + h - s * 1.2f - 2f;
                ui.triangle(px - s, my, px + s, my, px, my - s * 1.2f, 1f, color);
                ui.triangle(px - s, my, px + s, my, px, my + s * 1.2f, 1f, color);
                if (sel) {
                    ui.ring(px, my, s * 3.2f, 1.5f, ui.color("accent"));
                }
            }
            double[] next = module.nextPoint();
            if (next != null && next[2] == 1) {
                float rel = Mth.wrapDegrees(bearing(player.getX(), player.getZ(), next[0], next[1]) - facing);
                if (Math.abs(rel) <= span / 2f) {
                    float px = cx + rel / (span / 2f) * half;
                    ui.ring(px, y + h / 2f, size * 2.6f, 2f, ui.color("nav_portal"));
                }
            }
        }
        // Centre caret.
        float caret = ui.num(L + "compass_caret");
        ui.triangle(cx - caret, y + 1, cx + caret, y + 1, cx, y + 1 + caret * 1.2f, 1f, ui.color("accent"));
        ui.popAlpha();
    }
}
