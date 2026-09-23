package dev.skirmish.ui.widget;

import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * Base of the UI kit's controls. Positions and sizes are design px; the owning {@link UiScreen} lays widgets out
 * every frame (immediate layout, retained state) and routes input to them in design px.
 */
public abstract class Widget {
    public float x;
    public float y;
    public float w;
    public float h;
    public boolean enabled = true;
    protected final Anim hover = new Anim("hover_ms");
    private boolean focused;

    public Widget at(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Widget bounds(float x, float y, float w, float h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        return this;
    }

    /** Preferred width for auto-sized controls (buttons, segmented groups); fixed-size ones return {@link #w}. */
    public float preferredWidth(Ui ui) {
        return w;
    }

    public boolean contains(double mx, double my) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    public final void render(Ui ui, double mx, double my, boolean mouseOver) {
        hover.target(enabled && mouseOver && contains(mx, my));
        if (!enabled) {
            ui.pushAlpha(ui.num("layout.disabled_alpha"));
        }
        draw(ui, mx, my);
        if (!enabled) {
            ui.popAlpha();
        }
    }

    protected abstract void draw(Ui ui, double mx, double my);

    /** Whether hovering shows the pointing-hand cursor. */
    public boolean clickable() {
        return enabled;
    }

    public boolean mouseClicked(double mx, double my, int button) {
        return false;
    }

    public void mouseReleased(double mx, double my, int button) {
    }

    public void mouseDragged(double mx, double my, int button) {
    }

    public boolean keyPressed(KeyEvent event) {
        return false;
    }

    public boolean charTyped(CharacterEvent event) {
        return false;
    }

    public boolean focusable() {
        return false;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    protected float hovered() {
        return hover.value();
    }
}
