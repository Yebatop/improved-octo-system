package dev.skirmish.module.events;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which HolyWorld sub-server the client is on from text it already received (sidebar title and lines,
 * tab-list header/footer, join chat lines) or from the user's override. Pure Java.
 *
 * <p>The API keys Lite data by {@code LITE_ANARCHY_<n>} / {@code LITE_NEW_ANARCHY_<n>} with display names such as
 * "ДуоЛайт #17" or "Лайт (1.20) #2"; Prime uses bare "1".."5". Exact display names from {@code /v1/servers} win;
 * the regexes below are educated guesses (no in-game capture yet): "Лайт #17", "Анархия 17", "Lite-Anarchy-17",
 * "lanarchy17", "Прайм #2", "pr2", and the sidebar's "#12 -◆-" line.
 */
public final class ServerParser {
    public enum Mode {
        UNKNOWN, LITE, PRIME, ALPHA, HUB
    }

    /** A detected sub-server. {@code apiId} is the key used by api.holyworld.me ("LITE_ANARCHY_17", "2" for Prime). */
    public record ServerRef(Mode mode, int number, String apiId, String source) {
        public static ServerRef lite(int n, String source) {
            return new ServerRef(Mode.LITE, n, "LITE_ANARCHY_" + n, source);
        }

        public static ServerRef liteNew(int n, String source) {
            return new ServerRef(Mode.LITE, n, "LITE_NEW_ANARCHY_" + n, source);
        }

        public static ServerRef prime(int n, String source) {
            return new ServerRef(Mode.PRIME, n, Integer.toString(n), source);
        }

        public boolean isPrime() {
            return mode == Mode.PRIME;
        }
    }

    private static final String NUM = "(\\d{1,2})(?!\\d)";
    private static final Pattern LITE_NEW = Pattern.compile(
            "(?:лайт|lite)\\s*\\(?\\s*1[.,]20\\s*\\)?\\s*[#-]?\\s*" + NUM
                    + "|(?<![a-zа-я0-9])1-20l-?" + NUM
                    + "|(?<![a-zа-я])l2anarchy-?" + NUM);
    private static final Pattern PRIME = Pattern.compile(
            "(?:прайм|prime)(?:[\\s-]*(?:анархия|anarchy))?\\s*[#-]?\\s*" + NUM
                    + "|(?<![a-zа-я])pr-?" + NUM);
    private static final Pattern LITE = Pattern.compile(
            "(?:соло|дуо|трио|клан)?лайт\\s*[#-]?\\s*" + NUM
                    + "|(?<![a-zа-я])lite[-_ ]?(?:anarchy[-_ ]?)?" + NUM
                    + "|(?<![a-zа-я])lanarchy-?" + NUM
                    + "|(?:лайт[-\\s]?)?анархи[яи]\\s*[#-]?\\s*" + NUM);
    /** Sidebar line "… #12 -◆-" and any other "#N" (only trusted on the sidebar and tab list). */
    private static final Pattern BARE = Pattern.compile("#\\s*" + NUM);

    private ServerParser() {
    }

