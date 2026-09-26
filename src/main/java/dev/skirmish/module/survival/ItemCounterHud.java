package dev.skirmish.module.survival;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Icon + count per tracked item. Default place: left edge, right under the coordinates panel (in its place while
 * that one is off), which keeps it above the chat and away from the right-hand scoreboard.
 */
final class ItemCounterHud extends HudBlock {
    private static final String L = "layout.awareness.";
    private final ItemCounterModule module;

    private record Cell(ItemStack stack, int count, String label) {
    }

    private List<Cell> cells = List.of();

    ItemCounterHud(ItemCounterModule module) {
        super("item_counter", "skirmish.hud.element.item_counter", new Placement(0f, 0.5f, 0f, 0.5f,
                Theme.get().num(L + "items_default_x"), 0));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.isSpectator()) {
            return false;
        }
        update(false);
        return !cells.isEmpty();
    }

    @Override
    public boolean hasContent() {
        return !cells.isEmpty();
    }

    @Override
    public String stackUnder() {
        return "coords";
    }

    @Override
    public void update(boolean preview) {
        LocalPlayer player = Minecraft.getInstance().player;
        List<Cell> out = new ArrayList<>();
        int sample = 0;
        for (Item item : module.items()) {
            int count = player == null ? new int[]{2, 5, 1, 12, 0, 3}[sample++ % 6] : Supplies.count(player, item);
            if (count == 0 && module.hideZero.get() && !preview) {
                continue;
            }
            out.add(new Cell(new ItemStack(item), count, Integer.toString(count)));
        }
        cells = out;
    }

    private boolean row() {
        return module.layout.get() == ItemCounterModule.Layout.ROW;
    }

    private float cellWidth(Ui ui, Cell cell) {
        return ui.num(L + "item_icon") + ui.num(L + "item_count_gap") + Math.max(ui.textWidth("aw_count", cell.label()),
                ui.textWidth("aw_count", "00"));
    }

    private float cellHeight(Ui ui) {
        return Math.max(ui.num(L + "item_icon"), ui.lineHeight("aw_count"));
    }

    @Override
    public float width(Ui ui, boolean preview) {
        float content = 0f;
        for (int i = 0; i < cells.size(); i++) {
            float w = cellWidth(ui, cells.get(i));
            content = row() ? content + w + (i > 0 ? ui.num(L + "item_gap") : 0) : Math.max(content, w);
        }
        return ui.num(L + "item_pad_x") * 2 + content;
    }

    @Override
    public float height(Ui ui, boolean preview) {
        int rows = row() ? 1 : cells.size();
        return ui.num(L + "item_pad_y") * 2 + rows * cellHeight(ui) + Math.max(0, rows - 1) * ui.num(L + "item_row_gap");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.panel(ui, x, y, w, h);
        float icon = ui.num(L + "item_icon");
        float ch = cellHeight(ui);
        float cx = x + ui.num(L + "item_pad_x");
        float cy = y + ui.num(L + "item_pad_y");
        for (Cell cell : cells) {
            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(Math.round(cx), Math.round(cy + (ch - icon) / 2f));
            pose.scale(icon / 16f, icon / 16f);
            ui.graphics().renderItem(cell.stack(), 0, 0);
            pose.popMatrix();
            boolean zero = cell.count() == 0;
            if (zero) {
                // Items render opaque; a panel-colored veil dims the icon of an item I have none of.
                ui.rect(cx, cy + (ch - icon) / 2f, icon, icon, ui.theme().radius("slot"), ui.color("aw_item_veil"));
            }
            ui.textCentered("aw_count", cell.label(), cx + icon + ui.num(L + "item_count_gap"), cy, ch,
                    ui.color(zero ? "text_3" : "text"));
            if (row()) {
                cx += cellWidth(ui, cell) + ui.num(L + "item_gap");
            } else {
                cy += ch + ui.num(L + "item_row_gap");
            }
        }
    }
}
