package dev.skirmish.module.hwtimers;

import dev.skirmish.module.gearinspector.holy.HolyText;

import java.util.Collection;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text helpers for server lines (chat, titles, action bar): normalisation shared with the gear inspector, a duration
 * reader ("15с", "0:15", "4:59", "1м 5с", "5 минут") and a guard against lines players typed. Pure Java.
 */
public final class TimerText {
    private static final String NOT_WORD_BEFORE = "(?<![\\p{L}\\p{N}])";
    private static final String NOT_WORD_AFTER = "(?![\\p{L}\\p{N}])";
    private static final Pattern CLOCK = Pattern.compile(NOT_WORD_BEFORE + "(\\d{1,2}):(\\d{2})" + NOT_WORD_AFTER);
    private static final Pattern MIN_SEC = Pattern.compile(NOT_WORD_BEFORE
            + "(\\d{1,3})\\s*(?:м|мин|m|min)\\.?\\s*(\\d{1,2})\\s*(?:с|сек|s|sec)\\.?" + NOT_WORD_AFTER);
    private static final Pattern SECONDS = Pattern.compile(NOT_WORD_BEFORE
            + "(\\d{1,4})\\s*(?:с|сек|секунд[аыу]?|s|sec|secs|seconds?)\\.?" + NOT_WORD_AFTER);
    private static final Pattern MINUTES = Pattern.compile(NOT_WORD_BEFORE
            + "(\\d{1,3})\\s*(?:м|мин|минут[аыу]?|m|min|mins|minutes?)\\.?" + NOT_WORD_AFTER);
    /** "Nick: text", "[G] Nick » text", "Nick ⇨ text": the part before the first separator names the speaker. */
    private static final Pattern SPEAKER = Pattern.compile("^(.{1,60}?)\\s*(?::|»|⇨|➡|->|→|>)\\s");
    private static final int MAX_SECONDS = 24 * 3600;

    private TimerText() {
    }

    /** Colour codes and small caps removed, lower case, ё → е, single spaces; never null. */
    public static String normalize(String raw) {
        return HolyText.normalize(raw);
    }

    private static final Pattern ENDED = Pattern.compile(
            "(?:закончил|заканчива|истек|спал[аио]?(?![\\p{L}])|снят|прекрат|прекращ|больше не|окончен|ended|expired|worn off)");

    /** Whether a matched line announces the end ("Стан закончился", "эффект снят") rather than a start. */
    public static boolean announcesEnd(String normalized) {
        return ENDED.matcher(normalized).find();
    }

    /** A duration with a unit or in clock form, in seconds; empty when the line names none. */
    public static OptionalInt seconds(String normalized) {
        Matcher m = MIN_SEC.matcher(normalized);
        if (m.find()) {
            return bounded(Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)));
        }
        m = CLOCK.matcher(normalized);
        if (m.find()) {
            int s = Integer.parseInt(m.group(2));
            return s < 60 ? bounded(Integer.parseInt(m.group(1)) * 60 + s) : OptionalInt.empty();
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
     * Whether a system line looks like a player's chat message ("[G] Nick: стан у тебя"): the text before the first
     * chat separator contains the nick of a player on the server. Servers that relay chat as system messages would
     * otherwise let anyone start a timer by typing a keyword.
     */
    public static boolean looksLikePlayerChat(String raw, Collection<String> onlineNames) {
        String text = normalize(raw);
        Matcher m = SPEAKER.matcher(text);
        if (!m.find()) {
            return false;
        }
        String head = m.group(1);
        for (String name : onlineNames) {
            if (name.length() >= 3 && containsWord(head, name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWord(String text, String word) {
        int at = text.indexOf(word);
        while (at >= 0) {
            boolean before = at == 0 || !isNickChar(text.charAt(at - 1));
            int end = at + word.length();
            boolean after = end >= text.length() || !isNickChar(text.charAt(end));
            if (before && after) {
                return true;
            }
            at = text.indexOf(word, at + 1);
        }
        return false;
    }

    private static boolean isNickChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static OptionalInt bounded(int seconds) {
        return seconds >= 0 && seconds <= MAX_SECONDS ? OptionalInt.of(seconds) : OptionalInt.empty();
    }
}
