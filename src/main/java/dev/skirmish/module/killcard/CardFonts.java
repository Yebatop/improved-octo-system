package dev.skirmish.module.killcard;

import dev.skirmish.ui.Theme;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.font.TextAttribute;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The card's fonts: Manrope and JetBrains Mono from the mod's resources (same files as the in-game UI), with an
 * installed system font as fallback for characters they lack (e.g. CJK nicknames).
 */
public final class CardFonts {
    private static final String DIR = "/assets/skirmish/font/";
    private static final Map<String, Font> BASE = new ConcurrentHashMap<>();
    private static final List<String> FALLBACKS = List.of("Noto Sans", "DejaVu Sans", "Segoe UI", "Arial Unicode MS", "Arial");
    private static volatile Set<String> installed;

    private CardFonts() {
    }

    private static Font base(String file) {
        return BASE.computeIfAbsent(file, f -> {
            try (InputStream in = CardFonts.class.getResourceAsStream(DIR + f)) {
                if (in == null) {
                    throw new IllegalStateException("Missing font " + f);
                }
                return Font.createFont(Font.TRUETYPE_FONT, in);
            } catch (Exception e) {
                throw new IllegalStateException("Cannot load font " + f, e);
            }
        });
    }

    private static String file(Theme.TextStyle style) {
        if (style.mono()) {
            return style.weight() >= 600 ? "jetbrainsmono-semibold.ttf" : "jetbrainsmono-medium.ttf";
        }
        return switch (style.weight()) {
            case 800 -> "manrope-extrabold.ttf";
            case 700 -> "manrope-bold.ttf";
            case 600 -> "manrope-semibold.ttf";
            default -> "manrope-medium.ttf";
        };
    }

    /** Font for a text style, or a system fallback of similar weight when {@code text} has unsupported characters. */
    public static Font font(Theme.TextStyle style, String text) {
        Font font = base(file(style)).deriveFont(style.size());
        if (font.canDisplayUpTo(text) != -1) {
            font = new Font(fallbackFamily(text), style.weight() >= 700 ? Font.BOLD : Font.PLAIN, 12).deriveFont(style.size());
        }
        if (style.tracking() != 0f) {
            Map<TextAttribute, Object> attributes = new HashMap<>();
            attributes.put(TextAttribute.TRACKING, style.tracking() / style.size());
            font = font.deriveFont(attributes);
        }
        return font;
    }

    /** Family used for the card's names (for the debug log). */
    public static String describe(String text) {
        return base("manrope-extrabold.ttf").canDisplayUpTo(text) == -1 ? "Manrope" : "Manrope + " + fallbackFamily(text);
    }

    private static String fallbackFamily(String text) {
        Set<String> families = installed();
        for (String name : FALLBACKS) {
            if (families.contains(name) && new Font(name, Font.PLAIN, 12).canDisplayUpTo(text) == -1) {
                return name;
            }
        }
        return Font.SANS_SERIF;
    }

    private static Set<String> installed() {
        Set<String> result = installed;
        if (result == null) {
            String[] names;
            try {
                names = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
            } catch (Throwable t) {
                names = new String[0];
            }
            result = new HashSet<>(Arrays.asList(names));
            installed = result;
        }
        return result;
    }
}
