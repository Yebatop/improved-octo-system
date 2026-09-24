package dev.skirmish.module.events;

import java.util.Locale;

/**
 * Event rarity tier. HolyWorld's {@code metadata.rare} is free-form: tiers ({@code EPIC}, {@code legendary}),
 * variants ({@code rare_plains}, {@code normal_desert}) and ship classes ({@code ship_roskoshni_f}), so the tier is
 * read from the words it contains.
 */
public enum Rarity {
    UNKNOWN, COMMON, RARE, EPIC, LEGENDARY;

    /** Tier of a raw {@code rare} value; {@link #UNKNOWN} when nothing matches. */
    public static Rarity parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String r = raw.toLowerCase(Locale.ROOT).replace('ё', 'е');
        if (r.contains("legend") || r.contains("легенд") || r.contains("mythic") || r.contains("миф")
                || r.contains("sokrush") || r.contains("сокруш")) {
            return LEGENDARY;
        }
        if (r.contains("epic") || r.contains("эпич") || r.contains("roskosh") || r.contains("роскош")
                || r.contains("opasn") || r.contains("опасн")) {
            return EPIC;
        }
        if (r.contains("rare") || r.contains("редк") || r.contains("zazhit") || r.contains("зажиточ")
                || r.contains("zlov") || r.contains("злов")) {
            return RARE;
        }
        if (r.contains("normal") || r.contains("common") || r.contains("default") || r.contains("обыч")
                || r.contains("usual") || r.contains("gibel") || r.contains("гибел")) {
            return COMMON;
        }
        return UNKNOWN;
    }

    /** Lang/theme key: {@code common}, {@code rare}, ... */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}
