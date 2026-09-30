package dev.skirmish.module.worldmap;

import com.mojang.blaze3d.platform.NativeImage;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.friends.FriendsModule;
import dev.skirmish.ui.Ui;
import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * The minimap on the HUD. The ground is one small texture sampled from the World Map's tiles around you (turned with
 * you when «Поворот» is on) with the shape's soft edge baked in, rebuilt as you move or turn; the markers are drawn
 * over it: mobs as dots, players as their faces (friends ringed in their colour, a small arrow when well above or
 * below), your waypoints (on the rim when farther), the compass letters, and you as an arrow in the middle.
 */
final class MinimapHud extends HudBlock {
    private static final String L = "layout.minimap.";
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("skirmish", "minimap");

    private final MinimapModule module;
    private @Nullable DynamicTexture texture;
    private int texels;
    private double builtX = Double.NaN;
    private double builtZ;
    private float builtYaw;
    private long builtAt;
    private String builtKey = "";

    MinimapHud(MinimapModule module) {
        super(MinimapModule.ID, "skirmish.hud.element.minimap", new Placement(1, 0, 1, 0, -18, 18));
        this.module = module;
    }

    /** Right under the replay indicator in the top right corner, above the kill card and kill feed. */
    @Override
    public @Nullable String stackUnder() {
        return "killcam";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.level != null;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "size");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        float h = ui.num(L + "size");
        return module.coords.get() ? h + ui.num(L + "gap") + ui.num(L + "pill_h") : h;
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        Player self = mc.player;
        if (self == null || mc.level == null) {
            return;
        }
        long now = Util.getMillis();
        float size = ui.num(L + "size");
        boolean round = module.shape.get() == MinimapModule.Shape.ROUND;
        boolean rotate = module.rotate.get();
        float radius = round ? size / 2f : ui.num(L + "radius");
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        Vec3 me = self.getPosition(pt);
        float yaw = self.getViewYRot(pt);
        double scale = size / (double) module.zoom.get().blocks;

