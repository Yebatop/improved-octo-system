package dev.skirmish.module.hwitems;

import dev.skirmish.module.gearinspector.holy.HolyText;

/**
 * Lookup key of a HolyWorld item name. Pure Java, covered by tests.
 * <p>
 * On top of {@link HolyText#normalize} (colour codes, small caps, case, {@code ё}): every character that is not a
 * letter or a digit becomes a space ({@code «Разрывная волна»}, {@code - Рюкзак Iɴғɪɴɪᴛʏ -}, {@code Тнт-Пушка}), and
 * in short tokens (one or two characters) Cyrillic letters that look like Latin ones are read as Latin, because the
 * server writes {@code Динамит A}/{@code Динамит А} and {@code C4}/{@code С4} both ways.
 */
public final class HwItemKey {
    private static final String CYRILLIC = "авсеокмнртху";
    private static final String LATIN = "abceokmhptxy";

    private HwItemKey() {
    }

    public static String of(String raw) {
        String text = HolyText.normalize(raw);
        StringBuilder out = new StringBuilder(text.length());
        StringBuilder token = new StringBuilder();
        for (int i = 0; i <= text.length(); i++) {
            char c = i < text.length() ? text.charAt(i) : ' ';
            if (Character.isLetterOrDigit(c)) {
                token.append(c);
                continue;
            }
            if (!token.isEmpty()) {
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(token.length() <= 2 ? latinize(token) : token);
                token.setLength(0);
            }
        }
        return out.toString();
    }

    private static CharSequence latinize(CharSequence token) {
        StringBuilder out = new StringBuilder(token.length());
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            int at = CYRILLIC.indexOf(c);
            out.append(at >= 0 ? LATIN.charAt(at) : c);
        }
        return out;
    }

    /** Whether {@code key} contains {@code phrase} as whole words (both already keys). */
    public static boolean containsPhrase(String key, String phrase) {
        if (phrase.isEmpty()) {
            return false;
        }
        int from = 0;
        while (true) {
            int at = key.indexOf(phrase, from);
            if (at < 0) {
                return false;
            }
            int end = at + phrase.length();
            boolean startOk = at == 0 || key.charAt(at - 1) == ' ';
            boolean endOk = end == key.length() || key.charAt(end) == ' ';
            if (startOk && endOk) {
                return true;
            }
            from = at + 1;
        }
    }
}
