package dev.skirmish.ui.widget;

import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * One of many options: a field with the current value and a chevron that opens a list of all of them. For choices
 * too many or too long for a {@link Segmented} row. The {@link UiScreen} draws the open list over everything else and
 * sends it the clicks and the wheel; a click elsewhere or Esc closes it.
 */
public final class Dropdown extends Widget {
    private static final String L = "layout.menu.";

    private final Supplier<List<String>> labels;
    private final IntSupplier selected;
    private final IntConsumer select;
    private final Anim openAnim = new Anim("toggle_ms");
    private boolean open;
    private int scroll;
    private int hoveredRow = -1;
    /** The list's bounds as last drawn. */
    private float lx;
    private float ly;
    private float lw;
    private float lh;

    public Dropdown(Supplier<List<String>> labels, IntSupplier selected, IntConsumer select) {
        this.labels = labels;
        this.selected = selected;
        this.select = select;
    }

    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
        openAnim.target(0f);
    }

    @Override
    public float preferredWidth(Ui ui) {
        return ui.num(L + "dropdown_width");
    }

    public float preferredHeight(Ui ui) {
        return ui.num(L + "segment_height") + ui.num(L + "segment_pad") * 2;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        List<String> items = labels.get();
        int active = selected.getAsInt();
        String label = active >= 0 && active < items.size() ? items.get(active) : "—";
        float padX = ui.num(L + "segment_pad_x");
        float chev = ui.num(L + "dropdown_chevron");
        int fill = Anim.lerpColor(ui.color("fill_05"), ui.color("fill_08"), Math.max(hovered(), openAnim.value()));
        ui.box(x, y, w, h, ui.theme().radius("segment_group"), fill, open ? ui.color("accent") : ui.color("stroke"));
        String shown = ui.ellipsize("segment_on", label, w - padX * 2 - chev - padX / 2f);
        ui.textCentered("segment_on", shown, x + padX, y, h);
        chevron(ui, x + w - padX - chev / 2f, y + h / 2f, chev, openAnim.value(), ui.color("text_2"));
    }

    /** A «v» that turns into «^» as {@code turn} goes to 1. */
    private static void chevron(Ui ui, float cx, float cy, float size, float turn, int color) {
        float dx = size / 2f;
        float dy = size / 4f * (1f - 2f * turn);
        float lw = ui.num("stroke.width") * 1.5f;
        ui.line(cx - dx, cy - dy, cx, cy + dy, lw, color);
        ui.line(cx, cy + dy, cx + dx, cy - dy, lw, color);
    }

    /** Draws the open list under the field (over it when there is no room below); called by the screen last. */
    void drawList(Ui ui, double mx, double my) {
        List<String> items = labels.get();
        int rows = Math.min(items.size(), Math.round(ui.num(L + "dropdown_max_rows")));
        scroll = Math.max(0, Math.min(scroll, items.size() - rows));
        float rh = ui.num(L + "dropdown_row_height");
        float pad = ui.num(L + "dropdown_pad");
        float off = ui.num(L + "dropdown_offset");
        lw = w;
        lh = rows * rh + pad * 2;
        lx = x;
        ly = y + h + off;
        if (ly + lh > ui.height() - off && y - off - lh >= off) {
            ly = y - off - lh;
        }
        float t = openAnim.value();
        ui.pushAlpha(t);
        ui.box(lx, ly, lw, lh, ui.theme().radius("segment_group"), ui.color("window"), ui.color("stroke"));
        int active = selected.getAsInt();
        float padX = ui.num(L + "segment_pad_x");
        hoveredRow = -1;
        for (int r = 0; r < rows; r++) {
            int i = scroll + r;
            float ry = ly + pad + r * rh;
            boolean over = mx >= lx && mx < lx + lw && my >= ry && my < ry + rh;
            if (over) {
                hoveredRow = i;
            }
            if (i == active) {
                ui.rect(lx + pad, ry, lw - pad * 2, rh, ui.theme().radius("segment"), ui.color("selected"));
            } else if (over) {
                ui.rect(lx + pad, ry, lw - pad * 2, rh, ui.theme().radius("segment"), ui.color("fill_06"));
            }
            String style = i == active ? "segment_on" : "segment";
            int color = i == active ? ui.color("text") : over ? ui.color("text") : ui.color(ui.style(style).color());
            ui.textCentered(style, ui.ellipsize(style, items.get(i), lw - padX * 2 - pad * 2), lx + pad + padX, ry, rh, color);
            if (i == active) {
                float d = ui.num("layout.menu.dropdown_chevron") * 0.6f;
                ui.circle(lx + lw - pad - padX, ry + rh / 2f, d, ui.color("accent"));
            }
        }
        if (items.size() > rows) {
            float trackH = lh - pad * 2;
            float thumbH = Math.max(ui.num(L + "scrollbar_min"), trackH * rows / items.size());
            float thumbY = ly + pad + (trackH - thumbH) * scroll / Math.max(1, items.size() - rows);
            float sw = ui.num(L + "scrollbar_width");
            ui.rect(lx + lw - sw - 2, thumbY, sw, thumbH, sw / 2f, ui.color("fill_10"));
        }
        ui.popAlpha();
    }

    boolean listContains(double mx, double my) {
        return open && mx >= lx && mx < lx + lw && my >= ly && my < ly + lh;
    }

    /** A click in the open list: picks the row under the mouse and closes. */
    void clickList(double mx, double my) {
        List<String> items = labels.get();
        float rh = Theme.get().num(L + "dropdown_row_height");
        float pad = Theme.get().num(L + "dropdown_pad");
        int i = scroll + (int) Math.floor((my - ly - pad) / rh);
        if (i >= 0 && i < items.size() && i != selected.getAsInt()) {
            select.accept(i);
        }
        close();
    }

    void scrollList(double amount) {
        scroll -= (int) Math.signum(amount);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        open = !open;
        openAnim.target(open ? 1f : 0f);
        if (open) {
            List<String> items = labels.get();
            int rows = Math.min(items.size(), Math.round(Theme.get().num(L + "dropdown_max_rows")));
            scroll = Math.max(0, Math.min(selected.getAsInt() - rows / 2, items.size() - rows));
        }
        return true;
    }
}
