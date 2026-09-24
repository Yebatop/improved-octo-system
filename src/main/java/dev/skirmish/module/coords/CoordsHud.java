package dev.skirmish.module.coords;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Rows of label + value; default place: left edge, vertically centered. */
final class CoordsHud extends HudBlock {
    private static final String L = "layout.qol.";
    private final CoordsHudModule module;

    private record Row(String label, String value) {
    }

    CoordsHud(CoordsHudModule module) {
        super("coords", "skirmish.hud.element.coords", new Placement(0f, 0.5f, 0f, 0.5f, 18, 0));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        return Minecraft.getInstance().player != null;
    }

    private List<Row> rows(boolean preview) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        double x = 1234.5;
        double y = 64;
        double z = -873.2;
        float yaw = 180f;
        ResourceKey<Level> dimension = Level.OVERWORLD;
        if (player != null && mc.level != null) {
            x = player.getX();
            y = player.getY();
            z = player.getZ();
            yaw = player.getYRot();
            dimension = mc.level.dimension();
        }
        List<Row> rows = new ArrayList<>(3);
        boolean hidden = module.streamer.get();
        rows.add(new Row("XYZ", hidden ? Ui.tr("skirmish.coords.hidden")
                : CoordMath.block(x) + " " + CoordMath.block(y) + " " + CoordMath.block(z)));
        if (module.showFacing.get()) {
            CoordMath.Facing facing = CoordMath.facing(yaw);
            rows.add(new Row(Ui.tr("skirmish.coords.facing"),
                    Ui.tr("skirmish.coords.facing." + facing.name().toLowerCase(Locale.ROOT)) + " " + facing.axis()));
        }
        if (module.showConverted.get() && !hidden) {
            if (dimension == Level.OVERWORLD) {
                rows.add(new Row(Ui.tr("skirmish.coords.nether"), CoordMath.toNether(x) + " " + CoordMath.toNether(z)));
            } else if (dimension == Level.NETHER) {
                rows.add(new Row(Ui.tr("skirmish.coords.overworld"), CoordMath.toOverworld(x) + " " + CoordMath.toOverworld(z)));
            }
        }
        return rows;
    }

    private static float labelWidth(Ui ui, List<Row> rows) {
        float w = 0f;
        for (Row row : rows) {
            w = Math.max(w, ui.textWidth("qol_label", row.label()));
        }
        return w;
    }

    private static float rowHeight(Ui ui) {
        return Math.max(ui.lineHeight("qol_label"), ui.lineHeight("qol_value"));
    }

    @Override
    public float width(Ui ui, boolean preview) {
        List<Row> rows = rows(preview);
        float value = 0f;
        for (Row row : rows) {
            value = Math.max(value, ui.textWidth("qol_value", row.value()));
        }
        return HudStyle.insetX(ui) * 2 + labelWidth(ui, rows) + ui.num(L + "coords_label_gap") + value;
    }

    @Override
    public float height(Ui ui, boolean preview) {
        int n = rows(preview).size();
        return HudStyle.insetY(ui) * 2 + n * rowHeight(ui) + Math.max(0, n - 1) * ui.num(L + "coords_row_gap");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        List<Row> rows = rows(preview);
        HudStyle.panel(ui, x, y, width(ui, preview), height(ui, preview));
        float vx = x + HudStyle.insetX(ui) + labelWidth(ui, rows) + ui.num(L + "coords_label_gap");
        float rh = rowHeight(ui);
        float ry = y + HudStyle.insetY(ui);
        for (Row row : rows) {
            ui.textCentered("qol_label", row.label(), x + HudStyle.insetX(ui), ry, rh);
            ui.textCentered("qol_value", row.value(), vx, ry, rh);
            ry += rh + ui.num(L + "coords_row_gap");
        }
    }
}
