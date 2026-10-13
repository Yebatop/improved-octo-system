package dev.skirmish.module.hunt;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.worldmap.SearchZones;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.util.ServerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.BitSet;

/**
 * The hunt card: the object and who placed it; the mask as tiles, hidden digits glowing; the nearest cell not yet
 * visited with an arrow and its distance; a bar of the cells visited; and, standing in a candidate cell, a green
 * line with how far down the given height is. Its border pulses for the first seconds of a new hunt.
 */
final class HuntHud extends HudBlock {
    private static final String L = "layout.hunt.";
    private final HuntModule module;

    HuntHud(HuntModule module) {
        super("hunt", "skirmish.hud.element.hunt", new Placement(0, 0, 0, 0,
                Theme.get().num("layout.events.default_x"), Theme.get().num("layout.events.default_y")));
        this.module = module;
    }

    @Override
    public @Nullable String stackUnder() {
        return "event_commander";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.card.get();
    }

    @Override
    public boolean shown() {
        return module.zone() != null;
    }

    @Override
    public boolean stepsAsideInFight() {
        return true;
    }

    private SearchZones.@Nullable Zone zone(boolean preview) {
        SearchZones.Zone z = module.zone();
        if (z == null && preview) {
            var m = dev.skirmish.util.MaskedCoords.find("▶ Игрок Player_1 установил Золотой Спавнер на координатах 1*4*, 5, 1*9*");
            if (m != null) {
                BitSet v = new BitSet();
                v.set(0, 12);
                z = new SearchZones.Zone(m.what(), m.xs(), m.y(), m.zs(), "minecraft:overworld", Util.getMillis(), m.xMask(), m.zMask(), v);
            }
        }
        return z;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        SearchZones.Zone z = zone(preview);
        float h = HudStyle.insetY(ui) * 2 + HudStyle.headerHeight(ui) + ui.num("layout.panel_gap")
                + ui.num(L + "tile_h") + ui.num(L + "gap") + rowH(ui) + ui.num(L + "gap")
                + ui.lineHeight("hunt_small") + ui.num(L + "bar_gap") + ui.num(L + "bar_h");
        if (z != null && (inCell(z, preview) || !here(z, preview))) {
            h += ui.num(L + "gap") + ui.lineHeight("hunt_note");
        }
        return h;
    }

    private float rowH(Ui ui) {
        return Math.max(ui.num(L + "arrow"), ui.lineHeight("hunt_dist")) + ui.lineHeight("hunt_small");
    }

    private boolean inCell(SearchZones.Zone z, boolean preview) {
        return !preview && module.inCell() >= 0 && module.zone() == z;
    }

