package dev.skirmish.module.gearinspector.holy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text normalisation for HolyWorld item names and lore. Pure Java, covered by tests.
 * <p>
 * HolyWorld writes names in plain Cyrillic ({@code Шлем инфинити}), Latin ({@code Нагрудник Kraken}) or Unicode small
 * caps ({@code - Рюкзак Iɴғɪɴɪᴛʏ -}), sometimes with legacy {@code §} colour codes left in the string. Everything is
 * matched on the normalised form: codes stripped, small caps mapped to Latin, lower case, {@code ё → е}, single spaces.
 */
public final class HolyText {
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀѕᴛᴜᴠᴡʏᴢ";
    private static final String LATIN = "abcdefghijklmnopqrstuvwyz";

    private HolyText() {
    }

    /** Lower case, codes and small caps removed, runs of whitespace collapsed; never null. */
    public static String normalize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '§' && i + 1 < raw.length()) {
                i++;
                continue;
            }
            int small = SMALL_CAPS.indexOf(c);
            if (small >= 0) {
                c = LATIN.charAt(small);
            }
            out.append(c);
        }
        String lower = out.toString().toLowerCase(Locale.ROOT).replace('ё', 'е');
        return lower.replaceAll("[\\s\\u00A0]+", " ").trim();
    }

    /**
     * Words of the normalised text: runs of letters and digits; a hyphen inside a word is kept ({@code мега-бур}).
     */
    public static List<String> words(String raw) {
        String text = normalize(raw);
        List<String> words = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean inner = c == '-' && !current.isEmpty() && i + 1 < text.length()
                    && Character.isLetterOrDigit(text.charAt(i + 1));
            if (Character.isLetterOrDigit(c) || inner) {
                current.append(c);
            } else if (!current.isEmpty()) {
                words.add(current.toString());
                current.setLength(0);
            }
        }
        if (!current.isEmpty()) {
            words.add(current.toString());
        }
        return words;
    }

    /**
     * Level token in Roman ({@code I}…{@code XXX}, any case) or Arabic ({@code 1}…{@code 255}) notation; -1 if the
     * token is not a level.
     */
    public static int level(String token) {
        if (token == null || token.isEmpty()) {
            return -1;
        }
        String t = token.trim();
        if (t.chars().allMatch(Character::isDigit)) {
            if (t.length() > 3) {
                return -1;
            }
            int value = Integer.parseInt(t);
            return value >= 1 && value <= 255 ? value : -1;
        }
        String upper = t.toUpperCase(Locale.ROOT);
        int value = 0;
        int previous = Integer.MAX_VALUE;
        for (int i = 0; i < upper.length(); i++) {
            int digit = switch (upper.charAt(i)) {
                case 'I' -> 1;
                case 'V' -> 5;
                case 'X' -> 10;
                default -> -1;
            };
            if (digit < 0) {
                return -1;
            }
            value += digit > previous ? digit - 2 * previous : digit;
            previous = digit;
        }
        // Only canonical spellings count, so "IIII" or "VX" are not levels.
        return value >= 1 && value <= 30 && roman(value).equals(upper) ? value : -1;
    }

    /** 1 → I, 4 → IV, 13 → XIII; Arabic digits above 39. */
    public static String roman(int value) {
        if (value < 1 || value > 39) {
            return Integer.toString(value);
        }
        StringBuilder out = new StringBuilder();
        int rest = value;
        while (rest >= 10) {
            out.append('X');
            rest -= 10;
        }
        String[] units = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX"};
        return out.append(units[rest]).toString();
    }
}