        // Ground.
        rebuild(ui, me.x, me.z, rotate ? yaw : 0f, round, size, now);
        if (texture != null) {
            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(x, y);
            pose.scale(size / texels, size / texels);
            ui.graphics().blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, texels, texels, texels, texels, ui.fade(0xFFFFFFFF));
            pose.popMatrix();
        }
        float cx = x + size / 2f;
        float cy = y + size / 2f;

        // Mobs, then players over them.
        float inset = ui.num(L + "inset");
        float dot = ui.num(L + "dot");
        float face = ui.num(L + "face");
        java.util.List<Entity> people = new java.util.ArrayList<>();
        for (Entity e : mc.level.entitiesForRendering()) {
            MinimapModule.Kind kind = module.kind(e, self);
            if (kind == null) {
                continue;
            }
            Vec3 p = e.getPosition(pt);
            float[] at = MinimapMath.toMap(p.x - me.x, p.z - me.z, yaw, rotate, scale);
            if (!MinimapMath.inside(at[0], at[1], size, round, kind == MinimapModule.Kind.PLAYER ? face / 2f + 1 : dot)) {
                continue;
            }
            if (kind == MinimapModule.Kind.PLAYER) {
                people.add(e);
                continue;
            }
            int color = ui.color(kind == MinimapModule.Kind.HOSTILE ? "minimap_hostile" : "minimap_passive");
            ui.circle(cx + at[0], cy + at[1], dot + 1.5f, ui.color("minimap_outline"));
            ui.circle(cx + at[0], cy + at[1], dot, color);
        }
        for (Entity e : people) {
            Vec3 p = e.getPosition(pt);
            float[] at = MinimapMath.toMap(p.x - me.x, p.z - me.z, yaw, rotate, scale);
            drawPlayer(ui, mc, (Player) e, cx + at[0], cy + at[1], face, p.y - me.y);
        }

        if (module.waypoints.get()) {
            // A square's rounded corners cut into the rim, so markers pinned there sit a bit farther in.
            drawWaypoints(ui, me, yaw, rotate, scale, cx, cy, size, round, round ? inset : inset + radius * 0.3f);
        }
        drawLetters(ui, yaw, rotate, cx, cy, size, round);

        // You: an arrow facing where you look (always up when the map turns).
        float s = ui.num(L + "arrow");
        double a = rotate ? Math.PI : Math.toRadians(yaw);
        float dx = (float) -Math.sin(a);
        float dy = (float) Math.cos(a);
        float px = -dy;
        float py = dx;
        ui.triangle(cx + dx * s * 1.3f, cy + dy * s * 1.3f, cx - dx * s * 0.8f + px * s * 0.95f, cy - dy * s * 0.8f + py * s * 0.95f,
                cx - dx * s * 0.8f - px * s * 0.95f, cy - dy * s * 0.8f - py * s * 0.95f, 1.5f, ui.color("minimap_outline"));
        ui.triangle(cx + dx * s * 1.3f, cy + dy * s * 1.3f, cx - dx * s * 0.8f + px * s * 0.9f, cy - dy * s * 0.8f + py * s * 0.9f,
                cx - dx * s * 0.8f - px * s * 0.9f, cy - dy * s * 0.8f - py * s * 0.9f, 1f, ui.color("white"));

        // Rim.
        ui.border(x, y, size, size, radius, ui.num(L + "rim"), ui.color("minimap_rim"));

        // The scale for a moment after the zoom key.
        if (module.zoomChangedAt > 0 && now - module.zoomChangedAt < ui.num(L + "scale_ms")) {
            String label = Ui.tr("skirmish.minimap.span", module.zoom.get().blocks);
            float lw = ui.textWidth("minimap_scale", label) + 12;
            float lh = ui.lineHeight("minimap_scale") + 4;
            float ly = y + size - lh - (round ? size * 0.12f : 6f);
            ui.rect(cx - lw / 2f, ly, lw, lh, lh / 2f, ui.color("panel"));
            ui.textCentered("minimap_scale", label, cx - lw / 2f + 6, ly, lh);
        }

        if (module.coords.get()) {
            drawCoords(ui, self, x, y + size + ui.num(L + "gap"), size);
        }
    }

    /** Resamples the ground texture when you have moved half a texel, turned, changed the look, or now and then. */
    private void rebuild(Ui ui, double px, double pz, float yaw, boolean round, float size, long now) {
        int n = Math.max(32, Math.round(ui.num(L + "texels")));
        MinimapModule.Zoom zoom = module.zoom.get();
        double blocksPerTexel = zoom.blocks / (double) n;
        WorldMapModule map = WorldMapModule.instance();
        MapStore store = map == null ? null : map.store();
        Minecraft mc = Minecraft.getInstance();
        boolean mapped = mc.level != null && WorldMapModule.mapped(mc.level);
        String key = n + "|" + zoom + "|" + round + "|" + (store == null ? "" : store.key()) + "|" + mapped + "|" + size;
        if (texture != null && key.equals(builtKey) && now - builtAt < ui.num(L + "rebuild_ms")
                && Math.abs(px - builtX) < blocksPerTexel * 0.5 && Math.abs(pz - builtZ) < blocksPerTexel * 0.5
                && Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - builtYaw)) < 0.6f) {
            return;
        }
        if (texture == null || texels != n) {
            if (texture != null) {
                mc.getTextureManager().release(TEXTURE);
            }
            texture = new DynamicTexture(() -> "Skirmish minimap", n, n, true);
            mc.getTextureManager().register(TEXTURE, texture);
            texels = n;
        }
        NativeImage image = texture.getPixels();
        if (image == null) {
            return;
        }
        int bg = ui.color("minimap_bg");
        float edgeRadius = round ? n / 2f : ui.num(L + "radius") * n / size;
        double texPerBlock = n / (double) zoom.blocks;
        boolean turned = module.rotate.get();
        TileCache tiles = new TileCache(mapped ? store : null, now);
        for (int j = 0; j < n; j++) {
            float oy = j + 0.5f - n / 2f;
            for (int i = 0; i < n; i++) {
                float ox = i + 0.5f - n / 2f;
                float cover = MinimapMath.coverage(ox, oy, n, round, edgeRadius);
                if (cover <= 0f) {
                    image.setPixel(i, j, 0);
                    continue;
                }
                double[] w = MinimapMath.toWorld(ox, oy, yaw, turned, texPerBlock);
                int argb = tiles.at((int) Math.floor(px + w[0]), (int) Math.floor(pz + w[1]));
                if ((argb >>> 24) == 0) {
                    argb = bg;
                }
                int alpha = Math.round((argb >>> 24) * cover);
                image.setPixel(i, j, (alpha << 24) | (argb & 0xFFFFFF));
            }
        }
        texture.upload();
        builtX = px;
        builtZ = pz;
        builtYaw = yaw;
        builtAt = now;
        builtKey = key;
    }

    /** The tiles a rebuild reads, looked up once each. */
    private static final class TileCache {
        private final @Nullable MapStore store;
        private final long now;
        private final long[] keys = new long[8];
        private final MapTile[] tiles = new MapTile[8];
        private int size;

        TileCache(@Nullable MapStore store, long now) {
            this.store = store;
            this.now = now;
        }

        int at(int bx, int bz) {
            if (store == null) {
                return 0;
            }
            int rx = Math.floorDiv(bx, MapTile.SIZE);
            int rz = Math.floorDiv(bz, MapTile.SIZE);
            long k = ((long) rx << 32) | (rz & 0xFFFFFFFFL);
            MapTile tile = null;
            boolean found = false;
            for (int i = 0; i < size; i++) {
                if (keys[i] == k) {
                    tile = tiles[i];
                    found = true;
                    break;
                }
            }
            if (!found) {
                tile = store.exists(rx, rz) ? store.tile(rx, rz, false, now) : null;
                if (size < keys.length) {
                    keys[size] = k;
                    tiles[size] = tile;
                    size++;
                }
            }
            return tile == null ? 0 : tile.image.getPixel(Math.floorMod(bx, MapTile.SIZE), Math.floorMod(bz, MapTile.SIZE));
        }
    }

    private void drawPlayer(Ui ui, Minecraft mc, Player player, float x, float y, float face, double dy) {
        int friend = FriendsModule.nametagColor(player);
        int ring = friend >= 0 ? 0xFF000000 | friend : ui.color("minimap_player");
        float half = face / 2f;
        ui.rect(x - half - 1.5f, y - half - 1.5f, face + 3f, face + 3f, 3f, ring);
        var connection = mc.getConnection();
        PlayerInfo info = connection == null ? null : connection.getPlayerInfo(player.getUUID());
        if (info != null) {
            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(x - half, y - half);
            pose.scale(face / 8f, face / 8f);
            PlayerFaceRenderer.draw(ui.graphics(), info.getSkin().body().texturePath(), 0, 0, 8, info.showHat(), false, ui.fade(0xFFFFFFFF));
            pose.popMatrix();
        }
        // Well above or below you: a small arrow over or under the face.
        if (Math.abs(dy) > 3.5) {
            float t = 3f;
            float ty = dy > 0 ? y - half - 3.5f : y + half + 3.5f;
            float dir = dy > 0 ? -1f : 1f;
            ui.triangle(x - t, ty, x + t, ty, x, ty + dir * t, 0.5f, ring);
        }
        if (module.names.get()) {
            String name = ui.ellipsize("minimap_name", player.getGameProfile().name(), 70f);
            float nw = ui.textWidth("minimap_name", name);
            float lh = ui.lineHeight("minimap_name");
            float ny = y + half + (dy < -3.5 ? 7f : 3f);
            ui.rect(x - nw / 2f - 3, ny, nw + 6, lh, lh / 2f, ui.color("panel"));
            ui.text("minimap_name", name, x - nw / 2f, ny, friend >= 0 ? ring : ui.color("text"));
        }
    }

    private void drawWaypoints(Ui ui, Vec3 me, float yaw, boolean rotate, double scale, float cx, float cy, float size, boolean round, float inset) {
        float m = ui.num(L + "marker");
        Waypoint selected = WaypointManager.get().selected();
        for (Waypoint wp : WaypointManager.get().current()) {
            float[] at = MinimapMath.toMap(wp.x() - me.x, wp.z() - me.z, yaw, rotate, scale);
            boolean inside = MinimapMath.inside(at[0], at[1], size, round, inset);
            if (!inside) {
                at = MinimapMath.pin(at[0], at[1], size, round, inset);
            }
            float wx = cx + at[0];
            float wy = cy + at[1];
            float k = inside ? 1f : 0.8f;
            int color = 0xFF000000 | (wp.color() & 0xFFFFFF);
            if (selected != null && selected.id().equals(wp.id())) {
                ui.ring(wx, wy, m * 3.4f, 1.5f, ui.color("accent"));
            }
            ui.triangle(wx - m * k, wy, wx + m * k, wy, wx, wy - m * 1.25f * k, 1.5f, ui.color("minimap_outline"));
            ui.triangle(wx - m * k, wy, wx + m * k, wy, wx, wy + m * 1.25f * k, 1.5f, ui.color("minimap_outline"));
            ui.triangle(wx - m * k, wy, wx + m * k, wy, wx, wy - m * 1.25f * k, 0.5f, color);
            ui.triangle(wx - m * k, wy, wx + m * k, wy, wx, wy + m * 1.25f * k, 0.5f, color);
        }
    }

    /** N (red) and E, S, W on the rim, turning with the map. */
    private void drawLetters(Ui ui, float yaw, boolean rotate, float cx, float cy, float size, boolean round) {
        String[] letters = {Ui.tr("skirmish.minimap.north"), Ui.tr("skirmish.minimap.east"), Ui.tr("skirmish.minimap.south"), Ui.tr("skirmish.minimap.west")};
        double[][] dirs = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        float d = ui.num(L + "letter");
        for (int i = 0; i < 4; i++) {
            float[] at = MinimapMath.toMap(dirs[i][0] * 1000, dirs[i][1] * 1000, yaw, rotate, 1);
            at = MinimapMath.pin(at[0], at[1], size, round, d / 2f + 1);
            String style = i == 0 ? "minimap_n" : "minimap_letter";
            float lx = cx + at[0];
            float ly = cy + at[1];
            ui.circle(lx, ly, d, ui.color("panel"));
            float tw = ui.textWidth(style, letters[i]);
            ui.textCentered(style, letters[i], lx - tw / 2f, ly - d / 2f, d);
        }
    }

    private static void drawCoords(Ui ui, Player self, float x, float y, float w) {
        float h = ui.num(L + "pill_h");
        ui.box(x, y, w, h, h / 2f, ui.color("panel"), ui.color("stroke"));
        String[] axes = {"X", "Y", "Z"};
        String[] values = {Integer.toString(self.getBlockX()), Integer.toString(self.getBlockY()), Integer.toString(self.getBlockZ())};
        float gap = 3f;
        float between = 8f;
        float total = 0f;
        for (int i = 0; i < 3; i++) {
            total += ui.textWidth("minimap_axis", axes[i]) + gap + ui.textWidth("minimap_coords", values[i]);
        }
        total += between * 2;
        float tx = x + (w - total) / 2f;
        for (int i = 0; i < 3; i++) {
            ui.textCentered("minimap_axis", axes[i], tx, y, h);
            tx += ui.textWidth("minimap_axis", axes[i]) + gap;
            ui.textCentered("minimap_coords", values[i], tx, y, h);
            tx += ui.textWidth("minimap_coords", values[i]) + between;
        }
    }
}
