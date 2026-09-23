package dev.skirmish.module.killcard;

import dev.skirmish.ui.Theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.TexturePaint;
import java.awt.font.FontRenderContext;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Draws a {@link KillCardData} with Java2D, laid out like the mockup «KillCard» (800×420, ×scale). Colors, sizes
 * and text styles come from theme.json; fonts are the mod's Manrope / JetBrains Mono. No Minecraft classes: runs on
 * the card thread and in unit tests.
 */
public final class CardRenderer {
    private static final String L = "layout.card.";

    /**
     * @param fontFamily font used for the names (for the debug log)
     * @param warnings   icons that failed to compose (drawn as empty tiles)
     */
    public record Result(BufferedImage image, String fontFamily, List<String> warnings) {
    }

    private final Theme theme = Theme.get();
    private final Graphics2D g;
    private final double scale;

    private CardRenderer(Graphics2D g, double scale) {
        this.g = g;
        this.scale = scale;
    }

    public static int width(double scale) {
        return (int) Math.round(Theme.get().num(L + "width") * scale);
    }

    public static int height(double scale) {
        return (int) Math.round(Theme.get().num(L + "height") * scale);
    }

    public static Result render(KillCardData data) {
        BufferedImage image = new BufferedImage(width(data.scale()), height(data.scale()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        List<String> warnings = new ArrayList<>();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(data.scale(), data.scale());
            new CardRenderer(g, data.scale()).draw(data, warnings);
        } finally {
            g.dispose();
        }
        return new Result(image, CardFonts.describe(data.stats().killer() + data.stats().victim()), warnings);
    }

    private void draw(KillCardData data, List<String> warnings) {
        CardStats stats = data.stats();
        CardText text = data.text();
        float w = theme.num(L + "width");
        float h = theme.num(L + "height");
        fill(new Rectangle2D.Float(0, 0, w, h), "card");

        // Decorative ring: 240 px box, 40 px border, 60 px past the top-right corner.
        float size = theme.num(L + "deco_size");
        float ring = theme.num(L + "deco_width");
        float rx = w - size - theme.num(L + "deco_right");
        float ry = theme.num(L + "deco_top");
        g.setColor(color("accent_08"));
        g.setStroke(new BasicStroke(ring));
        g.draw(new Ellipse2D.Float(rx + ring / 2f, ry + ring / 2f, size - ring, size - ring));

        float padX = theme.num(L + "pad_x");
        float padY = theme.num(L + "pad_y");
        float gap = theme.num(L + "gap");
        float x = padX;
        float cw = w - padX * 2;
        float y = padY;

        // Header: logo tile, "Skirmish", badge on the right.
        String badge = stats.preview() ? text.previewBadge() : text.badge();
        float bpx = theme.num(L + "badge_pad_x");
        float bpy = theme.num(L + "badge_pad_y");
        float badgeH = lineHeight("card_badge") + bpy * 2;
        float logo = theme.num(L + "logo");
        float headerH = Math.max(logo, badgeH);
        float ly = y + (headerH - logo) / 2f;
        fill(round(x, ly, logo, logo, theme.radius("card_logo")), "accent");
        float icon = theme.num(L + "logo_icon");
        sword(x + (logo - icon) / 2f, ly + (logo - icon) / 2f, icon, 2.6f, color("white"));
        text("card_brand", "Skirmish", x + logo + theme.num(L + "header_gap"), y + (headerH - lineHeight("card_brand")) / 2f, null);
        float badgeW = width("card_badge", badge) + bpx * 2;
        float by = y + (headerH - badgeH) / 2f;
        fill(round(x + cw - badgeW, by, badgeW, badgeH, badgeH / 2f), "accent_14");
        text("card_badge", badge, x + cw - badgeW + bpx, by + bpy, null);
        y += headerH + gap;

        // Players: me (remaining HP) — "против" — the opponent (killed).
        float avatar = theme.num(L + "avatar");
        float playersGap = theme.num(L + "players_gap");
        float nameMax = (cw - playersGap * 2 - width("card_vs", text.vs())) / 2f - avatar - theme.num(L + "player_gap");
        String remaining = stats.killerHealth() < 0 ? "" : String.format(Locale.ROOT, text.remaining(), decimal(stats.killerHealth(), 1, text));
        float px = player(x, y, stats.killer(), remaining, "card_name", "card_sub_good", stats.killerFace(), nameMax);
        px += playersGap;
        text("card_vs", text.vs(), px, y + (avatar - lineHeight("card_vs")) / 2f, null);
        px += width("card_vs", text.vs()) + playersGap;
        player(px, y, stats.victim(), text.killed(), "card_name_lost", "card_sub_bad", stats.victimFace(), nameMax);
        y += avatar + gap;

        // Four stat tiles.
        String damage = stats.damageKnown() ? decimal(stats.damageDealt(), 1, text) : text.damageUnknown();
        String accuracy = stats.attackAttempts() > 0 ? Math.round(100.0 * stats.hitsDealt() / stats.attackAttempts()) + "%" : "—";
        String[][] tiles = {
                {damage, text.damageLabel()},
                {Integer.toString(stats.totemsPopped()), text.totemsLabel()},
                {accuracy, text.accuracyLabel()},
                {formatDuration(stats.durationMs()), text.durationLabel()}};
        float statsGap = theme.num(L + "stats_gap");
        float tileW = (cw - statsGap * 3) / 4f;
        float tilePad = theme.num(L + "tile_pad");
        float tileH = tilePad * 2 + lineHeight("card_stat_value") + theme.num(L + "tile_gap") + lineHeight("card_stat_label");
        for (int i = 0; i < tiles.length; i++) {
            float tx = x + i * (tileW + statsGap);
            fill(round(tx, y, tileW, tileH, theme.radius("tile")), "tile");
            text("card_stat_value", ellipsize("card_stat_value", tiles[i][0], tileW - tilePad * 2), tx + tilePad, y + tilePad, null);
            text("card_stat_label", ellipsize("card_stat_label", tiles[i][1], tileW - tilePad * 2), tx + tilePad,
                    y + tilePad + lineHeight("card_stat_value") + theme.num(L + "tile_gap"), null);
        }

        // Footer, bottom aligned: opponent's gear, date · server.
        float gear = theme.num(L + "gear");
        float footerH = Math.max(gear, Math.max(lineHeight("card_footer"), lineHeight("card_date")));
        float fy = h - padY - footerH;
        text("card_footer", text.gearLabel(), x, fy + (footerH - lineHeight("card_footer")) / 2f, null);
        float gx = x + width("card_footer", text.gearLabel()) + theme.num(L + "footer_gap");
        for (CardItem item : data.gear()) {
            if (item.isEmpty()) {
                continue;
            }
            fill(round(gx, fy + (footerH - gear) / 2f, gear, gear, theme.radius("card_gear")), "tile");
            drawIcon(item, gx, fy + (footerH - gear) / 2f, gear, warnings);
            gx += gear + theme.num(L + "gear_gap");
        }
        String date = stats.time().format(DateTimeFormatter.ofPattern(text.datePattern(), Locale.ROOT))
                + (stats.server() == null || stats.server().isBlank() ? "" : " · " + stats.server());
        text("card_date", date, x + cw - width("card_date", date), fy + (footerH - lineHeight("card_date")) / 2f, null);
    }

    /** Avatar + name + sub line; returns the x after the block. */
    private float player(float x, float y, String name, String sub, String nameStyle, String subStyle, int[] face, float nameMax) {
        float avatar = theme.num(L + "avatar");
        RoundRectangle2D shape = round(x, y, avatar, avatar, theme.radius("card_avatar"));
        if (face != null && face.length >= 64) {
            BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, 8, 8, face, 0, 8);
            Object interpolation = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setPaint(new TexturePaint(img, new Rectangle2D.Float(x, y, avatar, avatar)));
            g.fill(shape);
            if (interpolation != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
            }
        } else {
            fill(shape, "tile");
            String initial = name.isEmpty() ? "?" : name.substring(0, name.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
            text("card_name", initial, x + (avatar - width("card_name", initial)) / 2f, y + (avatar - lineHeight("card_name")) / 2f, "text_3");
        }
        float tx = x + avatar + theme.num(L + "player_gap");
        String shown = ellipsize(nameStyle, name, nameMax);
        float textH = lineHeight(nameStyle) + (sub.isEmpty() ? 0 : theme.num(L + "name_gap") + lineHeight(subStyle));
        float ty = y + (avatar - textH) / 2f;
        text(nameStyle, shown, tx, ty, null);
        if (!sub.isEmpty()) {
            text(subStyle, sub, tx, ty + lineHeight(nameStyle) + theme.num(L + "name_gap"), null);
        }
        return tx + Math.max(width(nameStyle, shown), sub.isEmpty() ? 0 : width(subStyle, sub));
    }

    private void drawIcon(CardItem item, float x, float y, float size, List<String> warnings) {
        IconSource source = item.icon();
        if (source == null) {
            warnings.add(item.itemId() + ": " + item.iconInfo());
            return;
        }
        float inner = size - 4;
        try {
            BufferedImage icon = IconComposer.compose(source, item.foil(), (int) Math.ceil(inner * scale));
            java.awt.geom.AffineTransform at = new java.awt.geom.AffineTransform();
            at.translate(x + 2, y + 2);
            at.scale(inner / icon.getWidth(), inner / icon.getHeight());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(icon, at, null);
        } catch (RuntimeException e) {
            warnings.add(item.itemId() + ": " + e);
        }
    }

    /** SVG sword {@code M14.5 17.5L3 6V3h3l11.5 11.5 M13 19l6-6} with round caps and joins. */
    private void sword(float x, float y, float size, float stroke, Color color) {
        float s = size / 24f;
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        Path2D.Float blade = new Path2D.Float();
        blade.moveTo(x + 14.5f * s, y + 17.5f * s);
        blade.lineTo(x + 3f * s, y + 6f * s);
        blade.lineTo(x + 3f * s, y + 3f * s);
        blade.lineTo(x + 6f * s, y + 3f * s);
        blade.lineTo(x + 17.5f * s, y + 14.5f * s);
        g.draw(blade);
        g.draw(new Line2D.Float(x + 13f * s, y + 19f * s, x + 19f * s, y + 13f * s));
    }

    // ---- text: CSS line boxes from theme.json, like the in-game UI ----

    private Theme.TextStyle style(String key) {
        return theme.text(key);
    }

    private float lineHeight(String key) {
        return theme.lineHeight(style(key));
    }

    private float width(String key, String text) {
        if (text.isEmpty()) {
            return 0f;
        }
        Font font = CardFonts.font(style(key), text);
        return (float) new TextLayout(text, font, frc()).getAdvance();
    }

    private void text(String key, String text, float x, float top, String colorOverride) {
        if (text.isEmpty()) {
            return;
        }
        Theme.TextStyle style = style(key);
        g.setFont(CardFonts.font(style, text));
        g.setColor(color(colorOverride != null ? colorOverride : style.color()));
        g.drawString(text, x, top + theme.baseline(style));
    }

    private String ellipsize(String key, String text, float max) {
        if (width(key, text) <= max) {
            return text;
        }
        int end = text.length();
        while (end > 0 && width(key, text.substring(0, end) + "…") > max) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    private FontRenderContext frc() {
        return g.getFontRenderContext();
    }

    private Color color(String token) {
        return new Color(theme.color(token), true);
    }

    private void fill(java.awt.Shape shape, String token) {
        g.setPaint(color(token));
        g.fill(shape);
    }

    private static RoundRectangle2D round(float x, float y, float w, float h, float r) {
        float d = Math.min(r * 2, Math.min(w, h));
        return new RoundRectangle2D.Float(x, y, w, h, d, d);
    }

    static String decimal(double value, int digits, CardText text) {
        return String.format(Locale.ROOT, "%." + digits + "f", value).replace('.', text.decimal());
    }

    /** m:ss */
    public static String formatDuration(long ms) {
        long s = Math.max(0, ms / 1000);
        return (s / 60) + ":" + String.format(Locale.ROOT, "%02d", s % 60);
    }
}
