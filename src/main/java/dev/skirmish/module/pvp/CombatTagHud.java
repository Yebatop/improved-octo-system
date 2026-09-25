package dev.skirmish.module.pvp;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;

/**
 * Combat-tag (КТ) panel: a countdown ring with big seconds, the «не выходи из игры» hint and the tagged opponents
 * from the board. Fades out when the tag ends, showing 0 on the way out.
 */
final class CombatTagHud extends HudBlock {
    private static final String L = "layout.pvp.";
    private static final PvpModule.TagView SAMPLE = new PvpModule.TagView(27, 0.9f, List.of("GFk31AK", "Notch_"),
            PvpModule.Source.BOARD, List.of(new TagParser.Opponent("GFk31AK", 27, 16, 20), new TagParser.Opponent("Notch_", 12, 7, 20)));

    private final PvpModule module;
    private PvpModule.@Nullable TagView current;
    private PvpModule.@Nullable TagView last;

    CombatTagHud(PvpModule module) {
        super("combat_tag", "skirmish.hud.element.combat_tag", new Placement(0.5f, 0f, 0.5f, 0f, 0, dev.skirmish.ui.Theme.get().num("layout.hud.top_column_dy")));
        this.module = module;
    }

    @Override
    public @org.jspecify.annotations.Nullable String stackUnder() {
        return "waypoint";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.combatTagHud.get();
    }

    @Override
    public boolean shown() {
        update(false);
        return current != null;
    }

    @Override
    public boolean hasContent() {
        return last != null;
    }

    @Override
    public void update(boolean preview) {
        current = Minecraft.getInstance().player == null ? null : module.tag(System.currentTimeMillis());
        if (current != null) {
            last = current;
        } else if (last != null && last.seconds() != 0) {
            // Ended: fade out on an empty ring and 0.
            last = new PvpModule.TagView(0, 0f, last.opponents(), last.source(), last.details());
        }
    }

    private PvpModule.TagView view(boolean preview) {
        if (preview && current == null || last == null) {
            return SAMPLE;
        }
        return last;
    }

    private static String detail(TagParser.Opponent o) {
        String seconds = Ui.tr("skirmish.pvp.tag.seconds", o.seconds());
        if (Float.isNaN(o.health())) {
            return seconds;
        }
        return Ui.tr("skirmish.pvp.tag.health", Ui.decimal(o.health(), 0), Ui.decimal(o.maxHealth(), 0)) + "  " + seconds;
    }

    /** Name rows: all names when they fit, else {@code tag_max_names} names and a "+N" row. */
    private static int nameRows(Ui ui, PvpModule.TagView view) {
        return Math.min(view.opponents().size(), Math.round(ui.num(L + "tag_max_names")) + 1);
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "tag_width");
    }

    private float columnHeight(Ui ui, PvpModule.TagView view) {
        float gap = ui.num(L + "tag_line_gap");
        float h = HudStyle.headerHeight(ui) + gap + ui.lineHeight("pvp_tag_hint");
        int rows = nameRows(ui, view);
        if (rows > 0) {
            h += ui.num(L + "tag_names_gap") + rows * ui.lineHeight("pvp_tag_name") + (rows - 1) * gap;
        }
        return h;
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return HudStyle.insetY(ui) * 2 + Math.max(ui.num(L + "tag_ring"), columnHeight(ui, view(preview)));
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        PvpModule.TagView view = view(preview);
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.panel(ui, x, y, w, h);
        float cx = x + HudStyle.insetX(ui);
        float cy = y + HudStyle.insetY(ui);
        float cw = w - HudStyle.insetX(ui) * 2;
        float ch = h - HudStyle.insetY(ui) * 2;

        // Ring with the seconds (or "КТ" when the server gives only a bar).
        float ring = ui.num(L + "tag_ring");
        float rx = cx + ring / 2f;
        float ry = cy + ch / 2f;
        Rings.countdown(ui, rx, ry, ring, ui.num(L + "tag_ring_width"), view.fraction(), ui.color("track"),
                ui.color(view.source() == PvpModule.Source.LOCAL ? "warn" : "accent"));
        String seconds = view.seconds() >= 0 ? Integer.toString(view.seconds()) : Ui.tr("skirmish.pvp.tag.short");
        String style = view.seconds() >= 0 ? "pvp_tag_seconds" : "pvp_tag_label";
        ui.textCentered(style, seconds, rx - ui.textWidth(style, seconds) / 2f, ry - ring / 2f, ring);

        // Text column: title + source, hint, opponents.
        float gap = ui.num(L + "tag_line_gap");
        float tx = cx + ring + ui.num(L + "tag_gap");
        float tw = cx + cw - tx;
        float ty = cy + (ch - columnHeight(ui, view)) / 2f;
        String source = Ui.tr("skirmish.pvp.source." + view.source().name().toLowerCase(Locale.ROOT));
        float hh = HudStyle.headerHeight(ui);
        ui.textCentered("panel_title", ui.ellipsize("panel_title", Ui.tr("skirmish.pvp.tag.title"),
                tw - ui.textWidth("panel_meta", source) - gap), tx, ty, hh);
        ui.textCentered("panel_meta", source, tx + tw - ui.textWidth("panel_meta", source), ty, hh);
        ty += hh + gap;
        ui.text("pvp_tag_hint", ui.ellipsize("pvp_tag_hint", Ui.tr("skirmish.pvp.tag.hint"), tw), tx, ty);
        ty += ui.lineHeight("pvp_tag_hint");

        int rows = nameRows(ui, view);
        if (rows == 0) {
            return;
        }
        ty += ui.num(L + "tag_names_gap");
        int max = Math.round(ui.num(L + "tag_max_names"));
        List<String> names = view.opponents();
        for (int i = 0; i < rows; i++) {
            if (i == max && names.size() > max + 1) {
                ui.text("pvp_tag_more", Ui.tr("skirmish.pvp.tag.more", names.size() - max), tx, ty);
            } else {
                // Right side: the opponent's health and own timer when the server lists them («16/20 HP  27с»).
                String right = i < view.details().size() ? detail(view.details().get(i)) : "";
                float rw = right.isEmpty() ? 0f : ui.textWidth("pvp_tag_detail", right);
                ui.text("pvp_tag_name", ui.ellipsize("pvp_tag_name", names.get(i), tw - (rw > 0 ? rw + gap * 2 : 0f)), tx, ty);
                if (rw > 0) {
                    ui.text("pvp_tag_detail", right, tx + tw - rw, ty);
                }
            }
            ty += ui.lineHeight("pvp_tag_name") + gap;
        }
    }
}
