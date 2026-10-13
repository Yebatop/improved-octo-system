package dev.skirmish.module.enemycd;

import dev.skirmish.ui.Ui;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;

/**
 * A centred row of cooldown chips: item icon and seconds left ("12"), or "+4" (seconds since the use) while this
 * server's cooldown is not known yet. Drawn in design px with its bottom at {@code bottom}.
 */
final class CooldownRow {
    private static final String L = EnemyCooldownsModule.L;

    private CooldownRow() {
    }

    static ItemStack icon(CooldownBook.Kind kind) {
        return switch (kind) {
            case PEARL -> new ItemStack(Items.ENDER_PEARL);
            case GAPPLE -> new ItemStack(Items.GOLDEN_APPLE);
            case EGAPPLE -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE);
            case SHIELD -> new ItemStack(Items.SHIELD);
        };
    }

    /** "12" for a known cooldown (whole seconds, rounded up), "+4" for the time since an unknown one. */
    static String text(CooldownBook.Mark mark, long now) {
        long left = mark.leftMs(now);
        if (left >= 0) {
            return Long.toString((left + 999) / 1000);
        }
        return "+" + mark.ageMs(now) / 1000;
    }

    static void draw(Ui ui, List<CooldownBook.Mark> marks, float cx, float bottom, long now) {
        if (marks.isEmpty()) {
            return;
        }
        float h = ui.num(L + "chip_h");
        float pad = ui.num(L + "chip_pad");
        float icon = ui.num(L + "chip_icon");
        float gap = ui.num(L + "chip_gap");
        float inner = ui.num(L + "chip_inner_gap");
        float total = 0f;
        float[] widths = new float[marks.size()];
        for (int i = 0; i < marks.size(); i++) {
            widths[i] = pad * 2 + icon + inner + ui.textWidth("ecd_value", text(marks.get(i), now));
            total += widths[i] + (i > 0 ? gap : 0f);
        }
        float x = Math.round(cx - total / 2f);
        float y = Math.round(bottom - h);
        for (int i = 0; i < marks.size(); i++) {
            CooldownBook.Mark mark = marks.get(i);
            long left = mark.leftMs(now);
            String tone = left < 0 ? "text_3" : left <= 3000 ? "good" : "warn";
            ui.box(x, y, widths[i], h, h / 2f, ui.color("panel"), ui.color("stroke"));
            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(x + pad, y + (h - icon) / 2f);
            pose.scale(icon / 16f, icon / 16f);
            ui.graphics().renderItem(icon(mark.kind()), 0, 0);
            pose.popMatrix();
            ui.textCentered("ecd_value", text(mark, now), x + pad + icon + inner, y, h, ui.color(tone));
            x += widths[i] + gap;
        }
    }
}
