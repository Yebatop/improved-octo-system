package dev.skirmish.ui.render;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2f;

/** Submits SDF shapes to the GUI render state. Coordinates are in the current pose's space. */
public final class Shapes {
    /** Extra quad margin so the anti-aliased edge is never clipped by the quad itself. */
    private static final float MARGIN = 1f;

    private Shapes() {
    }

    /**
     * Rounded box. {@code strokeWidth} 0 fills it, otherwise draws a border of that width inside the box.
     * {@code inverted} paints only the area of the box's rectangle that lies outside the rounded shape.
     */
    public static void box(GuiGraphics graphics, float x, float y, float w, float h, float radius, float strokeWidth,
                           int color, boolean inverted) {
        float m = inverted ? 0f : MARGIN;
        float hw = w / 2f;
        float hh = h / 2f;
        float x0 = x - m;
        float y0 = y - m;
        float x1 = x + w + m;
        float y1 = y + h + m;
        submit(graphics, x0, y0, x1, y1,
                -hw - m, -hh - m, hw + m, hh + m,
                fixed(hw), fixed(hh), fixed(Math.max(0f, radius)), fixed(Math.max(0f, strokeWidth)),
                inverted ? 1f : 0f, color);
    }

    /** Capsule from A to B (a line with round caps). */
    public static void segment(GuiGraphics graphics, float ax, float ay, float bx, float by, float halfThickness, int color) {
        float pad = halfThickness + MARGIN;
        float x0 = Math.min(ax, bx) - pad;
        float y0 = Math.min(ay, by) - pad;
        float x1 = Math.max(ax, bx) + pad;
        float y1 = Math.max(ay, by) + pad;
        submit(graphics, x0, y0, x1, y1,
                x0 - ax, y0 - ay, x1 - ax, y1 - ay,
                fixed(bx - ax), fixed(by - ay), fixed(halfThickness), 0,
                0.5f, color);
    }

    private static void submit(GuiGraphics graphics, float x0, float y0, float x1, float y1,
                               float lx0, float ly0, float lx1, float ly1,
                               int a, int b, int c, int d, float mode, int color) {
        Matrix3x2f pose = new Matrix3x2f(graphics.pose());
        ScreenRectangle scissor = graphics.scissorStack.peek();
        ScreenRectangle bounds = ShapeRenderState.bounds(pose, x0, y0, x1, y1, scissor);
        if (bounds == null || bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        graphics.guiRenderState.submitGuiElement(new ShapeRenderState(pose, x0, y0, x1, y1, lx0, ly0, lx1, ly1,
                a, b, c, d, mode, color, scissor, bounds));
    }

    private static int fixed(float v) {
        return Math.round(Math.max(-2047f, Math.min(2047f, v)) * ShapeRenderState.FIXED);
    }
}
