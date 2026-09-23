package dev.skirmish.ui.widget;

import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;


/** Square (rounded) or round button with a drawn icon; background brightens on hover. */
public final class IconButton extends Widget {
    /** Draws the icon; receives the button so it can read its bounds. */
    public interface Painter {
        void paint(Ui ui, IconButton button);
    }

    private final String fill;
    private final String fillHover;
    private final String radiusKey;
    private final Painter painter;
    private final Runnable action;

    /** {@code radiusKey} null makes a circle. */
    public IconButton(String fill, String fillHover, String radiusKey, Painter painter, Runnable action) {
        this.fill = fill;
        this.fillHover = fillHover;
        this.radiusKey = radiusKey;
        this.painter = painter;
        this.action = action;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        float r = radiusKey == null ? Math.min(w, h) / 2f : ui.theme().radius(radiusKey);
        ui.rect(x, y, w, h, r, Anim.lerpColor(ui.color(fill), ui.color(fillHover), hovered()));
        painter.paint(ui, this);
    }

    /** Top-left of a centered icon of the given size. */
    public float[] iconAt(float size) {
        return new float[]{x + (w - size) / 2f, y + (h - size) / 2f};
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            action.run();
            return true;
        }
        return false;
    }

}
