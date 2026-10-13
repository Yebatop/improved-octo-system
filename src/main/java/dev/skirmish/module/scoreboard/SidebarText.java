package dev.skirmish.module.scoreboard;

import java.util.Locale;

/**
 * How a sidebar line is read for the two-column look: where «Label: value» splits, which icon a label gets, and
 * whether the line uses resource-pack glyphs (then it keeps the game font so they still show). Pure Java.
 */
final class SidebarText {
    private SidebarText() {
    }

    /**
     * Index of the colon of a «Label: value» line, or −1: the label has a letter and at most 24 characters, the value
     * is not blank (so «12:30» or «https://» are not split).
     */
    static int split(String plain) {
        int colon = plain.indexOf(':');
        if (colon <= 0 || colon > 24 || colon + 1 >= plain.length()) {
            return -1;
        }
        String label = plain.substring(0, colon);
        String value = plain.substring(colon + 1);
        if (value.isBlank() || value.startsWith("//")) {
            return -1;
        }
        for (int i = 0; i < label.length(); i++) {
            if (Character.isLetter(label.charAt(i))) {
                return colon;
            }
        }
        return -1;
    }

    /** Private-use characters: glyphs a server's resource pack draws (icons, custom lettering). */
    static boolean customGlyphs(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\uE000' && c <= '\uF8FF') {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the mod's font has every character: Latin, Cyrillic, common punctuation and currency signs. Anything
     * else (⌚, ❤, emoji, resource-pack glyphs) keeps the game font, which draws them.
     */
    static boolean modFont(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean ok = c < 0x250 || c >= 0x400 && c <= 0x4FF || c >= 0x2000 && c <= 0x206F || c >= 0x20A0 && c <= 0x20CF
                    || c == 0x2116;
            if (!ok) {
                return false;
            }
        }
        return true;
    }

    /** The icon for a label: coin, shield, signal, sword, skull, user, star, people, globe, clock or dot. */
    static String icon(String label) {
        String t = label.toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (has(t, "баланс", "монет", "деньг", "коин", "золот", "balance", "coin", "money")) {
            return "coin";
        }
        if (has(t, "клан", "clan", "гильд")) {
            return "shield";
        }
        if (has(t, "пинг", "ping")) {
            return "signal";
        }
        if (has(t, "убийств", "килл", "kill")) {
            return "sword";
        }
        if (has(t, "смерт", "death")) {
            return "skull";
        }
        if (has(t, "ник", "игрок", "name", "player")) {
            return "user";
        }
        if (has(t, "групп", "донат", "привилег", "ранг", "статус", "rank", "group")) {
            return "star";
        }
        if (has(t, "онлайн", "online")) {
            return "people";
        }
        if (has(t, "сервер", "анарх", "режим", "лайт", "server", "mode")) {
            return "globe";
        }
        if (has(t, "время", "pvp", "пвп", "обновлен", "time") || t.equals("кт") || t.startsWith("кт ")) {
            return "clock";
        }
        return "dot";
    }

    private static boolean has(String text, String... words) {
        for (String w : words) {
            if (text.contains(w)) {
                return true;
            }
        }
        return false;
    }
}
