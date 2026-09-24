package dev.skirmish.module.pvp;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalInt;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the combat tag (КТ, "режим PvP") in text the client already shows: the sidebar board and boss bar names.
 * HolyWorld's exact formats are not published, so the rules are deliberately tolerant:
 * <ul>
 *     <li>a line with a PvP keyword (pvp, пвп, кт, режим боя, в бою, до выхода, …) and a duration is the timer;</li>
 *     <li>a keyword line without a duration (or a "Противники:" line, or a PvP board title) opens a block whose
 *     following lines may hold the timer ("Осталось: 15с") and opponent nicks ("Nick - 12с");</li>
 *     <li>without a timer line, the longest opponent time is the tag;</li>
 *     <li>a boss bar whose name has a keyword is the tag; its progress fills the ring when the name has no time.</li>
 * </ul>
 * Lines that look related but match nothing are returned as {@link Result#unrecognized()} for the debug log.
 *
 * <p>HolyWorld (captured 2026-09): each tagged opponent has its own line {@code "▍ Nick  (27 ⌚) 16/20 ❤"} whose
 * seconds restart at 30 on every hit between the two players; the tag lasts while any of them runs. Those lines win
 * over everything else: the board's «…из игры 30 секунд.» line under «Не выходите» is a static hint, not a timer.
 */
final class TagParser {
    private static final String B = "(?<![\\p{L}\\p{N}])";
    private static final String E = "(?![\\p{L}\\p{N}])";
    /** Unambiguous PvP-mode words (normalized text). "kt" and "кт" cover Latin and Cyrillic spellings. */
    private static final Pattern STRONG = Pattern.compile(B + "(?:pvp|пвп|кт|kt|к\\.т\\.?|combat|режим\\p{L}* бо[яю]|в бою"
            + "|до выхода|нельзя выйти|не выходи\\p{L}*|выход через|combat ?tag)" + E);
    /** "бой" alone also names boss fights, so it only counts outside boss lines. */
    private static final Pattern WEAK = Pattern.compile(B + "(?:бой|боя|бою|fight|battle)" + E);
    private static final Pattern BOSS = Pattern.compile("босс|boss");
    private static final Pattern OPPONENTS_HEADER = Pattern.compile(B + "(?:противник\\p{L}*|враг\\p{L}*|соперник\\p{L}*|opponents?|enemies|targets?)" + E);
    /** Optional bullet, a Minecraft nick, then optionally a separator and a time. Matched on colour-stripped text. */
    private static final Pattern NICK_LINE = Pattern.compile(
            "^[^\\p{L}\\p{N}_]*([A-Za-z0-9_]{3,16})(?:\\s*[-–—:|•·»>(\\[]?\\s*(.*?)[)\\]]?)?\\s*$");
    /** HolyWorld opponent line, matched on colour-stripped text: nick, own seconds, optional health / max ❤. */
    private static final Pattern HW_OPPONENT = Pattern.compile("^[^\\p{L}\\p{N}_]*([A-Za-z0-9_]{3,16})\\s*\\(\\s*(\\d{1,3})\\s*⌚\\uFE0F?\\s*\\)"
            + "(?:\\s*(\\d{1,4}(?:[.,]\\d+)?)\\s*/\\s*(\\d{1,4}(?:[.,]\\d+)?)\\s*❤)?");
    /** A line of only symbols (e.g. "-◆-", "———") or empty: ends a block. */
    private static final Pattern SEPARATOR = Pattern.compile("^[^\\p{L}\\p{N}]*$");

    enum Source {
        BOARD, BOSS_BAR
    }

    /**
     * @param seconds   remaining seconds shown by the server, -1 when only the progress is known
     * @param progress  boss bar progress in [0, 1], NaN when the tag came from the board
     * @param opponents tagged opponents as listed on the board, in board order
     * @param line      the text the timer was read from (for the debug log)
     */
    record Reading(int seconds, float progress, List<String> opponents, Source source, String line, List<Opponent> details) {
        Reading(int seconds, float progress, List<String> opponents, Source source, String line) {
            this(seconds, progress, opponents, source, line, List.of());
        }
    }

    /**
     * One opponent line with its own timer; health is NaN when the line has none.
     */
    record Opponent(String name, int seconds, float health, float maxHealth) {
    }

    record Result(@Nullable Reading reading, List<String> unrecognized) {
    }

    /** One boss bar as shown: name text and progress. */
    record BossBar(String name, float progress) {
    }

    private TagParser() {
    }

    static boolean isKeyword(String normalized) {
        if (STRONG.matcher(normalized).find()) {
            return true;
        }
        return WEAK.matcher(normalized).find() && !BOSS.matcher(normalized).find();
    }

    /**
     * @param title    sidebar title (may be empty)
     * @param lines    sidebar lines top to bottom, as displayed
     * @param bossBars boss bars in display order
     * @param selfName the local player's name, never listed as an opponent
     */
    static Result parse(String title, List<String> lines, List<BossBar> bossBars, String selfName) {
        List<String> unrecognized = new ArrayList<>();
        Reading board = parseBoard(title, lines, selfName, unrecognized);
        Reading bar = parseBossBars(bossBars, unrecognized);
        Reading reading = board;
        if (board == null) {
            reading = bar;
        } else if (board.seconds() < 0 && bar != null) {
            reading = new Reading(bar.seconds(), bar.progress(), board.opponents(), bar.source(), bar.line(), board.details());
        }
        return new Result(reading, unrecognized);
    }

    private static @Nullable Reading parseBoard(String title, List<String> lines, String selfName, List<String> unrecognized) {
        Reading holy = parseHolyOpponents(lines, selfName);
        if (holy != null) {
            return holy;
        }
        int timer = -1;
        String timerLine = null;
        boolean pvpTitle = isKeyword(PvpText.normalize(title));
        boolean block = pvpTitle;
        boolean pvpBlockSeen = pvpTitle;
        int maxOpponentTime = -1;
        Set<String> opponents = new LinkedHashSet<>();
        for (String raw : lines) {
            String plain = PvpText.stripCodes(raw).strip();
            String norm = PvpText.normalize(raw);
            if (SEPARATOR.matcher(norm).matches()) {
                block = pvpTitle;
                continue;
            }
            if (isKeyword(norm)) {
                // With a time it is the timer; without one it is a header ("Режим PvP") for the lines below.
                OptionalInt seconds = PvpText.seconds(norm);
                if (seconds.isPresent() && timer < 0) {
                    timer = seconds.getAsInt();
                    timerLine = raw;
                } else if (PvpText.hasDigit(norm)) {
                    unrecognized.add(raw);
                }
                block = true;
                pvpBlockSeen = true;
                continue;
            }
            if (OPPONENTS_HEADER.matcher(norm).find() && !PvpText.hasDigit(norm)) {
                block = true;
                pvpBlockSeen = true;
                continue;
            }
            if (!block) {
                if (PvpText.explicitSeconds(norm).isPresent()) {
                    unrecognized.add(raw);
                }
                continue;
            }
            Matcher nick = NICK_LINE.matcher(plain);
            if (nick.matches() && !nick.group(1).equalsIgnoreCase(selfName) && isNickTail(nick.group(2))) {
                opponents.add(nick.group(1));
                String tail = nick.group(2) == null ? "" : PvpText.normalize(nick.group(2));
                OptionalInt time = tail.isEmpty() ? OptionalInt.empty() : PvpText.seconds(tail);
                if (time.isPresent()) {
                    maxOpponentTime = Math.max(maxOpponentTime, time.getAsInt());
                }
                continue;
            }
            OptionalInt seconds = PvpText.explicitSeconds(norm);
            if (seconds.isPresent() && timer < 0) {
                timer = seconds.getAsInt();
                timerLine = raw;
            } else if (PvpText.hasDigit(norm)) {
                unrecognized.add(raw);
            }
        }
        if (timer < 0 && maxOpponentTime >= 0) {
            timer = maxOpponentTime;
            timerLine = "max of opponent times";
        }
        if (timer < 0 && (opponents.isEmpty() || !pvpBlockSeen)) {
            return null;
        }
        return new Reading(timer, Float.NaN, List.copyOf(opponents), Source.BOARD, timerLine == null ? "opponents only" : timerLine);
    }

    /** HolyWorld opponent lines anywhere on the board; the tag is the longest of their timers. */
    private static @Nullable Reading parseHolyOpponents(List<String> lines, String selfName) {
        List<Opponent> found = new ArrayList<>();
        for (String raw : lines) {
            Matcher m = HW_OPPONENT.matcher(PvpText.stripCodes(raw).strip());
            if (!m.find() || m.group(1).equalsIgnoreCase(selfName)) {
                continue;
            }
            float health = m.group(3) == null ? Float.NaN : Float.parseFloat(m.group(3).replace(',', '.'));
            float max = m.group(4) == null ? Float.NaN : Float.parseFloat(m.group(4).replace(',', '.'));
            found.add(new Opponent(m.group(1), Integer.parseInt(m.group(2)), health, max));
        }
        if (found.isEmpty()) {
            return null;
        }
        int seconds = 0;
        List<String> names = new ArrayList<>();
        for (Opponent o : found) {
            seconds = Math.max(seconds, o.seconds());
            names.add(o.name());
        }
        return new Reading(seconds, Float.NaN, List.copyOf(names), Source.BOARD, "opponent timers", List.copyOf(found));
    }

    /** What may follow a nick on an opponent line: nothing or a time ("12с", "0:12", "12"). */
    private static boolean isNickTail(@Nullable String tail) {
        if (tail == null || tail.isBlank()) {
            return true;
        }
        String norm = PvpText.normalize(tail).replaceAll("[()\\[\\]]", "").trim();
        return PvpText.seconds(norm).isPresent() && norm.replaceAll("[\\d\\s:.,]", "").length() <= 7;
    }

    private static @Nullable Reading parseBossBars(List<BossBar> bars, List<String> unrecognized) {
        Reading found = null;
        for (BossBar bar : bars) {
            String norm = PvpText.normalize(bar.name());
            if (!isKeyword(norm)) {
                if (PvpText.explicitSeconds(norm).isPresent()) {
                    unrecognized.add("bossbar: " + bar.name());
                }
                continue;
            }
            if (found != null) {
                continue;
            }
            OptionalInt seconds = PvpText.seconds(norm);
            float progress = Math.max(0f, Math.min(1f, bar.progress()));
            found = new Reading(seconds.orElse(-1), progress, List.of(), Source.BOSS_BAR, bar.name());
            if (seconds.isEmpty()) {
                unrecognized.add("bossbar without time: " + bar.name() + " @" + String.format(Locale.ROOT, "%.2f", progress));
            }
        }
        return found;
    }
}
