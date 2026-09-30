package dev.skirmish.module.regions;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import org.jspecify.annotations.Nullable;

/**
 * The pill for the region looked at last: accent in the region's tone, its kind, the size ("21×21", "37×37×37",
 * with "?" while Lite/Prime was guessed) and, under it, whether you are inside and how far the edge is. Last in the
 * top-centre alert column, clear of the crosshair.
 */
final class RegionHud extends HudBlock {
    private static final String L = RegionBoundsModule.L;
    private final RegionBoundsModule module;
    private RegionBoundsModule.@Nullable Shown region;

    RegionHud(RegionBoundsModule module) {
        super(RegionBoundsModule.ID, "skirmish.hud.element.region_bounds", new Placement(0.5f, 0f, 0.5f, 0f, 0,
                Theme.get().num("layout.hud.top_column_dy")));
        this.module = module;
    }

    @Override
    public @Nullable String stackUnder() {
        return "hw_item_timers";
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.hud.get();
    }

    @Override
    public boolean shown() {
        update(false);
        return module.latest() != null;
    }

    @Override
    public boolean hasContent() {
        return region != null;
    }

    @Override
    public void update(boolean preview) {
        RegionBoundsModule.Shown live = module.latest();
        if (live != null) {
            region = live;
        } else if (preview) {
            RegionTable.Type sample = RegionTable.lookup(RegionTable.Server.LITE, "minecraft:emerald_ore");
            BlockPos at = BlockPos.ZERO;
            region = new RegionBoundsModule.Shown(at, sample, RegionTable.bounds(sample, 0, 0, 0), false, "", 0L, 0L);
        }
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "pill_width");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "pill_pad_y") * 2 + ui.lineHeight("rb_title") + ui.num(L + "pill_line_gap") + ui.lineHeight("rb_note");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        RegionBoundsModule.Shown s = region;
        if (s == null) {
            return;
        }
        float w = width(ui, preview);
        float h = height(ui, preview);
        int tone = ui.color("rb_" + s.type().id());
        ui.box(x, y, w, h, ui.theme().radius("tile"), ui.color("panel"), ui.color("stroke"));
        float padX = ui.num(L + "pill_pad_x");
        float padY = ui.num(L + "pill_pad_y");
        float accent = ui.num(L + "pill_accent");
        ui.rect(x + padX, y + padY, accent, h - padY * 2, accent / 2f, tone);
        float tx = x + padX + accent + ui.num(L + "pill_accent_gap");
        float right = x + w - padX;

        String size = RegionTable.sizeText(s.type()) + (s.guessed() ? " ?" : "");
        float sizeW = ui.textWidth("rb_size", size);
        ui.text("rb_size", size, right - sizeW, y + padY, tone);
        String title = ui.ellipsize("rb_title", typeName(s.type()), right - sizeW - ui.num(L + "pill_accent_gap") - tx);
        ui.text("rb_title", title, tx, y + padY);

        float line = y + padY + ui.lineHeight("rb_title") + ui.num(L + "pill_line_gap");
        Minecraft mc = Minecraft.getInstance();
        String note;
        int color;
        if (mc.player == null || preview && module.latest() == null) {
            note = Ui.tr("skirmish.regions.inside", 4);
            color = ui.color("good");
        } else {
            double d = RegionTable.edgeDistance(s.box(), mc.player.getX(), mc.player.getY(), mc.player.getZ());
            if (d >= 0) {
                note = Ui.tr("skirmish.regions.inside", (int) Math.floor(d));
                color = ui.color("good");
            } else {
                note = Ui.tr("skirmish.regions.outside", (int) Math.ceil(-d));
                color = ui.color("text_2");
            }
        }
        ui.text("rb_note", ui.ellipsize("rb_note", note, right - tx), tx, line, color);
    }

    static String typeName(RegionTable.Type type) {
        String key = "skirmish.regions.type." + type.id();
        String text = Ui.tr(key);
        return text.equals(key) ? type.id() : text;
    }
}
