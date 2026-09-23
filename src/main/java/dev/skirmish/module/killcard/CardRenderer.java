package dev.skirmish.module.killcard;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.TextAttribute;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Draws a {@link KillCardData} with Java2D. No Minecraft classes: runs on the card thread and in unit tests. */
public final class CardRenderer {
    public static final int WIDTH = 960;
    public static final int HEIGHT = 540;

    private static final int MARGIN = 36;
    private static final int GEAR_TOP = 200;
    private static final int GEAR_HEIGHT = 182;
    private static final int STATS_TOP = 398;
    private static final int STATS_HEIGHT = 92;
    private static final int SLOT_BOX = 88;
    private static final int ICON = 64;

    /**
     * @param fontFamily main font family chosen for this card
     * @param warnings   icons that failed to compose (drawn as placeholders)
     */
    public record Result(BufferedImage image, String fontFamily, List<String> warnings) {
    }

    private CardRenderer() {
    }

    public static int width(double scale) {
        return (int) Math.round(WIDTH * scale);
    }

    public static int height(double scale) {
        return (int) Math.round(HEIGHT * scale);
    }

    public static Result render(KillCardData data) {
        CardStats stats = data.stats();
        CardText text = data.text();
        CardTheme theme = data.theme();
        CardFonts fonts = CardFonts.forSample(sampleText(data));
        List<String> warnings = new ArrayList<>();

        BufferedImage image = new BufferedImage(width(data.scale()), height(data.scale()), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.scale(image.getWidth() / (double) WIDTH, image.getHeight() / (double) HEIGHT);

            background(g, theme);
            header(g, fonts, data);
            names(g, fonts, data);
            gear(g, fonts, data, warnings);
            stats(g, fonts, data);
            footer(g, fonts, text, theme, stats);
        } finally {
            g.dispose();
        }
        return new Result(image, fonts.family(), warnings);
    }

    private static String sampleText(KillCardData data) {
        CardText t = data.text();
        return t.title() + t.killerCaption() + t.victimCaption() + t.gearTitle() + t.damageLabel() + t.damageNote()
                + t.damageUnknownNote() + t.totemsLabel() + t.durationLabel() + t.hitsLabel() + t.serverLabel()
                + String.join("", t.slotNames()) + data.stats().killer() + data.stats().victim();
    }

    // ---- sections ----