    /** Lower case, no § codes, small caps and ё folded, "№" as "#", single spaces. */
    public static String normalize(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '§' && i + 1 < text.length()) {
                i++;
                continue;
            }
            out.append(foldSmallCaps(c));
        }
        return out.toString().toLowerCase(Locale.ROOT).replace('ё', 'е').replace('№', '#')
                .replaceAll("\\s+", " ").trim();
    }

    private static char foldSmallCaps(char c) {
        return switch (c) {
            case 'ᴀ' -> 'a'; case 'ʙ' -> 'b'; case 'ᴄ' -> 'c'; case 'ᴅ' -> 'd'; case 'ᴇ' -> 'e'; case 'ꜰ' -> 'f';
            case 'ɢ' -> 'g'; case 'ʜ' -> 'h'; case 'ɪ' -> 'i'; case 'ᴊ' -> 'j'; case 'ᴋ' -> 'k'; case 'ʟ' -> 'l';
            case 'ᴍ' -> 'm'; case 'ɴ' -> 'n'; case 'ᴏ' -> 'o'; case 'ᴘ' -> 'p'; case 'ʀ' -> 'r'; case 'ꜱ' -> 's';
            case 'ᴛ' -> 't'; case 'ᴜ' -> 'u'; case 'ᴠ' -> 'v'; case 'ᴡ' -> 'w'; case 'ʏ' -> 'y'; case 'ᴢ' -> 'z';
            default -> c;
        };
    }

    /** Mode named in the text, if any ("прайм", "лайт", "альфа", "хаб"). */
    public static Mode mode(String text) {
        String t = normalize(text);
        if (t.contains("прайм") || t.contains("prime")) {
            return Mode.PRIME;
        }
        if (t.contains("лайт") || t.contains("lite")) {
            return Mode.LITE;
        }
        if (t.contains("альфа") || t.contains("alpha")) {
            return Mode.ALPHA;
        }
        if (t.contains("хаб") || t.contains("лобби") || t.contains("hub") || t.contains("lobby")) {
            return Mode.HUB;
        }
        return Mode.UNKNOWN;
    }

    /**
     * Sub-server named in one piece of text. {@code allowBare}: also accept a lone "#12" (sidebar and tab list,
     * not chat, where "#" is common); its mode comes from {@code modeHint} or the text itself, Lite by default.
     */
    public static @Nullable ServerRef parse(String text, Map<String, String> servers, boolean allowBare, Mode modeHint, String source) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String t = normalize(text);
        ServerRef byName = byDisplayName(t, servers, source);
        if (byName != null) {
            return byName;
        }
        Matcher m = LITE_NEW.matcher(t);
        if (m.find()) {
            return ServerRef.liteNew(group(m), source);
        }
        m = PRIME.matcher(t);
        if (m.find()) {
            return ServerRef.prime(group(m), source);
        }
        m = LITE.matcher(t);
        if (m.find()) {
            return ServerRef.lite(group(m), source);
        }
        if (allowBare) {
            m = BARE.matcher(t);
            if (m.find()) {
                Mode mode = mode(t);
                if (mode == Mode.UNKNOWN) {
                    mode = modeHint;
                }
                int n = group(m);
                if (mode == Mode.PRIME) {
                    return ServerRef.prime(n, source);
                }
                if (mode == Mode.LITE || mode == Mode.UNKNOWN) {
                    return ServerRef.lite(n, source);
                }
            }
        }
        return null;
    }

    /** Longest display name from {@code /v1/servers} contained in the text (not followed by another digit). */
    private static @Nullable ServerRef byDisplayName(String normalized, Map<String, String> servers, String source) {
        List<Map.Entry<String, String>> entries = new ArrayList<>(servers.entrySet());
        entries.sort(Comparator.comparingInt((Map.Entry<String, String> e) -> e.getValue().length()).reversed());
        for (Map.Entry<String, String> e : entries) {
            String name = normalize(e.getValue());
            int at = normalized.indexOf(name);
            while (at >= 0) {
                int end = at + name.length();
                if (end >= normalized.length() || !Character.isDigit(normalized.charAt(end))) {
                    return fromApiId(e.getKey(), source);
                }
                at = normalized.indexOf(name, at + 1);
            }
        }
        return null;
    }

    /** "LITE_ANARCHY_17", "LITE_NEW_ANARCHY_2" → a ref; anything else → null. */
    public static @Nullable ServerRef fromApiId(String id, String source) {
        Matcher m = Pattern.compile("LITE_(NEW_)?ANARCHY_(\\d{1,3})").matcher(id.trim().toUpperCase(Locale.ROOT));
        if (!m.matches()) {
            return null;
        }
        int n = Integer.parseInt(m.group(2));
        return m.group(1) != null ? ServerRef.liteNew(n, source) : ServerRef.lite(n, source);
    }

    /**
     * The module's "server" setting: empty = auto (null). Accepts an API id ({@code LITE_ANARCHY_17}), a Lite
     * number ({@code 17}), {@code p2} / {@code прайм 2} for Prime, {@code new 2} for Лайт (1.20), or any text the
     * parser understands ("ДуоЛайт #17").
     */
    public static @Nullable ServerRef parseOverride(String value, Map<String, String> servers) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        ServerRef api = fromApiId(v, "setting");
        if (api != null) {
            return api;
        }
        String t = normalize(v);
        Matcher m = Pattern.compile("#?\\s*(\\d{1,2})").matcher(t);
        if (m.matches()) {
            return ServerRef.lite(Integer.parseInt(m.group(1)), "setting");
        }
        m = Pattern.compile("(?:p|pr|prime|п|пр|прайм)\\s*#?\\s*(\\d{1,2})").matcher(t);
        if (m.matches()) {
            return ServerRef.prime(Integer.parseInt(m.group(1)), "setting");
        }
        m = Pattern.compile("(?:n|new|1[.,]20)\\s*#?\\s*(\\d{1,2})").matcher(t);
        if (m.matches()) {
            return ServerRef.liteNew(Integer.parseInt(m.group(1)), "setting");
        }
        return parse(v, servers, true, Mode.UNKNOWN, "setting");
    }

    private static int group(Matcher m) {
        for (int i = 1; i <= m.groupCount(); i++) {
            if (m.group(i) != null) {
                return Integer.parseInt(m.group(i));
            }
        }
        throw new IllegalStateException("no number group");
    }
}
