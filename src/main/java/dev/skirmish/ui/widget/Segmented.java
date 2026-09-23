package dev.skirmish.ui.widget;

import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;

import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/** A row of mutually exclusive options in a tinted group; the active one sits on a raised chip. */
public final class Segmented extends Widget {
    /** Token keys of a segmented variant (menu settings or the replay bar). */
    public record Spec(String layout, String groupColor, String groupRadius, String chipRadius, String text, String textOn) {
        public static final Spec MENU = new Spec("layout.menu.", "fill_05", "segment_group", "segment", "segment", "segment_on");
        public static final Spec REPLAY = new Spec("layout.replay.", "fill_06", "control", "button_sm", "replay_segment", "replay_segment_on");
    }

    private final Spec spec;
    private final Supplier<List<String>> labels;
    private final IntSupplier selected;
    private final IntConsumer select;
    private final Anim chip = new Anim("toggle_ms");
    private float[] starts = new float[0];
    private float[] widths = new float[0];
    private int hoveredIndex = -1;

    public Segmented(Spec spec, Supplier<List<String>> labels, IntSupplier selected, IntConsumer select) {
        this.spec = spec;
        this.labels = labels;
        this.selected = selected;
        this.select = select;
    }

    @Override
    public float preferredWidth(Ui ui) {
        List<String> items = labels.get();
        float pad = ui.num(spec.layout() + "segment_pad");
        float gap = ui.num(spec.layout() + "segment_gap");
        float padX = ui.num(spec.layout() + "segment_pad_x");
        float width = pad * 2 + gap * Math.max(0, items.size() - 1);
        for (int i = 0; i < items.size(); i++) {
            width += Math.max(ui.textWidth(spec.text(), items.get(i)), ui.textWidth(spec.textOn(), items.get(i))) + padX * 2;
        }
        return width;
    }

    public float preferredHeight(Ui ui) {
        return ui.num(spec.layout() + "segment_height") + ui.num(spec.layout() + "segment_pad") * 2;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        List<String> items = labels.get();
        float pad = ui.num(spec.layout() + "segment_pad");
        float gap = ui.num(spec.layout() + "segment_gap");
        float padX = ui.num(spec.layout() + "segment_pad_x");
        float bh = ui.num(spec.layout() + "segment_height");
        w = preferredWidth(ui);
        h = bh + pad * 2;
        ui.rect(x, y, w, h, ui.theme().radius(spec.groupRadius()), ui.color(spec.groupColor()));

        if (starts.length != items.size()) {
            starts = new float[items.size()];
            widths = new float[items.size()];
        }
        float cx = x + pad;
        hoveredIndex = -1;
        for (int i = 0; i < items.size(); i++) {
            float bw = Math.max(ui.textWidth(spec.text(), items.get(i)), ui.textWidth(spec.textOn(), items.get(i))) + padX * 2;
            starts[i] = cx;
            widths[i] = bw;
            if (hovered() > 0f && mx >= cx && mx < cx + bw && my >= y && my < y + h) {
                hoveredIndex = i;
            }
            cx += bw + gap;
        }
        int active = selected.getAsInt();
        if (active >= 0 && active < items.size()) {
            float pos = chip.target(active).value();
            int lo = Math.max(0, Math.min(items.size() - 1, (int) Math.floor(pos)));
            int hi = Math.min(items.size() - 1, lo + 1);
            float f = pos - lo;
            float chipX = starts[lo] + (starts[hi] - starts[lo]) * f;
            float chipW = widths[lo] + (widths[hi] - widths[lo]) * f;
            ui.rect(chipX, y + pad, chipW, bh, ui.theme().radius(spec.chipRadius()), ui.color("selected"));
        }
        for (int i = 0; i < items.size(); i++) {
            boolean on = i == active;
            String style = on ? spec.textOn() : spec.text();
            int color = ui.color(ui.style(style).color());
            if (!on && i == hoveredIndex) {
                color = Anim.lerpColor(color, ui.color("text"), hovered());
            }
            float tw = ui.textWidth(style, items.get(i));
            ui.textCentered(style, items.get(i), starts[i] + (widths[i] - tw) / 2f, y + pad, bh, color);
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        for (int i = 0; i < starts.length; i++) {
            if (mx >= starts[i] && mx < starts[i] + widths[i]) {
                if (i != selected.getAsInt()) {
                    select.accept(i);
                }
                return true;
            }
        }
        return false;
    }
}
