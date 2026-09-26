package dev.skirmish.module.events;

import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds block coordinates in a chat line and the event it talks about. Pure Java. Accepted forms:
 * {@code x: 123 y: 64 z: -50}, {@code X 123, Z -50} (no height), and three bare integers {@code 123 64 -50}
 * (also in brackets or with commas). Bare triples are filtered hard, because Russian prices use spaces as
 * thousands separators ("1 250 000"): no leading zeros, height within the world, not followed by a currency word.
 */
public final class ChatCoords {
    /** A match; {@code y} is null when the message gave only X and Z. {@code end} is the char after the match. */
    public record Coords(int x, @Nullable Integer y, int z, int start, int end) {
        public String text() {
            return y == null ? x + " ~ " + z : x + " " + y + " " + z;
        }
    }

    public static final int MIN_Y = -64;
    public static final int MAX_Y = 320;
    private static final int MAX_XZ = 30_000_000;
    private static final int MAX_PER_LINE = 3;

    private static final String N = "(-?\\d{1,8})";
    private static final String SEP = "\\s*[:=]?\\s*";
    private static final String GAP = "[\\s,;/|]*";
    /** x/y/z labels, also Cyrillic look-alikes (х, у). */
    private static final Pattern XYZ = Pattern.compile(
            "(?<![\\p{L}\\d])[xхXХ]" + SEP + N + GAP + "[yуYУ]" + SEP + N + GAP + "[zZ]" + SEP + N + "(?!\\d)");
    private static final Pattern XZ = Pattern.compile(
            "(?<![\\p{L}\\d])[xхXХ]" + SEP + N + GAP + "[zZ]" + SEP + N + "(?!\\d)");
    private static final Pattern TRIPLE = Pattern.compile(
            "(?<![\\p{L}\\d.,\\-+/])(-?\\d{1,8})\\s*[,;]?\\s+(-?\\d{1,3})\\s*[,;]?\\s+(-?\\d{1,8})(?![\\d:%]|[.,]\\d)");
    private static final Pattern CURRENCY_AFTER = Pattern.compile(
            "^\\s*(?:[¤$₽€]|k\\b|kk\\b|к\\b|кк\\b|м\\b|монет|коин|койн|руб|р\\.|сапфир|гем|жетон|шт|лвл|ур|%|hp|хп|мин|сек|ч\\b)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    /** Event names known without the API (Lite, Prime and Alpha; from the wiki). The API adds live display names. */
    public static final List<String> KNOWN_EVENTS = List.of(
            "Таинственный груз", "Таинственный корабль", "Цветочная поляна", "Опытный Тыпо", "Контейнер", "Посылка",
            "Смертельная шахта", "Золотая лихорадка", "Игральный куб", "Кубик", "Трофейная охота", "Захват Энда", "Бункер",
            "Ядро Солнца", "Древний город", "Ящик Пандоры", "Золотой БоБ", "Испытательное Сумасшествие",
            "Теневой купец", "Кровавая луна", "АирДроп", "Аирдроп", "Мистический босс", "Босс");

    private ChatCoords() {
    }

    /** Up to three coordinate matches in reading order. */
    public static List<Coords> find(String text) {
        List<Coords> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        collect(XYZ.matcher(text), text, out, true, true);
        collect(XZ.matcher(text), text, out, false, true);
        collect(TRIPLE.matcher(text), text, out, true, false);
        out.sort(java.util.Comparator.comparingInt(Coords::start));
        return out.size() > MAX_PER_LINE ? List.copyOf(out.subList(0, MAX_PER_LINE)) : out;
    }

    private static void collect(Matcher m, String text, List<Coords> out, boolean hasY, boolean labeled) {
        while (m.find()) {
            if (overlaps(out, m.start(), m.end())) {
                continue;
            }
            String gx = m.group(1);
            String gy = hasY ? m.group(2) : null;
            String gz = hasY ? m.group(3) : m.group(2);
            if (!labeled && (leadingZero(gx) || leadingZero(gy) || leadingZero(gz))) {
                continue;
            }
            long x = Long.parseLong(gx);
            long z = Long.parseLong(gz);
            Long y = gy == null ? null : Long.parseLong(gy);
            if (Math.abs(x) > MAX_XZ || Math.abs(z) > MAX_XZ) {
                continue;
            }
            if (y != null && (y < MIN_Y || y > MAX_Y)) {
                continue;
            }
            if (!labeled) {
                if (Math.abs(x) < 10 && Math.abs(z) < 10 && Math.abs(y) < 10) {
                    continue;
                }
                if (CURRENCY_AFTER.matcher(text.substring(m.end())).find()) {
                    continue;
                }
            }
            out.add(new Coords((int) x, y == null ? null : (int) (long) y, (int) z, m.start(), m.end()));
        }
    }

    private static boolean leadingZero(@Nullable String n) {
        if (n == null) {
            return false;
        }
        String digits = n.startsWith("-") ? n.substring(1) : n;
        return digits.length() > 1 && digits.charAt(0) == '0';
    }

    private static boolean overlaps(List<Coords> found, int start, int end) {
        for (Coords c : found) {
            if (start < c.end() && end > c.start()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The event the line names: the longest of {@code liveNames} (API display names) or {@link #KNOWN_EVENTS}
     * found in it, case-insensitively; null when none.
     */
    public static @Nullable String eventName(String text, Collection<String> liveNames) {
        String t = fold(text);
        String best = null;
        for (Collection<String> names : List.of(liveNames, KNOWN_EVENTS)) {
            for (String name : names) {
                if (name == null || name.isBlank()) {
                    continue;
                }
                if (containsWord(t, fold(name)) && (best == null || name.length() > best.length())) {
                    best = name;
                }
            }
        }
        return best;
    }

    private static boolean containsWord(String text, String word) {
        int at = text.indexOf(word);
        while (at >= 0) {
            boolean startOk = at == 0 || !Character.isLetter(text.charAt(at - 1));
            if (startOk) {
                return true;
            }
            at = text.indexOf(word, at + 1);
        }
        return false;
    }

    /** Dimension named in the line: "minecraft:the_nether", "minecraft:the_end", or null. */
    public static @Nullable String dimensionHint(String text) {
        String t = fold(text);
        if (t.contains("в аду") || t.contains("незер") || t.contains("nether")) {
            return "minecraft:the_nether";
        }
        if (t.contains("в энде") || t.contains("эндер мир") || t.contains("захват энда")
                || t.contains("the end") || t.contains("the_end")) {
            return "minecraft:the_end";
        }
        return null;
    }

    private static final Pattern VOTE_IN = Pattern.compile(
            "через\\s+(?:(\\d{1,2})\\s*ч\\p{L}*\\.?[,\\s]*)?(?:(\\d{1,3})\\s*мин\\p{L}*\\.?[,\\s]*)?(?:(\\d{1,2})\\s*сек)?");

    /**
     * HolyWorld's countdown to the next event vote: «▶ Ближайшее голосование за мероприятие будет проводиться через
     * 39 мин., 21 сек.» (captured 2026-09). Null for any other line.
     */
    public static @Nullable Duration voteCountdown(String text) {
        String t = fold(text);
        if (!t.contains("голосовани")) {
            return null;
        }
        Matcher m = VOTE_IN.matcher(t);
        while (m.find()) {
            if (m.group(1) == null && m.group(2) == null && m.group(3) == null) {
                continue;
            }
            long seconds = (m.group(1) == null ? 0 : Long.parseLong(m.group(1)) * 3600)
                    + (m.group(2) == null ? 0 : Long.parseLong(m.group(2)) * 60)
                    + (m.group(3) == null ? 0 : Long.parseLong(m.group(3)));
            return Duration.ofSeconds(seconds);
        }
        return null;
    }

    /** Heuristic: a server line starting a Lite event vote ("Началось голосование за ивент! /vote"). */
    public static boolean isVoteStart(String text) {
        String t = fold(text);
        return t.contains("голосовани") && (t.contains("/vote") || t.contains("начал") || t.contains("запущ")
                || t.contains("стартовал") || t.contains("открыт"));
    }

    private static String fold(String text) {
        return ServerParser.normalize(text).toLowerCase(Locale.ROOT);
    }
}
