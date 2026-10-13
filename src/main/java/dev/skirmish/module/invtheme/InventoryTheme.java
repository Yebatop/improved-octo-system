package dev.skirmish.module.invtheme;

import dev.skirmish.ui.Ui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Draws the themed background of a container screen: panel, a rounded well per active slot and, for the player
 * inventory, the model frame and the crafting arrow. Coordinates come in GUI px and are drawn in design px.
 */
public final class InventoryTheme {
    private static final String L = "layout.invtheme.";

    private InventoryTheme() {
    }

    public static void draw(GuiGraphics graphics, AbstractContainerScreen<?> screen, int left, int top, int width, int height, boolean inventory) {
        Ui ui = Ui.begin(graphics);
        try {
            float s = (float) Ui.toDesign(1.0);
            float pad = ui.num(L + "pad");
            ui.box(left * s - pad, top * s - pad, width * s + pad * 2, height * s + pad * 2, ui.theme().radius("panel"),
                    ui.color("inv_panel"), ui.color("stroke"));
            float slot = 18 * s;
            float r = ui.theme().radius("slot");
            for (Slot sl : screen.getMenu().slots) {
                if (!sl.isActive()) {
                    continue;
                }
                float x = (left + sl.x - 1) * s;
                float y = (top + sl.y - 1) * s;
                ui.box(x + 0.5f, y + 0.5f, slot - 1f, slot - 1f, r, ui.color("inv_slot"), ui.color("inv_slot_stroke"));
            }
            if (inventory) {
                ui.box((left + 26) * s, (top + 8) * s, 49 * s, 70 * s, ui.theme().radius("tile"), ui.color("inv_model"), ui.color("inv_slot_stroke"));
                float ax = (left + 135) * s;
                float ay = (top + 37) * s;
                float len = 13 * s;
                int arrow = ui.color("text_3");
                ui.line(ax, ay, ax + len, ay, 2f * s, arrow);
                ui.triangle(ax + len - 4 * s, ay - 4 * s, ax + len - 4 * s, ay + 4 * s, ax + len + 2 * s, ay, 1f, arrow);
            }
        } finally {
            ui.end();
        }
    }

    /** The title colour on the dark panel. */
    public static int labelColor() {
        return dev.skirmish.ui.Theme.get().color("text_2");
    }
}
