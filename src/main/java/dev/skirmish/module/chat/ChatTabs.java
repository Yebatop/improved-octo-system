package dev.skirmish.module.chat;

import dev.skirmish.module.events.ChatCoords;

import java.util.Locale;

/**
 * Chat tabs and which one a line belongs to, by HolyWorld's formats (captured 2026-09): players write as
 * «ɢ | «Ранг» Ник: текст» (global) or «ʟ | …» (local), the server as «▶ …», event cards as «▍ …» lines between
 * «▬▬▬» rules, ads as «[Объявление] …». Pure Java.
 */
public final class ChatTabs {
    /** A tab; {@link #ALL} shows everything. */
    public enum Tab {
        ALL, PLAYERS, TRADE, EVENTS, SYSTEM;

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private static final String[] TRADE_WORDS = {"прода", "куплю", "покупа", "скупа", "скуплю", "обмен", "меняю",
            "ставк", "аукцион", "/ah", "гарант", "/grant", "/garant", "дешев", "кк ", "кк,", "₽", "¤", "wts", "wtb"};
    private static final String[] EVENT_WORDS = {"ивент", "мероприяти", "голосовани", "/vote", "появится",
            "взорв", "до взрыва", "на рандомных координатах"};

    private ChatTabs() {
    }

    /** The tab a line belongs to (never {@link Tab#ALL}). */
    public static Tab classify(String text) {
        String t = fold(text);
        if (t.startsWith("▍") || t.startsWith("▬") || t.startsWith("⚡")) {
            return Tab.EVENTS;
        }
        if (isPlayerLine(t)) {
            return contains(t, TRADE_WORDS) ? Tab.TRADE : Tab.PLAYERS;
        }
        if (t.startsWith("[объявление]")) {
            return Tab.TRADE;
        }
        if (t.startsWith("▶") && (contains(t, EVENT_WORDS) || ChatCoords.eventName(text, java.util.List.of()) != null)) {
            return Tab.EVENTS;
        }
        return Tab.SYSTEM;
    }

    /** «ɢ | …» / «ʟ | …» (small caps, folded to g / l) and the like: a player's chat line. */
    static boolean isPlayerLine(String folded) {
        return folded.length() > 3 && (folded.charAt(0) == 'g' || folded.charAt(0) == 'l' || folded.charAt(0) == 'ɢ'
                || folded.charAt(0) == 'ʟ') && folded.charAt(1) == ' ' && folded.charAt(2) == '|';
    }

    /** The part after the author (after the first «: » of a player line), or null for other lines. */
    public static String body(String text) {
        String t = fold(text);
        if (!isPlayerLine(t)) {
            return null;
        }
        String s = text.strip().replaceFirst("^\\d\\d:\\d\\d ", "");
        int colon = s.indexOf(':');
        return colon < 0 ? null : s.substring(colon + 1);
    }

    private static boolean contains(String t, String[] words) {
        for (String w : words) {
            if (t.contains(w)) {
                return true;
            }
        }
        return false;
    }

    /** Lower case, small caps folded, without a leading «12:34 » time. */
    static String fold(String text) {
        StringBuilder out = new StringBuilder(text.length());
        String s = text.strip().replaceFirst("^\\d\\d:\\d\\d ", "");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            out.append(switch (c) {
                case 'ɢ' -> 'g';
                case 'ʟ' -> 'l';
                default -> c;
            });
        }
        return out.toString().toLowerCase(Locale.ROOT).replace('ё', 'е');
    }
}
