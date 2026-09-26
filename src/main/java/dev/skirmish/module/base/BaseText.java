package dev.skirmish.module.base;

import org.jspecify.annotations.Nullable;

import java.util.regex.Pattern;

/** Small text helpers for Base OS. Pure Java. */
final class BaseText {
    private BaseText() {
    }

    /** Whether {@code text} names {@code nick} as a whole word (any case): «Регион игрока Steve», not «Steve_2». */
    static boolean mentions(String text, String nick) {
        if (nick.isBlank()) {
            return false;
        }
        return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(nick) + "(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE)
                .matcher(text).find();
    }

    /** Whether a title uses private-use glyphs (a server's custom menu drawn with a resource-pack font). */
    static boolean customGlyphs(String title) {
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            if (c >= '\uE000' && c <= '\uF8FF') {
                return true;
            }
        }
        return false;
    }

    /**
     * A long countdown in words: "2д 3ч", "5ч 12м"; null under an hour (then a clock reads better). {@code units}
     * are the day, hour and minute suffixes.
     */
    static @Nullable String span(long ms, String[] units) {
        long minutes = ms / 60_000;
        long days = minutes / 1440;
        long hours = minutes / 60 % 24;
        long mins = minutes % 60;
        if (days > 0) {
            return hours > 0 ? days + units[0] + " " + hours + units[1] : days + units[0];
        }
        if (minutes >= 60) {
            return mins > 0 ? hours + units[1] + " " + mins + units[2] : hours + units[1];
        }
        return null;
    }

    /** Container blocks whose contents are worth indexing. */
    static boolean container(String blockId) {
        String path = blockId.substring(blockId.indexOf(':') + 1);
        return path.endsWith("chest") || path.endsWith("shulker_box") || path.equals("barrel") || path.equals("hopper")
                || path.equals("dispenser") || path.equals("dropper");
    }
}
