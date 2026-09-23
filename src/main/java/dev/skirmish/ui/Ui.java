package dev.skirmish.ui;

import dev.skirmish.ui.render.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Drawing context of the UI kit. All coordinates are design px (the mockup's pixels); {@link #begin} scales the
 * GUI pose by {@code layout.design_scale} so one design px is one screen pixel at GUI scale 2.
 * Colors are theme tokens; {@link #pushAlpha} fades everything drawn inside (panel appear/hide).
 */
public final class Ui {
    /** Baseline of Minecraft glyphs below the text origin, in font units (GlyphBitmap.getTop: 7 - bearingTop). */
    private static final float GLYPH_BASELINE = 7f;
    private static final Map<String, FontDescription> FONTS = new HashMap<>();

    private final GuiGraphics graphics;
    private final Theme theme;
    private final Font font;
    private final ArrayDeque<Float> alphas = new ArrayDeque<>();
    private float alpha = 1f;

    private Ui(GuiGraphics graphics) {
        this.graphics = graphics;
        this.theme = Theme.get();
        this.font = Minecraft.getInstance().font;
    }

    public static Ui begin(GuiGraphics graphics) {
        graphics.pose().pushMatrix();
        float scale = Theme.get().num("layout.design_scale");
        graphics.pose().scale(scale, scale);
        return new Ui(graphics);
    }

    public void end() {
        graphics.pose().popMatrix();
    }

    /** GUI px (mouse coordinates) to design px. */
    public static double toDesign(double guiCoordinate) {
        return guiCoordinate / Theme.get().num("layout.design_scale");
    }

    /** Screen size in design px. */
    public float width() {
        return (float) toDesign(graphics.guiWidth());
    }

    public float height() {
        return (float) toDesign(graphics.guiHeight());
    }

    public GuiGraphics graphics() {
        return graphics;
    }

    public Theme theme() {
        return theme;
    }

    public int color(String token) {
        return theme.color(token);
    }

    public float num(String path) {
        return theme.num(path);
    }

    /** Translated UI string (lang files), or the key itself. */
    public static String tr(String key) {
        return net.minecraft.locale.Language.getInstance().getOrDefault(key);
    }

    public static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }

    // ---- alpha ----

    public void pushAlpha(float multiplier) {
        alphas.push(alpha);
        alpha *= Math.max(0f, Math.min(1f, multiplier));
    }

    public void popAlpha() {
        alpha = alphas.isEmpty() ? 1f : alphas.pop();
    }

    public float alpha() {
        return alpha;
    }

    public int fade(int argb) {
        if (alpha >= 1f) {
            return argb;
        }
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    // ---- shapes ----

    public void rect(float x, float y, float w, float h, float radius, int color) {
        if (w <= 0 || h <= 0 || ((color >>> 24) == 0)) {
            return;
        }
        Shapes.box(graphics, x, y, w, h, radius, 0f, fade(color), false);
    }

    /** Border inside the box, like CSS {@code border} with {@code box-sizing: border-box}. */
    public void border(float x, float y, float w, float h, float radius, float width, int color) {
        if (w <= 0 || h <= 0 || ((color >>> 24) == 0)) {
            return;
        }
        Shapes.box(graphics, x, y, w, h, radius, width, fade(color), false);
    }

    /** Filled box with the theme's 1 px border. */
    public void box(float x, float y, float w, float h, float radius, int fill, int stroke) {
        rect(x, y, w, h, radius, fill);
        border(x, y, w, h, radius, theme.num("stroke.width"), stroke);
    }

    /** Paints {@code color} over the corners outside a rounded rect (to round square content such as faces). */
    public void cornerMask(float x, float y, float w, float h, float radius, int color) {
        Shapes.box(graphics, x, y, w, h, radius, 0f, fade(color), true);
    }

    public void circle(float cx, float cy, float diameter, int color) {
        rect(cx - diameter / 2f, cy - diameter / 2f, diameter, diameter, diameter / 2f, color);
    }

    public void ring(float cx, float cy, float diameter, float width, int color) {
        border(cx - diameter / 2f, cy - diameter / 2f, diameter, diameter, diameter / 2f, width, color);
    }

    /** Line with round caps (SVG stroke-linecap: round). */
    public void line(float ax, float ay, float bx, float by, float thickness, int color) {
        Shapes.segment(graphics, ax, ay, bx, by, thickness / 2f, fade(color));
    }

    public void triangle(float ax, float ay, float bx, float by, float cx, float cy, float rounding, int color) {
        if ((color >>> 24) == 0) {
            return;
        }
        Shapes.triangle(graphics, ax, ay, bx, by, cx, cy, rounding, fade(color));
    }

    public void polyline(float thickness, int color, float... points) {
        for (int i = 0; i + 3 < points.length; i += 2) {
            line(points[i], points[i + 1], points[i + 2], points[i + 3], thickness, color);
        }
    }

    public void hline(float x, float y, float w, int color) {
        rect(x, y, w, theme.num("stroke.width"), 0f, color);
    }

    // ---- text ----

    public Theme.TextStyle style(String key) {
        return theme.text(key);
    }

    public static FontDescription fontFor(Theme.TextStyle style) {
        Theme t = Theme.get();
        String family = style.mono() ? t.string("font.mono") : t.string("font.sans");
        int size = nearest(Math.round(style.size()), style.mono() ? "mono_sizes" : "sizes");
        int weight = nearest(style.weight(), style.mono() ? "mono_weights" : "weights");
        String key = family + "_" + weight + "_" + size;
        return FONTS.computeIfAbsent(key, k -> new FontDescription.Resource(Identifier.fromNamespaceAndPath("skirmish", "ui/" + k)));
    }

    private static int nearest(int value, String listKey) {
        List<Integer> options = Theme.get().ints("font." + listKey);
        int best = options.getFirst();
        for (int option : options) {
            if (Math.abs(option - value) < Math.abs(best - value)) {
                best = option;
            }
        }
        return best;
    }

    public MutableComponent component(Theme.TextStyle style, String text) {
        return Component.literal(text).withStyle(Style.EMPTY.withFont(fontFor(style)));
    }

    public float textWidth(String styleKey, String text) {
        return textWidth(style(styleKey), text);
    }

    /** Advance width in design px, including CSS letter-spacing after every character. */
    public float textWidth(Theme.TextStyle style, String text) {
        if (text.isEmpty()) {
            return 0f;
        }
        float width = font.getSplitter().stringWidth(component(style, text));
        if (style.tracking() != 0f) {
            width += style.tracking() * text.codePointCount(0, text.length());
        }
        return width;
    }

    public float lineHeight(String styleKey) {
        return theme.lineHeight(style(styleKey));
    }

    /** Draws text whose CSS line box starts at {@code top}; returns the x after the text. */
    public float text(String styleKey, String text, float x, float top) {
        Theme.TextStyle style = style(styleKey);
        return text(style, text, x, top, theme.color(style.color()));
    }

    public float text(String styleKey, String text, float x, float top, int color) {
        return text(style(styleKey), text, x, top, color);
    }

    public float text(Theme.TextStyle style, String text, float x, float top, int color) {
        if (text.isEmpty()) {
            return x;
        }
        float y = top + theme.baseline(style) - GLYPH_BASELINE;
        int c = fade(color);
        if ((c >>> 24) < 4) {
            return x + textWidth(style, text);
        }
        if (style.tracking() == 0f) {
            drawAt(component(style, text), x, y, c);
            return x + textWidth(style, text);
        }
        float cursor = x;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            String ch = new String(Character.toChars(cp));
            drawAt(component(style, ch), cursor, y, c);
            cursor += font.getSplitter().stringWidth(component(style, ch)) + style.tracking();
            i += Character.charCount(cp);
        }
        return cursor;
    }

    /** Text vertically centered in a box of height {@code h} starting at {@code y}. */
    public float textCentered(String styleKey, String text, float x, float y, float h) {
        return text(styleKey, text, x, y + (h - lineHeight(styleKey)) / 2f);
    }

    public float textCentered(String styleKey, String text, float x, float y, float h, int color) {
        return text(styleKey, text, x, y + (h - lineHeight(styleKey)) / 2f, color);
    }

    private void drawAt(Component component, float x, float y, int color) {
        graphics.pose().pushMatrix();
        // Whole design px (= physical px at GUI scale 2): glyph atlases are sampled NEAREST.
        graphics.pose().translate(Math.round(x), Math.round(y));
        graphics.drawString(font, component, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    /** Word-wraps {@code text} to {@code maxWidth}; returns the lines. */
    public List<String> wrap(String styleKey, String text, float maxWidth) {
        Theme.TextStyle style = style(styleKey);
        List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(style, candidate) <= maxWidth || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
            } else {
                lines.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    /** Cuts {@code text} with an ellipsis so it fits {@code maxWidth}. */
    public String ellipsize(String styleKey, String text, float maxWidth) {
        Theme.TextStyle style = style(styleKey);
        if (textWidth(style, text) <= maxWidth) {
            return text;
        }
        String dots = "…";
        int end = text.length();
        while (end > 0 && textWidth(style, text.substring(0, end) + dots) > maxWidth) {
            end--;
        }
        return text.substring(0, end) + dots;
    }
}
