package dev.skirmish.waypoint;

import dev.skirmish.gui.Texts;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Waypoint HUD: world labels drawn at the projected screen position of each waypoint (any distance, independent of
 * the world render pipeline), and the pill of the selected waypoint (ClanShare marker in the mockup) whose arrow
 * points to it relative to the player's yaw.
 */
final class WaypointHud implements HudElement {
    private final WaypointsModule module;
    private final WaypointManager manager;

    WaypointHud(WaypointsModule module, WaypointManager manager) {
        this.module = module;
        this.manager = manager;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.player == null || mc.level == null || mc.options.hideGui) {
            return;
        }
        List<Waypoint> here = manager.current();
        if (module.showLabels.get() && !here.isEmpty()) {
            Ui ui = Ui.begin(graphics);
            try {
                renderLabels(mc, ui, here);
            } finally {
                ui.end();
            }
        }
    }

    private void renderLabels(Minecraft mc, Ui ui, List<Waypoint> waypoints) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Vec3 cam = camera.position();
        Vector3fc forward = camera.forwardVector();
        double maxDistance = module.maxLabelDistance.get();
        float scale = module.labelScale.getFloat();
        float width = ui.width();
        float height = ui.height();
        Waypoint selected = manager.selected();
        String l = "layout.hud.";
        float stroke = ui.num("stroke.width");

        for (Waypoint waypoint : waypoints) {
            Vec3 target = new Vec3(waypoint.x(), waypoint.y() + 1.0, waypoint.z());
            Vec3 rel = target.subtract(cam);
            double distance = rel.length();
            if (maxDistance > 0 && distance > maxDistance) {
                continue;
            }
            double depth = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
            if (depth < 0.1) {
                continue;
            }
            Vec3 ndc = mc.gameRenderer.projectPointToScreen(target);
            if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y) || Math.abs(ndc.x) > 1.1 || Math.abs(ndc.y) > 1.1) {
                continue;
            }
            float sx = (float) ((ndc.x + 1.0) * 0.5 * width);
            float sy = (float) ((1.0 - ndc.y) * 0.5 * height);

            String name = ui.ellipsize("world_label", waypoint.name(), ui.num(l + "world_label_max"));
            String dist = formatDistance(distance);
            float padX = ui.num(l + "world_label_pad_x");
            float padY = ui.num(l + "world_label_pad_y");
            float gap = ui.num(l + "world_label_gap");
            float lh = Math.max(ui.lineHeight("world_label"), ui.lineHeight("world_label_dist"));
            float w = stroke * 2 + padX * 2 + ui.textWidth("world_label", name) + gap + ui.textWidth("world_label_dist", dist);
            float h = stroke * 2 + padY * 2 + lh;
            float stem = ui.num(l + "world_label_stem");
            boolean isSelected = waypoint.equals(selected);
            int color = 0xFF000000 | waypoint.color();

            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(Math.round(sx), Math.round(sy));
            pose.scale(scale, scale);
            float x = Math.round(-w / 2f);
            float y = -stem - h;
            ui.box(x, y, w, h, h / 2f, ui.color("panel"), isSelected ? ui.color("accent") : ui.color("stroke"));
            float tx = x + stroke + padX;
            ui.circle(tx - padX / 2f + 1f, y + h / 2f, ui.num(l + "world_label_dot"), color);
            tx = ui.textCentered("world_label", name, tx + ui.num(l + "world_label_dot"), y, h) + gap;
            ui.textCentered("world_label_dist", dist, tx, y, h);
            ui.rect(-stroke, -stem, stroke * 2, stem, stroke, isSelected ? ui.color("accent") : ui.color("marker"));
            pose.popMatrix();
        }
    }

    static String formatDistance(double distance) {
        if (distance >= 10_000) {
            return Ui.decimal(distance / 1000.0, 1) + " " + Texts.unit("km");
        }
        return Math.round(distance) + " " + Texts.unit("m");
    }

    /** Selected waypoint pill: arrow, «Клан»/«Метка», name, coordinates, distance. */
    static final class Pill extends HudBlock {
        private final WaypointsModule module;
        private final WaypointManager manager;

        Pill(WaypointsModule module, WaypointManager manager) {
            super("waypoint", "skirmish.hud.element.waypoint", new Placement(0.5f, 0, 0.5f, 0, 0, 18));
            this.module = module;
            this.manager = manager;
        }

        private @Nullable Waypoint target() {
            Waypoint selected = manager.selected();
            return selected != null && manager.current().contains(selected) ? selected : null;
        }

        @Override
        public boolean enabled() {
            return module.isEnabled() && module.showPill.get();
        }

        @Override
        public boolean shown() {
            return target() != null;
        }

        private record Parts(String label, String name, String coords, String dist, float angle) {
        }

        private Parts parts(Ui ui, boolean preview) {
            Waypoint w = target();
            Minecraft mc = Minecraft.getInstance();
            if (w == null || mc.player == null) {
                return new Parts(Ui.tr("skirmish.hud.waypoint.clan"), Ui.tr("skirmish.hud.waypoint.sample"),
                        "1240 64 −330", formatDistance(142), 35f);
            }
            Player player = mc.player;
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
            Vec3 pos = player.getPosition(partial);
            double dx = w.x() - pos.x;
            double dz = w.z() - pos.z;
            float targetYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float relative = Mth.wrapDegrees(targetYaw - player.getViewYRot(partial));
            String label = w.source().startsWith("clanshare") ? Ui.tr("skirmish.hud.waypoint.clan") : Ui.tr("skirmish.hud.waypoint.own");
            String name = ui.ellipsize("pill_name", w.name(), ui.num("layout.hud.pill_name_max"));
            String coords = String.format(Locale.ROOT, "%d %d %d", (long) Math.floor(w.x()), (long) Math.floor(w.y()), (long) Math.floor(w.z()))
                    .replace('-', '−');
            return new Parts(label, name, coords, formatDistance(Math.sqrt(w.distanceSq(pos.x, pos.y, pos.z))), relative);
        }

        @Override
        public float width(Ui ui, boolean preview) {
            Parts p = parts(ui, preview);
            String l = "layout.hud.";
            float gap = ui.num(l + "pill_gap");
            return ui.num("stroke.width") * 2 + ui.num(l + "pill_pad_left") + ui.num(l + "pill_pad_right")
                    + ui.num(l + "pill_icon_circle") + ui.textWidth("pill_label", p.label()) + ui.textWidth("pill_name", p.name())
                    + ui.num("stroke.width") + ui.textWidth("pill_coords", p.coords()) + ui.textWidth("pill_dist", p.dist()) + gap * 5;
        }

        @Override
        public float height(Ui ui, boolean preview) {
            String l = "layout.hud.";
            return ui.num("stroke.width") * 2 + ui.num(l + "pill_pad_top") + ui.num(l + "pill_pad_bottom") + ui.num(l + "pill_icon_circle");
        }

        @Override
        public void render(Ui ui, float x, float y, boolean preview) {
            Parts p = parts(ui, preview);
            String l = "layout.hud.";
            float w = width(ui, preview);
            float h = height(ui, preview);
            HudStyle.pill(ui, x, y, w, h);
            float gap = ui.num(l + "pill_gap");
            float circle = ui.num(l + "pill_icon_circle");
            float cx = x + ui.num("stroke.width") + ui.num(l + "pill_pad_left");
            ui.circle(cx + circle / 2f, y + h / 2f, circle, ui.color("accent_18"));
            float icon = ui.num(l + "pill_icon");
            Icons.navArrow(ui, cx + (circle - icon) / 2f, y + (h - icon) / 2f, icon, 2.2f, ui.color("accent"), p.angle());
            cx += circle + gap;
            cx = ui.textCentered("pill_label", p.label(), cx, y, h) + gap;
            cx = ui.textCentered("pill_name", p.name(), cx, y, h) + gap;
            float sepH = ui.num(l + "pill_separator_height");
            ui.rect(cx, y + (h - sepH) / 2f, ui.num("stroke.width"), sepH, 0, ui.color("separator"));
            cx += ui.num("stroke.width") + gap;
            cx = ui.textCentered("pill_coords", p.coords(), cx, y, h) + gap;
            ui.textCentered("pill_dist", p.dist(), cx, y, h);
        }
    }
}
