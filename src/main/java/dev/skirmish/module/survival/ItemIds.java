package dev.skirmish.module.survival;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Parses the item counter's «свои предметы» field: item ids separated by commas or spaces. Pure (unit tested). */
public final class ItemIds {
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    /** More than a HUD row can show usefully. */
    public static final int MAX = 12;

    private ItemIds() {
    }

    /**
     * "ender_eye, minecraft:golden_carrot  Cobweb" → [minecraft:ender_eye, minecraft:golden_carrot, minecraft:cobweb].
     * Entries without a namespace get {@code minecraft:}; malformed entries and duplicates are dropped.
     */
    public static List<String> parse(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) {
            return out;
        }
        for (String raw : text.split("[,;\\s]+")) {
            String id = raw.trim().toLowerCase(Locale.ROOT);
            if (id.isEmpty()) {
                continue;
            }
            if (!id.contains(":")) {
                id = "minecraft:" + id;
            }
            if (ID.matcher(id).matches() && !out.contains(id) && out.size() < MAX) {
                out.add(id);
            }
        }
        return out;
    }
}
