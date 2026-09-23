package dev.skirmish.module.killcard;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Localized labels printed on the card. Resolved from the game language on the client thread
 * ({@code skirmish.module.killcard.card.<name>}); {@link #english()} is the built-in fallback.
 *
 * @param slotNames   captions of the six equipment slots in {@code EquipmentSnapshot.SLOTS} order
 * @param datePattern {@link java.time.format.DateTimeFormatter} pattern
 */
public record CardText(String title, String previewTitle, String killerCaption, String victimCaption, String gearTitle,
                       List<String> slotNames, String damageLabel, String hpUnit, String damageNote,
                       String damageUnknownValue, String damageUnknownNote, String totemsLabel, String totemsNote,
                       String durationLabel, String secondsUnit, String hitsLabel, String hitsNote, String serverLabel,
                       String datePattern, String footer) {
    public static final String PREFIX = "skirmish.module.killcard.card.";

    /** Translation key suffix → English text. Order matches the record components. */
    public static final Map<String, String> DEFAULTS = defaults();

    public CardText {
        slotNames = List.copyOf(slotNames);
        if (slotNames.size() != 6) {
            throw new IllegalArgumentException("Expected 6 slot names, got " + slotNames.size());
        }
    }

    private static Map<String, String> defaults() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("title", "KILL CONFIRMED");
        map.put("preview_title", "PREVIEW");
        map.put("killer", "killer");
        map.put("victim", "eliminated");
        map.put("gear", "OPPONENT'S GEAR");
        map.put("slot.head", "Helmet");
        map.put("slot.chest", "Chestplate");
        map.put("slot.legs", "Leggings");
        map.put("slot.feet", "Boots");
        map.put("slot.mainhand", "Main hand");
        map.put("slot.offhand", "Off hand");
        map.put("damage", "DAMAGE DEALT");
        map.put("hp", "HP");
        map.put("damage_note", "estimate from health updates");
        map.put("damage_unknown", "N/A");
        map.put("damage_unknown_note", "server hides health");
        map.put("totems", "TOTEMS POPPED");
        map.put("totems_note", "by the opponent");
        map.put("duration", "FIGHT DURATION");
        map.put("seconds", "s");
        map.put("hits", "HITS");
        map.put("hits_note", "dealt / taken");
        map.put("server", "Server");
        map.put("date_pattern", "yyyy-MM-dd HH:mm:ss");
        map.put("footer", "Skirmish · KillCard");
        return Collections.unmodifiableMap(map);
    }

    /** Builds the labels from {@code lookup(fullKey, englishFallback)}, e.g. {@code Language.getInstance()::getOrDefault}. */
    public static CardText load(BiFunction<String, String, String> lookup) {
        BiFunction<String, String, String> safe = (key, fallback) -> {
            String value = lookup.apply(key, fallback);
            return value == null || value.isBlank() ? fallback : value;
        };
        Function<String, String> t = name -> safe.apply(PREFIX + name, DEFAULTS.get(name));
        return new CardText(t.apply("title"), t.apply("preview_title"), t.apply("killer"), t.apply("victim"), t.apply("gear"),
                List.of(t.apply("slot.head"), t.apply("slot.chest"), t.apply("slot.legs"), t.apply("slot.feet"),
                        t.apply("slot.mainhand"), t.apply("slot.offhand")),
                t.apply("damage"), t.apply("hp"), t.apply("damage_note"), t.apply("damage_unknown"),
                t.apply("damage_unknown_note"), t.apply("totems"), t.apply("totems_note"), t.apply("duration"),
                t.apply("seconds"), t.apply("hits"), t.apply("hits_note"), t.apply("server"), t.apply("date_pattern"),
                t.apply("footer"));
    }

    public static CardText english() {
        return load((key, fallback) -> fallback);
    }
}
