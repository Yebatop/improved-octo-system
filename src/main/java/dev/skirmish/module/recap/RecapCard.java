package dev.skirmish.module.recap;

import dev.skirmish.module.killcard.CardFonts;
import dev.skirmish.ui.Theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.TextLayout;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * The «Итоги сессии» PNG, drawn with Java2D like the KillCard ({@code killcard.CardRenderer}): same fonts
 * ({@link CardFonts}), colours and text styles from theme.json, layout under {@code layout.recap_card}. No Minecraft
 * classes: runs on the export thread and in unit tests.
 */
public final class RecapCard {
    private static final String L = "layout.recap_card.";

    /**
     * Everything printed, resolved on the client thread.
     *
     * @param title      big line (my name)
     * @param subtitle   server · date · time played
     * @param badge      header badge ("ИТОГИ СЕССИИ")
     * @param tiles      eight stat tiles
     * @param bestLabel  "ЛУЧШИЙ БОЙ"
     * @param bestTitle  "против Nick" or the no-fight text
     * @param bestDetail damage · hits · result · duration, may be empty
     * @param footer     bottom line
     */
    public record Data(String title, String subtitle, String badge, List<RecapLines.Tile> tiles, String bestLabel,
                       String bestTitle, String bestDetail, String footer, double scale) {
    }

    private final Theme theme = Theme.get();
    private final Graphics2D g;

    private RecapCard(Graphics2D g) {
        this.g = g;
    }

