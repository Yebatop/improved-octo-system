package dev.skirmish.ui.widget;

import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Text button: {@code primary} (accent fill) or ghost (1 px outline). Width follows the label. A ghost button can be a
 * toggle ({@link #selected}): while on, its outline and label take the accent colour.
 */
public final class Button extends Widget {
    private final Supplier<String> label;
    private final boolean primary;
    private final Runnable action;
    private String layout = "layout.menu.";
    private BooleanSupplier selected = () -> false;

    public Button(Supplier<String> label, boolean primary, Runnable action) {
        this.label = label;
        this.primary = primary;
        this.action = action;
    }

    /** Uses another layout block's {@code button_height}/{@code *_pad_x} (e.g. the death screen). */
    public Button layout(String prefix) {
        this.layout = prefix;
        return this;
    }

    /** Makes a ghost button a toggle that shows {@code on} in the accent colour. */
    public Button selected(BooleanSupplier on) {
        this.selected = on;
        return this;
    }

    private String style() {
        return primary ? "button_primary" : "button_ghost";
    }

    @Override
    public float preferredWidth(Ui ui) {
        float pad = ui.num(layout + (primary ? "primary_pad_x" : "ghost_pad_x"));
        return ui.textWidth(style(), label.get()) + pad * 2 + (primary ? 0f : ui.num("stroke.width") * 2);
    }

    public float preferredHeight(Ui ui) {
        return ui.num(layout + "button_height");
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        float r = ui.theme().radius("button");
        float t = hovered();
        if (primary) {
            ui.rect(x, y, w, h, r, Anim.lerpColor(ui.color("accent"), Anim.lerpColor(ui.color("accent"), ui.color("white"), ui.num("motion.hover_lighten")), t));
        } else {
            boolean on = selected.getAsBoolean();
            ui.rect(x, y, w, h, r, Anim.lerpColor(ui.color("fill_00"), ui.color("fill_05"), t));
            ui.border(x, y, w, h, r, ui.num("stroke.width"), ui.color(on ? "accent" : "stroke_12"));
        }
        String text = label.get();
        float tw = ui.textWidth(style(), text);
        int color = ui.color(ui.style(style()).color());
        if (!primary) {
            color = selected.getAsBoolean() ? ui.color("accent") : Anim.lerpColor(color, ui.color("text"), t);
        }
        ui.textCentered(style(), text, x + (w - tw) / 2f, y, h, color);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && enabled) {
            action.run();
            return true;
        }
        return false;
    }
}
