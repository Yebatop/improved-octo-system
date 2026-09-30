package dev.skirmish.module.pvp;

import java.text.Normalizer;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text helpers for server surfaces (sidebar lines, boss bar names): strips legacy colour codes, maps Unicode small
 * caps ("ᴘᴠᴘ") to plain letters, and reads durations such as "15с", "0:15", "1м 5с" or "15 sec". Pure Java.
 */
final class PvpText {
    /** Unicode small capitals used by server fonts, mapped to their plain lowercase letters. */
    private static final String SMALL_CAPS = "ᴀʙᴄᴅᴇғꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡʏᴢᴫ";
    private static final String PLAIN = "abcdeffghijklmnopqrstuvwyzл";

    private static final String NOT_WORD_BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_WORD_AFTER = "(?![\\p{L}\\p{N}])";
    private static final Pattern CLOCK = Pattern.compile(NOT_WORD_BEFORE + "(\\d{1,2}):(\\d{2})" + NOT_WORD_AFTER);
    private static final Pattern MIN_SEC = Pattern.compile(NOT_WORD_BEFORE
            + "(\\d{1,3})\\s*(?:м|мин|m|min)\\.?\\s*(\\d{1,2})\\s*(?:с|сек|s|sec)\\.?" + NOT_WORD_AFTER);
    private static final Pattern SECONDS = Pattern.compile(NOT_WORD_BEFORE
            + "(\\d{1,4})\\s*(?:с|сек|секунд[аы]?|s|sec|secs|seconds?)\\.?" + NOT_WORD_AFTER);
    private static final Pattern MINUTES = Pattern.compile(NOT_WORD_BEFORE
            + "(\\d{1,3})\\s*(?:м|мин|минут[аы]?|m|min|mins|minutes?)\\.?" + NOT_WORD_AFTER);
    private static final Pattern BARE_NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}#.,:])(\\d{1,4})(?![\\p{L}\\p{N}.,:])");
    private static final Pattern ANY_DIGIT = Pattern.compile("\\d");
    private static final int MAX_SECONDS = 3600;

    private PvpText() {
    }

    /** Removes {@code §x} formatting codes (including the {@code §x§r§r…} hex form). */
    static String stripCodes(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(c);
        }
        return out.toString();
    }

    /** Colour codes stripped, small caps mapped, NFKC, lowercase, ё → е, whitespace collapsed. */
    static String normalize(String text) {
        String s = Normalizer.normalize(stripCodes(text), Normalizer.Form.NFKC);
        StringBuilder out = new StringBuilder(s.length());
        boolean space = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int cap = SMALL_CAPS.indexOf(c);
            if (cap >= 0) {
                c = PLAIN.charAt(cap);
            }
            if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
                space = true;
                continue;
            }
            if (space && !out.isEmpty()) {
                out.append(' ');
            }
            space = false;
            out.append(c);
        }
        return out.toString().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }

    /** A duration with an explicit unit or clock form ("15с", "0:15", "1м 5с", "2 мин"), in seconds. */
    static OptionalInt explicitSeconds(String normalized) {
        Matcher m = MIN_SEC.matcher(normalized);
        if (m.find()) {
            return bounded(Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)));
        }
        m = CLOCK.matcher(normalized);
        if (m.find()) {
            int seconds = Integer.parseInt(m.group(2));
            return seconds < 60 ? bounded(Integer.parseInt(m.group(1)) * 60 + seconds) : OptionalInt.empty();
        }
        m = SECONDS.matcher(normalized);
        if (m.find()) {
            return bounded(Integer.parseInt(m.group(1)));
        }
        m = MINUTES.matcher(normalized);
        if (m.find()) {
            return bounded(Integer.parseInt(m.group(1)) * 60);
        }
        return OptionalInt.empty();
    }

    /**
     * {@link #explicitSeconds}, else the only plain number in the text ("ᴘᴠᴘ: 15"). Numbers after '#' (anarchy
     * numbers such as "#12 -◆-") and parts of decimals are ignored; two or more plain numbers are ambiguous.
     */
    static OptionalInt seconds(String normalized) {
        OptionalInt explicit = explicitSeconds(normalized);
        if (explicit.isPresent()) {
            return explicit;
        }
        Matcher m = BARE_NUMBER.matcher(normalized);
        if (!m.find()) {
            return OptionalInt.empty();
        }
        int value = Integer.parseInt(m.group(1));
        if (m.find()) {
            return OptionalInt.empty();
        }
        return bounded(value);
    }

    static boolean hasDigit(String text) {
        return ANY_DIGIT.matcher(text).find();
    }

    private static OptionalInt bounded(int seconds) {
        return seconds >= 0 && seconds <= MAX_SECONDS ? OptionalInt.of(seconds) : OptionalInt.empty();
    }
}
