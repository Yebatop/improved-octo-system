package dev.skirmish.module.worldmap;

import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.util.ServerContext;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The world map: saved tiles drawn 1 texel per block at the chosen zoom, your waypoints as diamonds with names and
 * you as an arrow. Drag to pan, wheel to zoom around the cursor, right click to add a waypoint there.
 */
public final class WorldMapScreen extends UiScreen {
    private static final String L = WorldMapModule.L;
    private static final float MIN_ZOOM = 0.125f;
    private static final float MAX_ZOOM = 16f;

    private double cx;
    private double cz;
    private float zoom;
    private boolean dragging;
    private double hoverX = Double.NaN;
    private double hoverZ = Double.NaN;

    /** Atlas layers (kept for the session). */
    private static boolean showPath = true;
    private static boolean showPortals = true;
    private static boolean showRoute = true;

    private final Button pathToggle = new Button(() -> Ui.tr("skirmish.worldmap.layer.path"), false,
            () -> showPath = !showPath).layout(L).selected(() -> showPath);
    private final Button portalToggle = new Button(() -> Ui.tr("skirmish.worldmap.layer.portals"), false,
            () -> showPortals = !showPortals).layout(L).selected(() -> showPortals);
    private final Button routeToggle = new Button(() -> Ui.tr("skirmish.worldmap.layer.route"), false,
            () -> showRoute = !showRoute).layout(L).selected(() -> showRoute);
    private final Button center = new Button(() -> Ui.tr("skirmish.worldmap.center"), false, this::centerOnPlayer).layout(L);
    private final Button toZone = new Button(() -> Ui.tr("skirmish.worldmap.to_zone"), false, this::centerOnZone).layout(L);
    private final Button in = new Button(() -> "+", false, () -> zoomAt(1.5f, Double.NaN, Double.NaN)).layout(L);
    private final Button out = new Button(() -> "−", false, () -> zoomAt(1 / 1.5f, Double.NaN, Double.NaN)).layout(L);

