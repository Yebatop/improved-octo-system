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

    private final Button center = new Button(() -> Ui.tr("skirmish.worldmap.center"), false, this::centerOnPlayer).layout(L);
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
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        if (scrollY != 0) {
            zoomAt(scrollY > 0 ? 1.25f : 0.8f, Ui.toDesign(x), Ui.toDesign(y));
            return true;
        }
        return false;
    }
}
