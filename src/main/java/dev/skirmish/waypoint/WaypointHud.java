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

    private record Marker(Waypoint waypoint, float x, float y, double distance, boolean selected) {
    }

    /**
     * World markers: a diamond in the waypoint's colour on a short stem, with a pulsing ring on the selected one.
     * Far from the crosshair only the distance chip shows; as the crosshair nears a marker its label opens up to the
     * name and distance, then the coordinates. The selected waypoint, when off screen or behind, gets an arrow at the
     * screen edge pointing to it.
     */
    private void renderLabels(Minecraft mc, Ui ui, List<Waypoint> waypoints) {
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Vec3 cam = camera.position();
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        double maxDistance = module.maxLabelDistance.get();
        float width = ui.width();
        float height = ui.height();
        Waypoint selected = manager.selected();
        List<Marker> markers = new java.util.ArrayList<>();

        for (Waypoint waypoint : waypoints) {
            Vec3 target = new Vec3(waypoint.x(), waypoint.y() + 1.0, waypoint.z());
            Vec3 rel = target.subtract(cam);
            double distance = rel.length();
            if (maxDistance > 0 && distance > maxDistance) {
                continue;
            }
            boolean isSelected = waypoint.equals(selected);
            double depth = rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z();
            Vec3 ndc = depth < 0.1 ? null : mc.gameRenderer.projectPointToScreen(target);
            boolean onScreen = ndc != null && Double.isFinite(ndc.x) && Double.isFinite(ndc.y)
                    && Math.abs(ndc.x) <= 1.0 && Math.abs(ndc.y) <= 1.0;
            if (!onScreen) {
                if (isSelected && module.edgeArrow.get()) {
                    double right = -(rel.x * left.x() + rel.y * left.y() + rel.z * left.z());
                    double upward = rel.x * up.x() + rel.y * up.y() + rel.z * up.z();
                    edgeArrow(ui, waypoint, right, upward, distance);
                }
                continue;
            }
            markers.add(new Marker(waypoint, (float) ((ndc.x + 1.0) * 0.5 * width), (float) ((1.0 - ndc.y) * 0.5 * height),
                    distance, isSelected));
        }
        markers.sort(java.util.Comparator.comparingDouble(Marker::distance).reversed());
        for (Marker marker : markers) {
            drawMarker(ui, marker);
        }
    }

    /** Marker scale for a distance: 1 up close, down to {@code wp_min_scale} far away. */
    static float distanceScale(double distance, float near, float far, float min) {
        if (distance <= near) {
            return 1f;
        }
        float t = (float) Math.min(1.0, (distance - near) / Math.max(0.001, far - near));
        return 1f + (min - 1f) * t;
    }

    /** 0 away from the crosshair, 1 on it (smoothstep over {@code radius}). */
    static float focus(float dx, float dy, float radius) {
        float t = 1f - (float) Math.min(1.0, Math.hypot(dx, dy) / radius);
        return t * t * (3f - 2f * t);
    }

    private void drawMarker(Ui ui, Marker m) {
        String l = "layout.hud.";
        Waypoint waypoint = m.waypoint();
        int color = 0xFF000000 | waypoint.color();
        float stroke = ui.num("stroke.width");
        float scale = module.labelScale.getFloat()
                * distanceScale(m.distance(), ui.num(l + "wp_near"), ui.num(l + "wp_far"), ui.num(l + "wp_min_scale"));
        float focus = Math.max(m.selected() ? 0.55f : 0f, focus(m.x() - ui.width() / 2f, m.y() - ui.height() / 2f, ui.num(l + "wp_focus_radius")));
        long now = net.minecraft.util.Util.getMillis();

        float labelScale = module.labelScale.getFloat();
        float size = ui.num(l + "wp_marker");
        float half = size / 2f;
        float stem = ui.num(l + "wp_stem");
        var pose = ui.graphics().pose();

        // Marker and stem shrink with distance.
        pose.pushMatrix();
        pose.translate(Math.round(m.x()), Math.round(m.y()));
        pose.scale(scale, scale);
        try {
            // Selected: a ring pulsing out of the marker.
            if (m.selected()) {
                float pulse = (now % (long) ui.num(l + "wp_pulse_ms")) / ui.num(l + "wp_pulse_ms");
                ui.ring(0, 0, size + 6f + pulse * 14f, 1.5f, (Math.round((1f - pulse) * 170f) << 24) | (color & 0xFFFFFF));
            }
            diamond(ui, 0, 0, half + ui.num(l + "wp_marker_outline"), ui.color("panel"));
            diamond(ui, 0, 0, half, color);
            diamond(ui, 0, -half * 0.35f, half * 0.35f, 0x59FFFFFF);
            int segments = 5;
            for (int i = 0; i < segments; i++) {
                float a = 0.85f * (1f - i / (float) segments);
                float y0 = -half - (i + 1) * stem / segments;
                ui.rect(-stroke, y0, stroke * 2, stem / segments, 0, (Math.round(a * 255) << 24) | (color & 0xFFFFFF));
            }
        } finally {
            pose.popMatrix();
        }

        // The label keeps its size at any distance (only «Масштаб меток» scales it) so the text stays sharp.
        float labelBottom = Math.round(m.y() - (half + stem) * scale);
        pose.pushMatrix();
        pose.translate(Math.round(m.x()), labelBottom);
        pose.scale(labelScale, labelScale);
        try {
            String dist = formatDistance(m.distance());
            boolean full = focus > 0.05f;
            float padX = ui.num(l + "wp_pill_pad_x");
            float padY = ui.num(l + "wp_pill_pad_y");
            float strip = ui.num(l + "wp_pill_strip");
            float gap = ui.num(l + "world_label_gap");
            String name = ui.ellipsize("world_label", waypoint.name(), ui.num(l + "world_label_max"));
            float rowH = Math.max(ui.lineHeight("world_label"), ui.lineHeight("world_label_dist"));
            String coords = (long) Math.floor(waypoint.x()) + "  " + (long) Math.floor(waypoint.y()) + "  " + (long) Math.floor(waypoint.z());
            float coordsA = Math.max(0f, Math.min(1f, (focus - 0.55f) / 0.3f));
            float coordsH = coordsA > 0f ? ui.num(l + "wp_coords_gap") + ui.lineHeight("world_label_coords") : 0f;
            float contentW = full
                    ? ui.textWidth("world_label", name) + gap + ui.textWidth("world_label_dist", dist)
                    : ui.textWidth("world_label_dist", dist);
            if (coordsA > 0f) {
                contentW = Math.max(contentW, ui.textWidth("world_label_coords", coords));
            }
            float w = Math.round(stroke * 2 + padX * 2 + strip + gap + contentW);
            float h = Math.round(stroke * 2 + padY * 2 + rowH + coordsH);
            float x = -Math.round(w / 2f);
            float y = -h;
            ui.pushAlpha(full ? 1f : 0.85f);
            ui.box(x, y, w, h, Math.min(h / 2f, ui.num(l + "wp_pill_radius")), ui.color("panel"),
                    m.selected() ? ui.color("accent") : ui.color("stroke_10"));
            ui.rect(x + stroke + padX, y + stroke + padY, strip, h - (stroke + padY) * 2, strip / 2f, color);
            float tx = x + stroke + padX + strip + gap;
            float ty = y + stroke + padY;
            if (full) {
                float after = ui.textCentered("world_label", name, tx, ty, rowH);
                ui.textCentered("world_label_dist", dist, after + gap, ty, rowH);
            } else {
                ui.textCentered("world_label_dist", dist, tx, ty, rowH);
            }
            if (coordsA > 0f) {
                ui.pushAlpha(coordsA);
                ui.text("world_label_coords", coords, tx, ty + rowH + ui.num(l + "wp_coords_gap"));
                ui.popAlpha();
            }
            ui.popAlpha();
        } finally {
            pose.popMatrix();
        }
    }

    /** Diamond (a square turned 45°) centred on (cx, cy) with half-diagonal {@code r}. */
    private static void diamond(Ui ui, float cx, float cy, float r, int color) {
        ui.triangle(cx - r, cy, cx + r, cy, cx, cy - r, 0f, color);
        ui.triangle(cx - r, cy, cx + r, cy, cx, cy + r, 0f, color);
    }

    /** Arrow at the screen edge pointing to an off-screen waypoint ({@code right}/{@code up} in camera space). */
    private void edgeArrow(Ui ui, Waypoint waypoint, double right, double up, double distance) {
        String l = "layout.hud.";
        if (Math.abs(right) < 1e-4 && Math.abs(up) < 1e-4) {
            up = -1;
        }
        double angle = Math.atan2(right, up);
        float radius = Math.min(ui.width(), ui.height()) * ui.num(l + "wp_arrow_radius");
        float cx = ui.width() / 2f + (float) Math.sin(angle) * radius;
        float cy = ui.height() / 2f - (float) Math.cos(angle) * radius;
        int color = 0xFF000000 | waypoint.color();
        float d = ui.num(l + "wp_arrow");
        float icon = ui.num(l + "wp_arrow_icon");
        ui.circle(cx, cy, d, ui.color("panel"));
        ui.ring(cx, cy, d, ui.num("stroke.width") * 1.5f, color);
        Icons.navArrow(ui, cx - icon / 2f, cy - icon / 2f, icon, 2f, color, (float) Math.toDegrees(angle));
        String dist = formatDistance(distance);
        float tw = ui.textWidth("world_label_dist", dist);
        ui.text("world_label_dist", dist, cx - tw / 2f, cy + d / 2f + ui.num("stroke.width") * 2);
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
