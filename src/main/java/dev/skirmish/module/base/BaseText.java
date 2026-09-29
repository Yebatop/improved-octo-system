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

    /** {@code chest} → normal, {@code trapped_chest} → trapped, {@code exposed_copper_chest} → copper_exposed … */
    static String chestTexture(String block) {
        String path = block.startsWith("waxed_") ? block.substring(6) : block;
        if (path.endsWith("copper_chest")) {
            String age = path.substring(0, path.length() - "copper_chest".length());
            return age.isEmpty() ? "copper" : "copper_" + age.substring(0, age.length() - 1);
        }
        if (path.equals("chest")) {
            return "normal";
        }
        return path.endsWith("_chest") ? path.substring(0, path.length() - 6) : path;
    }

    /** Blocks the base model never shows as themselves (no X-ray): ores, raw ore blocks of veins, debris, spawners. */
    static boolean buried(String path) {
        return path.endsWith("_ore") || path.equals("ancient_debris") || path.equals("spawner") || path.equals("trial_spawner")
                || path.equals("budding_amethyst") || path.startsWith("raw_") && path.endsWith("_block");
    }
}
