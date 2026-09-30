package dev.skirmish.module.hwitems.badges;

import dev.skirmish.module.hwitems.HwItemInfo;
import dev.skirmish.module.hwitems.HwStack;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Draws the badges of one slot with the UI kit at the theme's vanilla-screen scale ({@link Ui#beginOnVanilla}), so
 * they keep their size relative to the 16 px item at every GUI scale. Render thread.
 */
public final class BadgeRenderer {
    private static final String L = "layout.hw_badges.";
    /** Set by the hotbar mixin while {@code Gui.renderItemHotbar} runs. */
    private static int hotbarDepth;

    private final TalismanBadgesModule module;

    BadgeRenderer(TalismanBadgesModule module) {
        this.module = module;
    }

    public static void enterHotbar() {
        hotbarDepth++;
    }

    public static void exitHotbar() {
        hotbarDepth = Math.max(0, hotbarDepth - 1);
    }

    void draw(GuiGraphics graphics, ItemStack stack, int x, int y) {
        if (hotbarDepth > 0 && !module.hotbar.get() || !module.active()) {
            return;
        }
        HwItemInfo info;
        try {
            info = HwStack.info(stack);
        } catch (RuntimeException e) {
            return;
        }
        if (info.isEmpty()) {
            return;
        }
        List<BadgeText.Badge> badges = BadgeText.badges(info, module.options(), Ui::tr);
        if (badges.isEmpty()) {
            return;
        }
        Ui ui = Ui.beginOnVanilla(graphics);
        try {
            Theme theme = ui.theme();
            Theme.TextStyle base = ui.style("hwb_text");
            float scale = module.textScale.getFloat();
            Theme.TextStyle style = new Theme.TextStyle(base.size() * scale, base.weight(), base.color(), base.tracking(), base.mono(),
                    base.lineHeight());
            float s = Ui.baseScale();
            float sx = x / s;
            float sy = y / s;
            float slot = 16f / s;
            float padX = ui.num(L + "pad_x");
            float inset = ui.num(L + "inset");
            float lh = theme.lineHeight(style);
            float maxW = slot + ui.num(L + "overhang") * 2 - padX * 2;
            for (BadgeText.Badge badge : badges) {
                String text = pick(ui, style, badge.texts(), maxW);
                float tw = ui.textWidth(style, text);
                float bw = tw + padX * 2;
                float bx = badge.corner() == BadgeText.Corner.TOP_LEFT ? sx - ui.num(L + "overhang") + inset
                        : sx + slot + ui.num(L + "overhang") - inset - bw;
                float by = sy - ui.num(L + "overhang") + inset;
                ui.rect(bx, by, bw, lh, ui.theme().radius("hw_badge"), ui.color("hwb_bg"));
                ui.text(style, text, bx + padX, by, ui.color(badge.kind().color()));
            }
        } finally {
            ui.end();
        }
    }

    /** First candidate that fits {@code maxW}, else the shortest one. */
    private static String pick(Ui ui, Theme.TextStyle style, List<String> texts, float maxW) {
        for (String t : texts) {
            if (ui.textWidth(style, t) <= maxW) {
                return t;
            }
        }
        return texts.getLast();
    }
}