    private static boolean here(SearchZones.Zone z, boolean preview) {
        return preview || z.dimension().equals(ServerContext.dimension());
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        SearchZones.Zone z = zone(preview);
        if (z == null) {
            return;
        }
        long now = Util.getMillis();
        float w = width(ui, preview);
        float h = height(ui, preview);
        boolean inCell = inCell(z, preview);
        HudStyle.panel(ui, x, y, w, h);
        // A fresh hunt (and standing in a candidate cell) glows.
        long age = preview ? 0 : now - module.startedAt();
        float glow = inCell ? 0.6f + 0.4f * (float) Math.sin(now / 250.0) : age < 6000 ? 0.5f + 0.5f * (float) Math.sin(now / 160.0) : 0f;
        if (glow > 0f) {
            int tone = ui.color(inCell ? "good" : "hunt_gold");
            ui.border(x, y, w, h, ui.theme().radius("panel"), 1.5f, (tone & 0xFFFFFF) | (Math.round(255 * Math.max(0f, glow)) << 24));
        }
        float cx = x + HudStyle.insetX(ui);
        float cw = w - HudStyle.insetX(ui) * 2;
        float cy = y + HudStyle.insetY(ui);
        String[] subject = HuntMath.subject(z.what());
        String meta = subject[1].isEmpty() ? Ui.duration(now - z.at()) : subject[1] + " · " + Ui.duration(now - z.at());
        String shownMeta = ui.ellipsize("panel_meta", meta, cw * 0.4f);
        float titleRoom = cw - ui.num("layout.panel_icon") - ui.num("layout.panel_header_gap") - ui.textWidth("panel_meta", shownMeta) - 8f;
        float[] icon = HudStyle.header(ui, cx, cy, cw, ui.ellipsize("panel_title", subject[0], titleRoom), shownMeta);
        diamond(ui, icon[0], icon[1], ui.num("layout.panel_icon"), ui.color("hunt_gold"));
        cy += HudStyle.headerHeight(ui) + ui.num("layout.panel_gap");

        // The mask: X 1?4? · Z 1?9? · Y 5 — known digits plain, hidden ones as glowing tiles.
        float tx = cx;
        tx = maskTiles(ui, "X", z.xMask(), tx, cy, now);
        tx += ui.num(L + "gap") * 2;
        tx = maskTiles(ui, "Z", z.zMask(), tx, cy, now);
        if (z.y() != null) {
            tx += ui.num(L + "gap") * 2;
            ui.textCentered("hunt_axis", "Y", tx, cy, ui.num(L + "tile_h"));
            tx += ui.textWidth("hunt_axis", "Y") + ui.num(L + "tile_gap") * 2;
            ui.textCentered("hunt_digit", Integer.toString(z.y()), tx, cy, ui.num(L + "tile_h"));
        }
        cy += ui.num(L + "tile_h") + ui.num(L + "gap");

        // The nearest cell not visited yet: arrow, «Ближайшая клетка», distance; its ranges under it.
        HuntMath.Target t = preview ? new HuntMath.Target(12, 1245, 1195, 342) : module.target();
        Minecraft mc = Minecraft.getInstance();
        float arrow = ui.num(L + "arrow");
        float lineH = Math.max(arrow, ui.lineHeight("hunt_dist"));
        if (t == null) {
            ui.textCentered("hunt_label", Ui.tr("skirmish.hunt.all_visited"), cx, cy, lineH, ui.color("good"));
        } else {
            float angle = 0f;
            if (mc.player != null && !preview) {
                float yaw = (float) Math.toDegrees(Math.atan2(-(t.x() - mc.player.getX()), t.z() - mc.player.getZ()));
                angle = Mth.wrapDegrees(yaw - mc.player.getViewYRot(1f));
            }
            if (here(z, preview)) {
                Icons.navArrow(ui, cx, cy + (lineH - arrow) / 2f, arrow, 2f, ui.color("hunt_gold"), angle);
            }
            float lx = cx + arrow + ui.num(L + "gap");
            ui.textCentered("hunt_label", Ui.tr("skirmish.hunt.nearest"), lx, cy, lineH);
            String dist = here(z, preview) ? distance(t.distance()) : "—";
            ui.textCentered("hunt_dist", dist, cx + cw - ui.textWidth("hunt_dist", dist), cy, lineH, ui.color("hunt_gold"));
            int i = t.index() / z.zs().size();
            int j = t.index() % z.zs().size();
            String ranges = "X " + z.xs().get(i)[0] + "…" + z.xs().get(i)[1] + " · Z " + z.zs().get(j)[0] + "…" + z.zs().get(j)[1];
            ui.text("hunt_small", ranges, lx, cy + lineH);
        }
        cy += rowH(ui) + ui.num(L + "gap");

        // Cells visited.
        int total = z.cells();
        int visited = z.visited().cardinality();
        String progress = Ui.tr("skirmish.hunt.visited", visited, total);
        ui.text("hunt_small", progress, cx, cy);
        cy += ui.lineHeight("hunt_small") + ui.num(L + "bar_gap");
        float bh = ui.num(L + "bar_h");
        ui.rect(cx, cy, cw, bh, bh / 2f, ui.color("fill_06"));
        if (visited > 0) {
            ui.rect(cx, cy, Math.max(bh, cw * visited / (float) total), bh, bh / 2f, ui.color("hunt_gold"));
        }
        cy += bh;

        if (inCell) {
            cy += ui.num(L + "gap");
            String note = Ui.tr("skirmish.hunt.in_cell");
            if (z.y() != null && mc.player != null) {
                int dy = mc.player.getBlockY() - z.y();
                note += " · " + (dy > 0 ? Ui.tr("skirmish.hunt.below", dy) : dy < 0 ? Ui.tr("skirmish.hunt.above", -dy) : Ui.tr("skirmish.hunt.level"));
            }
            ui.text("hunt_note", ui.ellipsize("hunt_note", note, cw), cx, cy, ui.color("good"));
        } else if (!here(z, preview)) {
            cy += ui.num(L + "gap");
            ui.text("hunt_note", Ui.tr("skirmish.hunt.other_dim"), cx, cy, ui.color("text_3"));
        }
    }

    /** «X» then one tile per character: digits plain, hidden digits as gold «?» tiles that breathe. */
    private static float maskTiles(Ui ui, String axis, String mask, float x, float y, long now) {
        float th = ui.num(L + "tile_h");
        float tw = ui.num(L + "tile_w");
        float gap = ui.num(L + "tile_gap");
        ui.textCentered("hunt_axis", axis, x, y, th);
        x += ui.textWidth("hunt_axis", axis) + gap * 2;
        Character[] chars = HuntMath.maskChars(mask);
        for (int k = 0; k < chars.length; k++) {
            Character c = chars[k];
            if (c == null) {
                float pulse = 0.55f + 0.45f * (float) Math.sin(now / 300.0 + k);
                int gold = ui.color("hunt_gold");
                ui.rect(x, y, tw, th, ui.num(L + "tile_r"), (gold & 0xFFFFFF) | (Math.round(70 * pulse) << 24));
                ui.border(x, y, tw, th, ui.num(L + "tile_r"), 1f, gold);
                float qw = ui.textWidth("hunt_digit", "?");
                ui.textCentered("hunt_digit", "?", x + (tw - qw) / 2f, y, th, gold);
            } else {
                String d = c.toString();
                ui.rect(x, y, tw, th, ui.num(L + "tile_r"), ui.color("fill_06"));
                float dw = ui.textWidth("hunt_digit", d);
                ui.textCentered("hunt_digit", d, x + (tw - dw) / 2f, y, th);
            }
            x += tw + gap;
        }
        return x;
    }

    private static void diamond(Ui ui, float x, float y, float size, int color) {
        float c = size / 2f;
        ui.triangle(x, y + c, x + size, y + c, x + c, y, 1f, color);
        ui.triangle(x, y + c, x + size, y + c, x + c, y + size, 1f, color);
    }

    private static String distance(double blocks) {
        return blocks >= 1000 ? Ui.decimal(blocks / 1000.0, 1) + " км" : Math.round(blocks) + " м";
    }
}
