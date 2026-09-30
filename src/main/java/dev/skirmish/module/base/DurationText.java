package dev.skirmish.module.base;

import org.jspecify.annotations.Nullable;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Countdowns in hologram text: «Налог через 2д 3ч», «Защита: 12:34:56», «Эффект Сила 5 мин», «1h 20m». Finds the
 * first duration in a line and the label before it. Pure Java.
 */
final class DurationText {
    /** A countdown: what it is and how long is left. */
    record Found(String label, long millis) {
    }

    private static final Pattern CLOCK = Pattern.compile("(?<![\\d:])(\\d{1,3}):(\\d{2})(?::(\\d{2}))?(?![\\d:])");
    private static final Pattern PART = Pattern.compile(
            "(\\d+)\\s*(дн(?:я|ей|\\.)?|д\\.?|d|час(?:а|ов)?|ч\\.?|h|мин(?:ут[аы]?|\\.)?|м\\.?|m|сек(?:унд[аы]?|\\.)?|с\\.?|s)(?![a-zа-яё])",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private DurationText() {
    }

    static @Nullable Found find(String line) {
        String text = line.strip();
        Matcher clock = CLOCK.matcher(text);
        Matcher part = PART.matcher(text);
        boolean hasClock = clock.find();
        boolean hasPart = part.find();
        if (!hasClock && !hasPart) {
            return null;
        }
        if (hasClock && (!hasPart || clock.start() <= part.start())) {
            long a = Long.parseLong(clock.group(1));
            long b = Long.parseLong(clock.group(2));
            long ms = clock.group(3) != null ? ((a * 60 + b) * 60 + Long.parseLong(clock.group(3))) * 1000 : (a * 60 + b) * 1000;
            return new Found(label(text.substring(0, clock.start())), ms);
        }
        int start = part.start();
        long ms = Long.parseLong(part.group(1)) * unit(part.group(2));
        int end = part.end();
        // «2д 3ч 15м»: keep adding parts separated only by spaces.
        while (part.find() && text.substring(end, part.start()).isBlank()) {
            ms += Long.parseLong(part.group(1)) * unit(part.group(2));
            end = part.end();
        }
        return ms <= 0 ? null : new Found(label(text.substring(0, start)), ms);
    }

    private static long unit(String raw) {
        String u = raw.toLowerCase(Locale.ROOT);
        if (u.startsWith("д") || u.equals("d")) {
            return 86_400_000L;
        }
        if (u.startsWith("ч") || u.equals("h")) {
            return 3_600_000L;
        }
        if (u.startsWith("м") || u.equals("m")) {
            return 60_000L;
        }
        return 1000L;
    }

    /** The words before the duration without trailing punctuation and «через»/«ещё»/«осталось». */
    private static String label(String before) {
        String s = before.strip().replaceAll("[\\s:—\\-–·|]+$", "");
        s = s.replaceAll("(?iu)\\s*(через|ещё|еще|осталось|in)$", "").strip();
        return s.replaceAll("[\\s:—\\-–·|]+$", "");
    }
}
