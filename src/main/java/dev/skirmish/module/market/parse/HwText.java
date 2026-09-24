package dev.skirmish.module.market.parse;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Text clean-up for HolyWorld item names, lore and chat lines. Pure Java (unit tested).
 * <ul>
 *     <li>{@link #plain}: drops legacy {@code §} formatting codes and decoration glyphs such as {@code ▍ ▶ ▎ ●}.</li>
 *     <li>{@link #key}: {@link #plain} + lower case + Unicode small caps ({@code ᴀʀᴍᴏʀ}) to Latin + Latin look-alikes
 *     to Cyrillic ({@code Продaвeц} → {@code продавец}), for keyword matching only. It maps one char to one char,
 *     so an index found in the key is valid in the plain text.</li>
 * </ul>
 */
public final class HwText {
    private static final Pattern LEGACY_CODE = Pattern.compile("§.?");
    /** Bars, arrows, bullets and box glyphs servers put around lore values. */
    private static final Pattern DECORATION = Pattern.compile("[▍▎▏▌▋▊▉█▶►▸▹›»«‹◆◇●•·■□▪▫★☆✦✧❖➤➜⟶→⇒|┃│]");
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇғɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";
    private static final String SMALL_CAPS_LATIN = "abcdefghijklmnopqrstuvwxyz";
    /** Latin letters that look like Cyrillic ones (used by servers and cheat-proofing to break naive matching). */
    private static final String LATIN_LOOKALIKE = "aeopcxykmhtb";
    private static final String CYRILLIC_LOOKALIKE = "аеорсхукмнтв";

    private HwText() {
    }

    /** Without § codes and decoration glyphs, whitespace collapsed and trimmed. Case is kept (nicks). */
    public static String plain(String text) {
        if (text == null) {
            return "";
        }
        String s = LEGACY_CODE.matcher(text).replaceAll("");
        s = DECORATION.matcher(s).replaceAll(" ");
        s = s.replace('\u00A0', ' ').replace('\u202F', ' ').replace('\u2007', ' ');
        return s.replaceAll("\\s+", " ").trim();
    }

    /** Lower case, small caps folded to Latin; same length as the input. */
    public static String fold(String plain) {
        StringBuilder out = new StringBuilder(plain.length());
        for (int i = 0; i < plain.length(); i++) {
            char c = plain.charAt(i);
            int sc = SMALL_CAPS.indexOf(c);
            if (sc >= 0) {
                c = SMALL_CAPS_LATIN.charAt(sc);
            }
            out.append(Character.toLowerCase(c));
        }
        return out.toString().replace('ё', 'е');
    }

    /**
     * Keyword form of already {@link #plain} text: {@link #fold} plus Latin look-alikes turned Cyrillic inside
     * words that contain Cyrillic letters. Same length as the input.
     */
    public static String key(String plain) {
        char[] chars = fold(plain).toCharArray();
        int start = 0;
        while (start < chars.length) {
            if (!Character.isLetter(chars[start])) {
                start++;
                continue;
            }
            int end = start;
            boolean cyrillic = false;
            while (end < chars.length && Character.isLetter(chars[end])) {
                cyrillic |= Character.UnicodeBlock.of(chars[end]) == Character.UnicodeBlock.CYRILLIC;
                end++;
            }
            if (cyrillic) {
                for (int i = start; i < end; i++) {
                    int l = LATIN_LOOKALIKE.indexOf(chars[i]);
                    if (l >= 0) {
                        chars[i] = CYRILLIC_LOOKALIKE.charAt(l);
                    }
                }
            }
            start = end;
        }
        return new String(chars);
    }

    /** {@code key(plain(text))}. */
    public static String normalize(String text) {
        return key(plain(text));
    }

    public static String lower(String text) {
        return text.toLowerCase(Locale.ROOT);
    }
}
