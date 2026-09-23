package dev.skirmish.module.killcard;

import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Picks installed font families that can draw the card's text. Only fonts the OS provides are used: a preferred
 * sans-serif when it covers the text (Cyrillic included), otherwise the JDK logical "SansSerif" font, which maps to
 * a composite of system fonts, and finally any installed family that covers the string.
 */
public final class CardFonts {
    private static final List<String> PREFERRED = List.of("Segoe UI", "Helvetica Neue", "Inter", "Noto Sans", "Roboto",
            "DejaVu Sans", "Liberation Sans", "Arial", "FreeSans", "Verdana", "Tahoma");
    private static final Map<String, String> FALLBACK_BY_TEXT = new ConcurrentHashMap<>();
    private static volatile Set<String> installed;

    private final String family;

    private CardFonts(String family) {
        this.family = family;
    }

    /** A font set whose main family covers {@code sample} (all labels and names of one card). */
    public static CardFonts forSample(String sample) {
        Set<String> families = installed();
        for (String name : PREFERRED) {
            if (families.contains(name) && covers(name, sample)) {
                return new CardFonts(name);
            }
        }
        return new CardFonts(Font.SANS_SERIF);
    }

    public String family() {
        return family;
    }

    /** The main family, or a fallback family for text it cannot display (exotic characters in nicknames). */
    public Font font(int style, float size, String text) {
        String name = family;
        if (!covers(family, text)) {
            name = FALLBACK_BY_TEXT.computeIfAbsent(text, CardFonts::findFallback);
        }
        return new Font(name, style, 12).deriveFont(style, size);
    }

    public boolean canDisplay(String text) {
        return covers(family, text);
    }

    private static String findFallback(String text) {
        if (covers(Font.SANS_SERIF, text)) {
            return Font.SANS_SERIF;
        }
        for (String name : installed()) {
            if (covers(name, text)) {
                return name;
            }
        }
        return Font.SANS_SERIF;
    }

    private static boolean covers(String family, String text) {
        return new Font(family, Font.PLAIN, 12).canDisplayUpTo(text) == -1;
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
