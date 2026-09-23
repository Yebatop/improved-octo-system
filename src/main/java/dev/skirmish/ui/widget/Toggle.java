package dev.skirmish.ui.widget;

import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Animated on/off switch (44×26, knob 20). */
public final class Toggle extends Widget {
    private final BooleanSupplier value;
    private final Consumer<Boolean> setter;
    private final Anim knob = new Anim("toggle_ms");

    public Toggle(BooleanSupplier value, Consumer<Boolean> setter) {
        this.value = value;
        this.setter = setter;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        String l = "layout.menu.";
        w = ui.num(l + "toggle_width");
        h = ui.num(l + "toggle_height");
        float t = knob.target(value.getAsBoolean()).value();
        int off = Anim.lerpColor(ui.color("toggle_off"), ui.color("toggle_off_hover"), hovered());
        ui.rect(x, y, w, h, h / 2f, Anim.lerpColor(off, ui.color("accent"), t));
        float pad = ui.num(l + "toggle_pad");
        float size = ui.num(l + "toggle_knob");
        float kx = x + pad + t * (w - pad * 2 - size);
        ui.circle(kx + size / 2f, y + h / 2f, size, ui.color("white"));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && enabled) {
            setter.accept(!value.getAsBoolean());
            return true;
        }
        return false;
    }
}