    private static void background(Graphics2D g, CardTheme theme) {
        g.setPaint(new GradientPaint(0, 0, theme.backgroundTop, WIDTH, HEIGHT, theme.backgroundBottom));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        Color glow = withAlpha(theme.accent, theme.isLight() ? 0x30 : 0x48);
        g.setPaint(new RadialGradientPaint(new Point2D.Float(WIDTH - 120, 40), 420, new float[]{0f, 1f},
                new Color[]{glow, withAlpha(theme.accent, 0)}, MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        Color line = withAlpha(theme.text, theme.isLight() ? 0x10 : 0x0C);
        g.setColor(line);
        g.setStroke(new BasicStroke(1f));
        switch (theme.pattern) {
            case LINES -> {
                for (int x = -HEIGHT; x < WIDTH; x += 22) {
                    g.draw(new Line2D.Float(x, HEIGHT, x + HEIGHT, 0));
                }
            }
            case DOTS -> {
                g.setColor(withAlpha(theme.text, 0x18));
                for (int y = 12; y < HEIGHT; y += 24) {
                    for (int x = 12; x < WIDTH; x += 24) {
                        g.fill(new Ellipse2D.Float(x - 1.3f, y - 1.3f, 2.6f, 2.6f));
                    }
                }
            }
            case GRID -> {
                g.setColor(withAlpha(theme.accent, 0x14));
                for (int x = 0; x < WIDTH; x += 32) {
                    g.draw(new Line2D.Float(x, 0, x, HEIGHT));
                }
                for (int y = 0; y < HEIGHT; y += 32) {
                    g.draw(new Line2D.Float(0, y, WIDTH, y));
                }
            }
        }
        g.setPaint(new GradientPaint(0, 0, theme.accent, 0, HEIGHT, theme.accent2));
        g.fillRect(0, 0, 8, HEIGHT);
    }

    private static void header(Graphics2D g, CardFonts fonts, KillCardData data) {
        CardText text = data.text();
        CardTheme theme = data.theme();
        String title = data.stats().preview() ? text.previewTitle() : text.title();
        Font titleFont = tracked(fonts.font(Font.BOLD, 20, title), 0.14f);
        g.setColor(theme.accent);
        drawString(g, title, titleFont, MARGIN + 12, 62);

        String date = formatDate(data);
        Font dateFont = fonts.font(Font.PLAIN, 16, date);
        g.setColor(theme.muted);
        drawRight(g, date, dateFont, WIDTH - MARGIN - 12, 62);
    }

    private static void names(Graphics2D g, CardFonts fonts, KillCardData data) {
        CardText text = data.text();
        CardTheme theme = data.theme();
        int leftX = MARGIN + 12;
        int arrowX = 452;
        int rightX = 516;
        int nameWidth = arrowX - leftX - 20;

        Font caption = tracked(fonts.font(Font.BOLD, 13, text.killerCaption() + text.victimCaption()), 0.12f);
        g.setColor(theme.muted);
        drawString(g, text.killerCaption().toUpperCase(Locale.ROOT), caption, leftX, 106);
        drawString(g, text.victimCaption().toUpperCase(Locale.ROOT), caption, rightX, 106);

        g.setColor(theme.text);
        drawFitted(g, fonts, data.stats().killer(), Font.BOLD, 46, 22, leftX, 158, nameWidth);
        g.setColor(theme.accent);
        drawFitted(g, fonts, data.stats().victim(), Font.BOLD, 46, 22, rightX, 158, WIDTH - MARGIN - 12 - rightX);

        g.setStroke(new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 2; i++) {
            float x = arrowX + i * 18;
            g.setColor(i == 0 ? withAlpha(theme.accent, 0x80) : theme.accent);
            Path2D chevron = new Path2D.Float();
            chevron.moveTo(x, 124);
            chevron.lineTo(x + 16, 142);
            chevron.lineTo(x, 160);
            g.draw(chevron);
        }
    }

    private static void gear(Graphics2D g, CardFonts fonts, KillCardData data, List<String> warnings) {
        CardText text = data.text();
        CardTheme theme = data.theme();
        panel(g, theme, MARGIN, GEAR_TOP, WIDTH - 2 * MARGIN, GEAR_HEIGHT, 18);
        Font titleFont = tracked(fonts.font(Font.BOLD, 13, text.gearTitle()), 0.12f);
        g.setColor(theme.muted);
        drawString(g, text.gearTitle(), titleFont, MARGIN + 20, GEAR_TOP + 28);

        int inner = WIDTH - 2 * MARGIN - 40;
        int columnWidth = inner / 6;
        for (int i = 0; i < 6; i++) {
            int columnX = MARGIN + 20 + i * columnWidth;
            int boxX = columnX + (columnWidth - SLOT_BOX) / 2;
            int boxY = GEAR_TOP + 42;
            slot(g, fonts, theme, data.gear().get(i), boxX, boxY, data.scale(), warnings);

            CardItem item = data.gear().get(i);
            String name = item.isEmpty() ? "—" : item.name();
            Font nameFont = fonts.font(Font.BOLD, 13, name);
            g.setColor(item.isEmpty() ? theme.muted : theme.text);
            drawCentered(g, ellipsize(g, name, nameFont, columnWidth - 8), nameFont, columnX + columnWidth / 2f, boxY + SLOT_BOX + 22);
            String slotName = text.slotNames().get(i);
            Font slotFont = fonts.font(Font.PLAIN, 11, slotName);
            g.setColor(theme.muted);
            drawCentered(g, ellipsize(g, slotName, slotFont, columnWidth - 8), slotFont, columnX + columnWidth / 2f, boxY + SLOT_BOX + 40);
        }
    }

    private static void slot(Graphics2D g, CardFonts fonts, CardTheme theme, CardItem item, int x, int y, double scale,
                             List<String> warnings) {
        RoundRectangle2D box = new RoundRectangle2D.Float(x, y, SLOT_BOX, SLOT_BOX, 14, 14);
        g.setColor(theme.slot);
        g.fill(box);
        g.setColor(item.foil() ? withAlpha(new Color(0x9C5CFF), 0xC0) : theme.panelBorder);
        g.setStroke(new BasicStroke(item.foil() ? 2f : 1f));
        g.draw(box);
        if (item.isEmpty()) {
            g.setColor(withAlpha(theme.muted, 0x60));
            g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            float cx = x + SLOT_BOX / 2f;
            float cy = y + SLOT_BOX / 2f;
            g.draw(new Line2D.Float(cx - 10, cy, cx + 10, cy));
            return;
        }

        int iconX = x + (SLOT_BOX - ICON) / 2;
        int iconY = y + (SLOT_BOX - ICON) / 2;
        BufferedImage icon = null;
        if (item.icon() != null) {
            try {
                icon = IconComposer.compose(item.icon(), item.foil(), (int) Math.round(ICON * scale));
            } catch (RuntimeException e) {
                warnings.add(item.itemId() + ": " + e);
            }
        }
        if (icon != null) {
            Object previous = g.getRenderingHint(RenderingHints.KEY_INTERPOLATION);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(icon, iconX, iconY, ICON, ICON, null);
            if (previous != null) {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, previous);
            }
        } else {
            placeholder(g, fonts, theme, item.name(), x, y);
        }

        if (item.durability() >= 0) {
            float fraction = Math.max(0f, Math.min(1f, item.durability()));
            int barX = x + 14;
            int barY = y + SLOT_BOX - 11;
            int barWidth = SLOT_BOX - 28;
            g.setColor(new Color(0, 0, 0, 0xC0));
            g.fillRect(barX, barY, barWidth, 5);
            g.setColor(new Color(Color.HSBtoRGB(fraction / 3f, 1f, 1f)));
            g.fillRect(barX, barY, Math.max(1, Math.round(barWidth * fraction)), 3);
        }
        if (item.count() > 1) {
            String count = Integer.toString(item.count());
            Font countFont = fonts.font(Font.BOLD, 18, count);
            int right = x + SLOT_BOX - 8;
            int baseline = y + SLOT_BOX - (item.durability() >= 0 ? 16 : 8);
            g.setColor(new Color(0x3F, 0x3F, 0x3F));
            drawRight(g, count, countFont, right + 2, baseline + 2);
            g.setColor(Color.WHITE);
            drawRight(g, count, countFont, right, baseline);
        }
    }

    /** Stand-in for items whose icon could not be resolved (special renderers such as shields or heads). */
    private static void placeholder(Graphics2D g, CardFonts fonts, CardTheme theme, String name, int x, int y) {
        int inset = 10;
        RoundRectangle2D inner = new RoundRectangle2D.Float(x + inset, y + inset, SLOT_BOX - 2 * inset, SLOT_BOX - 2 * inset, 10, 10);
        g.setColor(withAlpha(theme.accent2, 0x30));
        g.fill(inner);
        g.setColor(withAlpha(theme.accent2, 0xA0));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f, new float[]{4f, 4f}, 0f));
        g.draw(inner);
        Font font = fonts.font(Font.BOLD, 11, name);
        List<String> lines = wrap(g, name, font, SLOT_BOX - 2 * inset - 8, 3);
        FontMetrics metrics = g.getFontMetrics(font);
        float lineHeight = metrics.getAscent() + 1;
        float top = y + SLOT_BOX / 2f - lines.size() * lineHeight / 2f + metrics.getAscent() - 1;
        g.setColor(theme.text);
        for (int i = 0; i < lines.size(); i++) {
            drawCentered(g, lines.get(i), font, x + SLOT_BOX / 2f, top + i * lineHeight);
        }
    }

    private static void stats(Graphics2D g, CardFonts fonts, KillCardData data) {
        CardStats stats = data.stats();
        CardText text = data.text();
        CardTheme theme = data.theme();
        int gap = 16;
        int tileWidth = (WIDTH - 2 * MARGIN - 3 * gap) / 4;

        String approx = fonts.canDisplay("≈") ? "≈ " : "~ ";
        String damageValue = stats.damageKnown()
                ? approx + String.format(Locale.ROOT, "%.1f", stats.damageDealt()) + " " + text.hpUnit()
                : text.damageUnknownValue();
        String damageNote = stats.damageKnown() ? text.damageNote() : text.damageUnknownNote();

        String[][] tiles = {
                {text.damageLabel(), damageValue, damageNote},
                {text.totemsLabel(), Integer.toString(stats.totemsPopped()), text.totemsNote()},
                {text.durationLabel(), formatDuration(stats.durationMs(), text.secondsUnit()), ""},
                {text.hitsLabel(), stats.hitsDealt() + " / " + stats.hitsTaken(), text.hitsNote()},
        };
        for (int i = 0; i < tiles.length; i++) {
            int x = MARGIN + i * (tileWidth + gap);
            panel(g, theme, x, STATS_TOP, tileWidth, STATS_HEIGHT, 14);
            boolean warn = i == 0 && !stats.damageKnown();
            Font labelFont = tracked(fonts.font(Font.BOLD, 12, tiles[i][0]), 0.1f);
            g.setColor(theme.muted);
            drawString(g, ellipsize(g, tiles[i][0], labelFont, tileWidth - 28), labelFont, x + 16, STATS_TOP + 26);

            g.setColor(warn ? theme.accent2 : (i == 1 && stats.totemsPopped() > 0 ? theme.accent : theme.text));
            drawFitted(g, fonts, tiles[i][1], Font.BOLD, 30, 16, x + 16, STATS_TOP + 62, tileWidth - 32);

            if (!tiles[i][2].isEmpty()) {
                Font noteFont = fonts.font(Font.PLAIN, 12, tiles[i][2]);
                g.setColor(warn ? theme.accent2 : theme.muted);
                drawString(g, ellipsize(g, tiles[i][2], noteFont, tileWidth - 28), noteFont, x + 16, STATS_TOP + 81);
            }
        }
    }

    private static void footer(Graphics2D g, CardFonts fonts, CardText text, CardTheme theme, CardStats stats) {
        int baseline = HEIGHT - 20;
        if (stats.server() != null && !stats.server().isBlank()) {
            String server = text.serverLabel() + ": " + stats.server();
            Font font = fonts.font(Font.PLAIN, 14, server);
            g.setColor(theme.muted);
            drawString(g, ellipsize(g, server, font, WIDTH / 2 - MARGIN), font, MARGIN + 12, baseline);
        }
        Font brand = tracked(fonts.font(Font.BOLD, 13, text.footer()), 0.08f);
        g.setColor(withAlpha(theme.muted, 0xB0));
        drawRight(g, text.footer(), brand, WIDTH - MARGIN - 12, baseline);
    }

    // ---- formatting ----

    static String formatDuration(long ms, String secondsUnit) {
        long clamped = Math.max(0, ms);
        if (clamped < 60_000) {
            return String.format(Locale.ROOT, "%.1f %s", clamped / 1000.0, secondsUnit);
        }
        long seconds = clamped / 1000;
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private static String formatDate(KillCardData data) {
        try {
            return DateTimeFormatter.ofPattern(data.text().datePattern(), Locale.ROOT).format(data.stats().time());
        } catch (IllegalArgumentException e) {
            return DateTimeFormatter.ofPattern(CardText.DEFAULTS.get("date_pattern"), Locale.ROOT).format(data.stats().time());
        }
    }

    // ---- drawing helpers ----

    private static void panel(Graphics2D g, CardTheme theme, int x, int y, int w, int h, int radius) {
        Shape shape = new RoundRectangle2D.Float(x, y, w, h, radius, radius);
        g.setColor(theme.panel);
        g.fill(shape);
        g.setColor(theme.panelBorder);
        g.setStroke(new BasicStroke(1f));
        g.draw(shape);
    }

    private static Font tracked(Font font, float tracking) {
        return font.deriveFont(Map.of(TextAttribute.TRACKING, tracking));
    }

    private static void drawString(Graphics2D g, String text, Font font, float x, float baseline) {
        g.setFont(font);
        g.drawString(text, x, baseline);
    }

    private static void drawRight(Graphics2D g, String text, Font font, float right, float baseline) {
        g.setFont(font);
        g.drawString(text, right - g.getFontMetrics().stringWidth(text), baseline);
    }

    private static void drawCentered(Graphics2D g, String text, Font font, float centerX, float baseline) {
        g.setFont(font);
        g.drawString(text, centerX - g.getFontMetrics().stringWidth(text) / 2f, baseline);
    }

    /** Shrinks the font down to {@code minSize} to fit {@code maxWidth}, then ellipsizes. */
    private static void drawFitted(Graphics2D g, CardFonts fonts, String text, int style, float maxSize, float minSize,
                                   float x, float baseline, int maxWidth) {
        Font font = fonts.font(style, maxSize, text);
        float size = maxSize;
        while (size > minSize && g.getFontMetrics(font).stringWidth(text) > maxWidth) {
            size -= 2;
            font = font.deriveFont(size);
        }
        drawString(g, ellipsize(g, text, font, maxWidth), font, x, baseline);
    }

    static String ellipsize(Graphics2D g, String text, Font font, int maxWidth) {
        FontMetrics metrics = g.getFontMetrics(font);
        if (metrics.stringWidth(text) <= maxWidth) {
            return text;
        }
        String dots = font.canDisplay('…') ? "…" : "...";
        int end = text.length();
        while (end > 0 && metrics.stringWidth(text.substring(0, end) + dots) > maxWidth) {
            end = Character.isLowSurrogate(text.charAt(end - 1)) && end > 1 ? end - 2 : end - 1;
        }
        return text.substring(0, end).stripTrailing() + dots;
    }

    private static List<String> wrap(Graphics2D g, String text, Font font, int maxWidth, int maxLines) {
        FontMetrics metrics = g.getFontMetrics(font);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (metrics.stringWidth(candidate) <= maxWidth || line.isEmpty()) {
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
        if (lines.size() > maxLines) {
            List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
            cut.set(maxLines - 1, cut.get(maxLines - 1) + " " + String.join(" ", lines.subList(maxLines, lines.size())));
            lines = cut;
        }
        lines.replaceAll(l -> ellipsize(g, l, font, maxWidth));
        return lines;
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }
}