    public WorldMapScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.worldmap.title"), parent);
        zoom = (float) dev.skirmish.ui.Theme.get().num(L + "zoom");
        centerOnPlayer();
    }

    private void centerOnPlayer() {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            cx = mc.player.getX();
            cz = mc.player.getZ();
        }
    }

    /** The map opened on the newest search zone, zoomed so all of it fits. */
    public static WorldMapScreen onZone(@Nullable Screen parent) {
        WorldMapScreen screen = new WorldMapScreen(parent);
        screen.centerOnZone();
        return screen;
    }

    private void centerOnZone() {
        SearchZones.Zone z = SearchZones.latest(Util.getMillis());
        if (z == null) {
            return;
        }
        cx = (z.minX() + z.maxX() + 1) / 2.0;
        cz = (z.minZ() + z.maxZ() + 1) / 2.0;
        var mc = net.minecraft.client.Minecraft.getInstance();
        double w = mc.getWindow().getGuiScaledWidth() / Ui.designScale();
        double h = mc.getWindow().getGuiScaledHeight() / Ui.designScale();
        double span = Math.max(z.maxX() - z.minX() + 1, z.maxZ() - z.minZ() + 1) * 1.25;
        zoom = (float) Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, Math.min(w, h) / span));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Zooms by {@code factor}, keeping the block under (sx, sy) in place (the screen centre when NaN). */
    private void zoomAt(float factor, double sx, double sy) {
        float next = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        if (!Double.isNaN(sx)) {
            double w = width / Ui.designScale();
            double h = height / Ui.designScale();
            double bx = cx + (sx - w / 2) / zoom;
            double bz = cz + (sy - h / 2) / zoom;
            cx = bx - (sx - w / 2) / next;
            cz = bz - (sy - h / 2) / next;
        }
        zoom = next;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        long now = Util.getMillis();
        float w = ui.width();
        float h = ui.height();
        ui.rect(0, 0, w, h, 0f, ui.color("map_bg"));
        WorldMapModule module = WorldMapModule.instance();
        MapStore store = module == null ? null : module.store();
        boolean mapped = minecraft.level != null && WorldMapModule.mapped(minecraft.level);
        if (store != null && mapped) {
            drawTiles(ui, store, w, h, now);
        }
        if (zoom >= ui.num(L + "grid_zoom")) {
            drawGrid(ui, w, h);
        }
        var nav = dev.skirmish.module.navigator.NavigatorModule.instance();
        if (nav != null && nav.isEnabled()) {
            if (showPath && nav.breadcrumbsOn()) {
                drawPath(ui, nav.crumbs(), w, h);
            }
            if (showPortals) {
                for (double[] p : nav.portalsIn(ServerContext.dimension())) {
                    float px = sx(p[0], w);
                    float py = sy(p[1], h);
                    // An obsidian-frame glyph: purple frame, dark inside, a lighter core.
                    ui.rect(px - 6, py - 8, 12, 16, 2f, ui.color("nav_portal"));
                    ui.rect(px - 3.5f, py - 5.5f, 7, 11, 1f, ui.color("map_bg"));
                    ui.rect(px - 2, py - 4, 4, 8, 1f, (ui.color("nav_portal") & 0x00FFFFFF) | 0x90000000);
                }
            }
            if (showRoute) {
                drawRoute(ui, nav, w, h);
            }
        }
        java.util.List<SearchZones.Zone> zones = SearchZones.in(ServerContext.dimension(), now);
        drawZones(ui, zones, w, h);
        drawEntities(ui, w, h);
        drawWaypoints(ui, w, h);
        drawPlayer(ui, w, h);

        hoverX = cx + (mx - w / 2) / zoom;
        hoverZ = cz + (my - h / 2) / zoom;

        // Header card.
        float pad = ui.num(L + "pad");
        float m = ui.num(L + "margin");
        String title = Ui.tr("skirmish.worldmap.title");
        String sub = mapped ? ServerContext.dimension().replace("minecraft:", "") : Ui.tr("skirmish.worldmap.no_roof");
        String coords = "X " + (int) Math.floor(hoverX) + " · Z " + (int) Math.floor(hoverZ);
        float cw = Math.max(ui.textWidth("wm_title", title), Math.max(ui.textWidth("wm_sub", sub), ui.textWidth("wm_coords", coords))) + pad * 2;
        float ch = pad * 2 + ui.lineHeight("wm_title") + ui.lineHeight("wm_sub") + ui.lineHeight("wm_coords") + 4;
        ui.box(m, m, cw, ch, ui.theme().radius("panel"), ui.color("panel"), ui.color("stroke"));
        float ty = m + pad;
        ui.text("wm_title", title, m + pad, ty);
        ty += ui.lineHeight("wm_title");
        ui.text("wm_sub", sub, m + pad, ty, ui.color(mapped ? "text_2" : "warn"));
        ty += ui.lineHeight("wm_sub") + 4;
        ui.text("wm_coords", coords, m + pad, ty, ui.color("accent"));

        // Controls.
        float bh = ui.num(L + "button_height");
        float gap = ui.num(L + "gap");
        float cwBtn = center.preferredWidth(ui);
        float x = w - m - cwBtn;
        center.bounds(x, m, cwBtn, bh);
        in.bounds(x - gap - bh, m, bh, bh);
        out.bounds(x - gap * 2 - bh * 2, m, bh, bh);
        widget(ui, out, mx, my);
        widget(ui, in, mx, my);
        widget(ui, center, mx, my);
        if (!zones.isEmpty()) {
            float zw = toZone.preferredWidth(ui);
            toZone.bounds(x - gap * 3 - bh * 2 - zw, m, zw, bh);
            widget(ui, toZone, mx, my);
        }
        if (nav != null && nav.isEnabled()) {
            float ly = m + bh + gap;
            float lx = w - m;
            for (Button b : new Button[]{routeToggle, portalToggle, pathToggle}) {
                float bw = b.preferredWidth(ui);
                lx -= bw;
                b.bounds(lx, ly, bw, bh);
                widget(ui, b, mx, my);
                lx -= gap;
            }
        }

        String hint = Ui.tr("skirmish.worldmap.hint");
        ui.text("wm_hint", hint, (w - ui.textWidth("wm_hint", hint)) / 2f, h - m - ui.lineHeight("wm_hint"));
        String scale = Ui.tr("skirmish.worldmap.scale", zoom >= 1 ? Math.round(zoom) + ":1" : "1:" + Math.round(1 / zoom));
        ui.text("wm_hint", scale, m, h - m - ui.lineHeight("wm_hint"));
    }

    private float sx(double bx, float w) {
        return (float) ((bx - cx) * zoom + w / 2);
    }

    private float sy(double bz, float h) {
        return (float) ((bz - cz) * zoom + h / 2);
    }

    private void drawTiles(Ui ui, MapStore store, float w, float h, long now) {
        double left = cx - w / 2 / zoom;
        double right = cx + w / 2 / zoom;
        double top = cz - h / 2 / zoom;
        double bottom = cz + h / 2 / zoom;
        int rx0 = (int) Math.floor(left / MapTile.SIZE);
        int rx1 = (int) Math.floor(right / MapTile.SIZE);
        int rz0 = (int) Math.floor(top / MapTile.SIZE);
        int rz1 = (int) Math.floor(bottom / MapTile.SIZE);
        var pose = ui.graphics().pose();
        for (int rx = rx0; rx <= rx1; rx++) {
            for (int rz = rz0; rz <= rz1; rz++) {
                if (!store.exists(rx, rz)) {
                    continue;
                }
                MapTile tile = store.tile(rx, rz, false, now);
                if (tile == null) {
                    continue;
                }
                Identifier id = tile.texture();
                pose.pushMatrix();
                pose.translate(sx((double) rx * MapTile.SIZE, w), sy((double) rz * MapTile.SIZE, h));
                pose.scale(zoom, zoom);
                ui.graphics().blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0f, 0f, MapTile.SIZE, MapTile.SIZE, MapTile.SIZE, MapTile.SIZE,
                        ui.fade(0xFFFFFFFF));
                pose.popMatrix();
            }
        }
    }

    private void drawPath(Ui ui, java.util.List<double[]> crumbs, float w, float h) {
        int n = crumbs.size();
        float dot = ui.num(L + "crumb");
        for (int i = 0; i < n; i++) {
            double[] c = crumbs.get(i);
            float px = sx(c[0], w);
            float py = sy(c[2], h);
            if (px < -5 || py < -5 || px > w + 5 || py > h + 5) {
                continue;
            }
            int a = 60 + Math.round(170f * i / Math.max(1, n - 1));
            ui.circle(px, py, dot, (a << 24) | (ui.color("nav_path") & 0xFFFFFF));
        }
    }

    private void drawRoute(Ui ui, dev.skirmish.module.navigator.NavigatorModule nav, float w, float h) {
        java.util.List<double[]> pts = nav.routePoints(ServerContext.dimension());
        for (int i = 0; i + 1 < pts.size(); i++) {
            ui.line(sx(pts.get(i)[0], w), sy(pts.get(i)[1], h), sx(pts.get(i + 1)[0], w), sy(pts.get(i + 1)[1], h), 3f, ui.color("accent"));
        }
    }

    private void drawGrid(Ui ui, float w, float h) {
        int color = ui.color("map_grid");
        double left = cx - w / 2 / zoom;
        double top = cz - h / 2 / zoom;
        for (double bx = Math.floor(left / 16) * 16; sx(bx, w) < w; bx += 16) {
            ui.rect(sx(bx, w), 0, 1f, h, 0f, color);
        }
        for (double bz = Math.floor(top / 16) * 16; sy(bz, h) < h; bz += 16) {
            ui.rect(0, sy(bz, h), w, 1f, 0f, color);
        }
    }

    /** Every cell a masked position can be in, shaded, inside the outline of all of them, with what it is. */
    private void drawZones(Ui ui, java.util.List<SearchZones.Zone> zones, float w, float h) {
        int accent = ui.color("warn");
        int fill = (accent & 0x00FFFFFF) | 0x55000000;
        for (SearchZones.Zone z : zones) {
            float x0 = sx(z.minX(), w);
            float y0 = sy(z.minZ(), h);
            float x1 = sx(z.maxX() + 1, w);
            float y1 = sy(z.maxZ() + 1, h);
            if (x1 < 0 || y1 < 0 || x0 > w || y0 > h) {
                continue;
            }
            ui.rect(x0, y0, x1 - x0, y1 - y0, 0f, (accent & 0x00FFFFFF) | 0x14000000);
            // Cells: gold to search, green once you have been in them, the hunt's next cell ringed.
            int visitedFill = (ui.color("good") & 0x00FFFFFF) | 0x70000000;
            int target = z == SearchZones.latest(Util.getMillis()) ? dev.skirmish.module.hunt.HuntModule.targetCell() : -1;
            for (int i = 0; i < z.xs().size(); i++) {
                int[] xr = z.xs().get(i);
                for (int j = 0; j < z.zs().size(); j++) {
                    int[] zr = z.zs().get(j);
                    float cx0 = sx(xr[0], w);
                    float cy0 = sy(zr[0], h);
                    float cw = Math.max(2f, (xr[1] - xr[0] + 1) * zoom);
                    float ch = Math.max(2f, (zr[1] - zr[0] + 1) * zoom);
                    int index = z.cell(i, j);
                    ui.rect(cx0, cy0, cw, ch, 0f, z.visited().get(index) ? visitedFill : fill);
                    if (index == target) {
                        ui.border(cx0 - 3, cy0 - 3, cw + 6, ch + 6, 2f, 2f, ui.color("accent"));
                    }
                }
            }
            ui.border(x0, y0, x1 - x0, y1 - y0, 0f, 1.5f, accent);
            String label = ui.ellipsize("wm_marker", z.what(), 260f) + " · "
                    + Ui.tr("skirmish.worldmap.zone_cells", z.cells()) + (z.y() == null ? "" : " · Y " + z.y());
            float lw = ui.textWidth("wm_marker", label);
            float lh = ui.lineHeight("wm_marker");
            float ly = Math.max(4f, y0 - lh - 6);
            ui.rect(x0, ly, lw + 10, lh + 2, lh / 2f, ui.color("panel"));
            ui.text("wm_marker", label, x0 + 5, ly + 1, accent);
        }
    }

    private void drawWaypoints(Ui ui, float w, float h) {
        float size = ui.num(L + "marker");
        Waypoint selected = WaypointManager.get().selected();
        for (Waypoint wp : WaypointManager.get().current()) {
            float x = sx(wp.x(), w);
            float y = sy(wp.z(), h);
            if (x < -50 || y < -50 || x > w + 50 || y > h + 50) {
                continue;
            }
            int color = 0xFF000000 | (wp.color() & 0xFFFFFF);
            if (selected != null && selected.id().equals(wp.id())) {
                ui.ring(x, y, size * 2.6f, 2f, ui.color("accent"));
            }
            ui.triangle(x - size, y, x + size, y, x, y - size * 1.2f, 1f, color);
            ui.triangle(x - size, y, x + size, y, x, y + size * 1.2f, 1f, color);
            String name = ui.ellipsize("wm_marker", wp.name(), 160f);
            float nw = ui.textWidth("wm_marker", name);
            float lh = ui.lineHeight("wm_marker");
            ui.rect(x - nw / 2 - 5, y + size * 1.4f, nw + 10, lh + 2, lh / 2f, ui.color("panel"));
            ui.text("wm_marker", name, x - nw / 2, y + size * 1.4f + 1);
        }
    }

    /** With the Minimap on, the same players and mobs as on it (its settings decide which). */
    private void drawEntities(Ui ui, float w, float h) {
        MinimapModule mini = MinimapModule.active();
        if (mini == null || minecraft.level == null || minecraft.player == null) {
            return;
        }
        float pt = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        float dot = ui.num(L + "entity_dot");
        float face = ui.num(L + "entity_face");
        var connection = minecraft.getConnection();
        for (net.minecraft.world.entity.Entity e : minecraft.level.entitiesForRendering()) {
            MinimapModule.Kind kind = mini.kind(e, minecraft.player);
            if (kind == null) {
                continue;
            }
            var p = e.getPosition(pt);
            float x = sx(p.x, w);
            float y = sy(p.z, h);
            if (x < -20 || y < -20 || x > w + 20 || y > h + 20) {
                continue;
            }
            if (kind != MinimapModule.Kind.PLAYER) {
                ui.circle(x, y, dot + 2f, ui.color("minimap_outline"));
                ui.circle(x, y, dot, ui.color(kind == MinimapModule.Kind.HOSTILE ? "minimap_hostile" : "minimap_passive"));
                continue;
            }
            var player = (net.minecraft.world.entity.player.Player) e;
            int friend = dev.skirmish.module.friends.FriendsModule.nametagColor(player);
            int ring = friend >= 0 ? 0xFF000000 | friend : ui.color("minimap_player");
            float half = face / 2f;
            ui.rect(x - half - 1.5f, y - half - 1.5f, face + 3f, face + 3f, 3f, ring);
            var info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
            if (info != null) {
                var pose = ui.graphics().pose();
                pose.pushMatrix();
                pose.translate(x - half, y - half);
                pose.scale(face / 8f, face / 8f);
                net.minecraft.client.gui.components.PlayerFaceRenderer.draw(ui.graphics(), info.getSkin().body().texturePath(), 0, 0, 8,
                        info.showHat(), false, ui.fade(0xFFFFFFFF));
                pose.popMatrix();
            }
            String name = ui.ellipsize("wm_marker", player.getGameProfile().name(), 120f);
            float nw = ui.textWidth("wm_marker", name);
            float lh = ui.lineHeight("wm_marker");
            ui.rect(x - nw / 2 - 5, y + half + 3, nw + 10, lh + 2, lh / 2f, ui.color("panel"));
            ui.text("wm_marker", name, x - nw / 2, y + half + 4, friend >= 0 ? ring : ui.color("text"));
        }
    }

    private void drawPlayer(Ui ui, float w, float h) {
        if (minecraft.player == null) {
            return;
        }
        float x = sx(minecraft.player.getX(), w);
        float y = sy(minecraft.player.getZ(), h);
        double yaw = Math.toRadians(minecraft.player.getYRot());
        // Facing: yaw 0 looks to +Z (down on the map), yaw 90 to −X (left).
        float dx = (float) -Math.sin(yaw);
        float dy = (float) Math.cos(yaw);
        float s = ui.num(L + "arrow");
        float px = -dy;
        float py = dx;
        ui.circle(x, y, s * 2.4f, ui.color("accent_16"));
        ui.triangle(x + dx * s * 1.3f, y + dy * s * 1.3f, x - dx * s * 0.8f + px * s * 0.9f, y - dy * s * 0.8f + py * s * 0.9f,
                x - dx * s * 0.8f - px * s * 0.9f, y - dy * s * 0.8f - py * s * 0.9f, 1f, ui.color("white"));
        ui.ring(x, y, s * 2.4f, 1.5f, ui.color("accent"));
    }

    @Override
    protected boolean onBackgroundClick(double mx, double my, int button) {
        if (button == 0) {
            Waypoint hit = waypointAt(mx, my);
            if (hit != null) {
                WaypointManager.get().select(hit.id());
                return true;
            }
            dragging = true;
            return true;
        }
        if (button == 1 && !Double.isNaN(hoverX) && minecraft.level != null) {
            int bx = (int) Math.floor(hoverX);
            int bz = (int) Math.floor(hoverZ);
            int y = minecraft.level.hasChunk(bx >> 4, bz >> 4)
                    ? minecraft.level.getHeight(Heightmap.Types.WORLD_SURFACE, bx, bz)
                    : (int) Math.floor(minecraft.player == null ? 64 : minecraft.player.getY());
            String name = Ui.tr("skirmish.worldmap.waypoint", bx, bz);
            WaypointManager.get().add(name, bx + 0.5, y, bz + 0.5, ServerContext.dimension(), "map");
            return true;
        }
        return false;
    }

    /** The waypoint marker under the cursor (within a few design px), or null. */
    private @Nullable Waypoint waypointAt(double mx, double my) {
        float w = width / Ui.designScale();
        float h = height / Ui.designScale();
        Waypoint best = null;
        double bestD = 12 * 12;
        for (Waypoint wp : WaypointManager.get().current()) {
            double dx = sx(wp.x(), w) - mx;
            double dy = sy(wp.z(), h) - my;
            double d = dx * dx + dy * dy;
            if (d < bestD) {
                bestD = d;
                best = wp;
            }
        }
        return best;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (super.mouseDragged(event, dx, dy)) {
            return true;
        }
        if (dragging) {
            cx -= Ui.toDesign(dx) / zoom;
            cz -= Ui.toDesign(dy) / zoom;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    protected boolean onScroll(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) {
            zoomAt(scrollY > 0 ? 1.25f : 0.8f, Ui.toDesign(x), Ui.toDesign(y));
            return true;
        }
        return false;
    }
}