    public static BufferedImage render(Data data) {
        Theme theme = Theme.get();
        int w = (int) Math.round(theme.num(L + "width") * data.scale());
        int h = (int) Math.round(theme.num(L + "height") * data.scale());
        BufferedImage image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(data.scale(), data.scale());
            new RecapCard(g).draw(data);
        } finally {
            g.dispose();
        }
        return image;
    }

    private void draw(Data d) {
        float w = theme.num(L + "width");
        float h = theme.num(L + "height");
        fill(new Rectangle2D.Float(0, 0, w, h), "card");
        float size = theme.num(L + "deco_size");
        float ring = theme.num(L + "deco_width");
        g.setColor(color("accent_08"));
        g.setStroke(new BasicStroke(ring));
        float rx = w - size - theme.num(L + "deco_right");
        float ry = theme.num(L + "deco_top");
        g.draw(new Ellipse2D.Float(rx + ring / 2f, ry + ring / 2f, size - ring, size - ring));

        float padX = theme.num(L + "pad_x");
        float padY = theme.num(L + "pad_y");
        float gap = theme.num(L + "gap");
        float x = padX;
        float cw = w - padX * 2;
        float y = padY;

        // Header: logo tile, brand, badge.
        float logo = theme.num(L + "logo");
        float bpx = theme.num(L + "badge_pad_x");
        float bpy = theme.num(L + "badge_pad_y");
        float badgeH = lineHeight("card_badge") + bpy * 2;
        float headerH = Math.max(logo, badgeH);
        fill(round(x, y + (headerH - logo) / 2f, logo, logo, theme.radius("card_logo")), "accent");
        logo(x, y + (headerH - logo) / 2f, logo, color("white"));
        text("card_brand", "Skirmish", x + logo + theme.num(L + "header_gap"), y + (headerH - lineHeight("card_brand")) / 2f, null);
        float badgeW = width("card_badge", d.badge()) + bpx * 2;
        fill(round(x + cw - badgeW, y + (headerH - badgeH) / 2f, badgeW, badgeH, badgeH / 2f), "accent_14");
        text("card_badge", d.badge(), x + cw - badgeW + bpx, y + (headerH - badgeH) / 2f + bpy, null);
        y += headerH + gap;

        // Title and subtitle.
        text("card_name", ellipsize("card_name", d.title(), cw), x, y, null);
        y += lineHeight("card_name") + theme.num(L + "title_gap");
        text("recap_card_sub", ellipsize("recap_card_sub", d.subtitle(), cw), x, y, null);
        y += lineHeight("recap_card_sub") + gap;

        // 4 × 2 tiles.
        int cols = 4;
        float tg = theme.num(L + "tile_gap");
        float tp = theme.num(L + "tile_pad");
        float tileW = (cw - tg * (cols - 1)) / cols;
        float tileH = tp * 2 + lineHeight("recap_card_value") + theme.num(L + "tile_value_gap") + lineHeight("card_stat_label");
        List<RecapLines.Tile> tiles = d.tiles();
        for (int i = 0; i < tiles.size(); i++) {
            RecapLines.Tile t = tiles.get(i);
            float tx = x + (i % cols) * (tileW + tg);
            float ty = y + (i / cols) * (tileH + tg);
            fill(round(tx, ty, tileW, tileH, theme.radius("tile")), "tile");
            text("recap_card_value", ellipsize("recap_card_value", t.value(), tileW - tp * 2), tx + tp, ty + tp, t.color());
            text("card_stat_label", ellipsize("card_stat_label", t.label(), tileW - tp * 2), tx + tp,
                    ty + tp + lineHeight("recap_card_value") + theme.num(L + "tile_value_gap"), null);
        }
        int rows = (tiles.size() + cols - 1) / cols;
        y += rows * tileH + (rows - 1) * tg + gap;

        // Best fight.
        float bp = theme.num(L + "tile_pad");
        float bestH = bp * 2 + lineHeight("recap_card_label") + theme.num(L + "tile_value_gap") + lineHeight("recap_card_best")
                + (d.bestDetail().isEmpty() ? 0 : theme.num(L + "tile_value_gap") + lineHeight("recap_card_sub"));
        fill(round(x, y, cw, bestH, theme.radius("tile")), "tile");
        float by = y + bp;
        text("recap_card_label", d.bestLabel(), x + bp, by, null);
        by += lineHeight("recap_card_label") + theme.num(L + "tile_value_gap");
        text("recap_card_best", ellipsize("recap_card_best", d.bestTitle(), cw - bp * 2), x + bp, by, null);
        by += lineHeight("recap_card_best") + theme.num(L + "tile_value_gap");
        if (!d.bestDetail().isEmpty()) {
            text("recap_card_sub", ellipsize("recap_card_sub", d.bestDetail(), cw - bp * 2), x + bp, by, null);
        }

        // Footer.
        float fy = h - padY - lineHeight("card_date");
        text("card_date", d.footer(), x + cw - width("card_date", d.footer()), fy, null);
    }

    /** The Skirmish logo emblem (same geometry as ModuleIcons.logo). */
    private void logo(float x, float y, float size, Color color) {
        float s = size / 24f;
        g.setColor(color);
        g.setStroke(new BasicStroke(2.4f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path(x, y, s, 17f, 4.825f, 8f, 4.825f, 5.5f, 7.325f, 5.5f, 9.825f, 10.6f, 11.525f));
        g.draw(path(x, y, s, 13.4f, 12.475f, 18.5f, 14.175f, 18.5f, 16.675f, 16f, 19.175f, 7f, 19.175f));
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 170));
        g.setStroke(new BasicStroke(0.9f * s, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(path(x, y, s, 4.2f, 19.8f, 8.3f, 15.7f));
        g.draw(path(x, y, s, 15.7f, 8.3f, 19.8f, 4.2f));
    }

    private static Path2D.Float path(float x, float y, float s, float... points) {
        Path2D.Float p = new Path2D.Float();
        p.moveTo(x + points[0] * s, y + points[1] * s);
        for (int i = 2; i < points.length; i += 2) {
            p.lineTo(x + points[i] * s, y + points[i + 1] * s);
        }
        return p;
    }

    private float lineHeight(String key) {
        return theme.lineHeight(theme.text(key));
    }

    private float width(String key, String text) {
        if (text.isEmpty()) {
            return 0f;
        }
        Font font = CardFonts.font(theme.text(key), text);
        return (float) new TextLayout(text, font, g.getFontRenderContext()).getAdvance();
    }

    private void text(String key, String text, float x, float top, String colorOverride) {
        if (text.isEmpty()) {
            return;
        }
        Theme.TextStyle style = theme.text(key);
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
}
