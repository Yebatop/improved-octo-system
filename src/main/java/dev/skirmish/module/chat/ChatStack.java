package dev.skirmish.module.chat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Repeated chat lines («меч infinity на ставках 1 минута» three times in a minute) become one line with « ×3» at
 * the newest place. Only lines with some words count, so card rules and blank lines never stack. Pure Java.
 */
final class ChatStack {
    /** Earlier lines looked at, and how old they may be (in game ticks). */
    static final int LOOK_BACK = 20;
    static final int MAX_AGE_TICKS = 2400;
    private static final Pattern COUNT = Pattern.compile(" ×(\\d{1,4})$");

    private ChatStack() {
    }

    /** The line without the « ×N» this module added. */
    static String base(String text) {
        Matcher m = COUNT.matcher(text);
        return m.find() ? text.substring(0, m.start()) : text;
    }

    /** N of a « ×N» line, else 1. */
    static int count(String text) {
        Matcher m = COUNT.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : 1;
    }

    /** Whether the line may stack: at least three letters or digits (not a rule, a bar or an empty line). */
    static boolean stackable(String text) {
        int n = 0;
        for (int i = 0; i < text.length() && n < 3; i++) {
            if (Character.isLetterOrDigit(text.charAt(i))) {
                n++;
            }
        }
        return n >= 3;
    }
}
