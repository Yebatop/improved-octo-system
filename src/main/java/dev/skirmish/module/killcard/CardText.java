package dev.skirmish.module.killcard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Localized labels printed on the card, resolved on the client thread from
 * {@code skirmish.module.killcard.card.<name>}; {@link #english()} is the built-in fallback.
 *
 * @param remaining   format with one {@code %s} (my remaining HP)
 * @param totemsLabel already in the plural form for the card's totem count
 * @param decimal     decimal separator of the language
 */
public record CardText(String badge, String previewBadge, String vs, String remaining, String killed, String damageLabel,
                       String damageUnknown, String totemsLabel, String accuracyLabel, String durationLabel,
                       String gearLabel, String datePattern, char decimal) {
    public static final String PREFIX = "skirmish.module.killcard.card.";

    /** Translation key suffix → English text. */
    public static final Map<String, String> DEFAULTS = defaults();

    private static Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("badge", "VICTORY");
        map.put("preview_badge", "PREVIEW");
        map.put("vs", "vs");
        map.put("remaining", "%s HP left");
        map.put("killed", "killed");
        map.put("damage", "damage dealt");
        map.put("damage_unknown", "n/a");
        map.put("totems.one", "totem popped");
        map.put("totems.many", "totems popped");
        map.put("accuracy", "hit rate");
        map.put("duration", "duration");
        map.put("gear", "Opponent's gear");
        map.put("date_pattern", "dd.MM.yyyy");
        map.put("decimal", ".");
        return Collections.unmodifiableMap(map);
    }

    /** Builds the labels from {@code lookup(fullKey, englishFallback)}; {@code totems} picks the plural form. */
    public static CardText load(BiFunction<String, String, String> lookup, int totems) {
        BiFunction<String, String, String> t = (name, fallback) -> {
            String value = lookup.apply(PREFIX + name, fallback);
            return value == null || value.isBlank() ? fallback : value;
        };
        String form = pluralForm(totems);
        String many = t.apply("totems.many", DEFAULTS.get("totems.many"));
        String totemsLabel = t.apply("totems." + form, form.equals("one") ? DEFAULTS.get("totems.one") : many);
        String decimal = t.apply("decimal", ".");
        return new CardText(t.apply("badge", DEFAULTS.get("badge")), t.apply("preview_badge", DEFAULTS.get("preview_badge")),
                t.apply("vs", DEFAULTS.get("vs")), t.apply("remaining", DEFAULTS.get("remaining")),
                t.apply("killed", DEFAULTS.get("killed")), t.apply("damage", DEFAULTS.get("damage")),
                t.apply("damage_unknown", DEFAULTS.get("damage_unknown")), totemsLabel,
                t.apply("accuracy", DEFAULTS.get("accuracy")), t.apply("duration", DEFAULTS.get("duration")),
                t.apply("gear", DEFAULTS.get("gear")), t.apply("date_pattern", DEFAULTS.get("date_pattern")),
                decimal.isEmpty() ? '.' : decimal.charAt(0));
    }

    public static CardText english(int totems) {
        return load((key, fallback) -> fallback, totems);
    }

    /** Russian plural rule (one / few / many); languages without "few" fall back to "many". */
    static String pluralForm(long n) {
        long mod10 = n % 10;
        long mod100 = n % 100;
        if (mod10 == 1 && mod100 != 11) {
            return "one";
        }
        if (mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14)) {
            return "few";
        }
        return "many";
    }
}
