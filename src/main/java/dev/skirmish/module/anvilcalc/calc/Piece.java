package dev.skirmish.module.anvilcalc.calc;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * An anvil input: the base item, an enchanted book or an intermediate result.
 *
 * @param book       has {@code STORED_ENCHANTMENTS} (an enchanted book)
 * @param count      stack size; the anvil charges 40 per applied enchantment when the left stack has more than one
 * @param repairCost {@code REPAIR_COST}, the prior work penalty 2^n − 1
 * @param enchants   enchantment id to level, in component order
 */
public record Piece(boolean book, int count, int repairCost, Map<String, Integer> enchants) {
    public Piece {
        enchants = Collections.unmodifiableMap(new LinkedHashMap<>(enchants));
    }

    public static Piece book(int repairCost, Map<String, Integer> enchants) {
        return new Piece(true, 1, repairCost, enchants);
    }

    public static Piece item(int repairCost, Map<String, Integer> enchants) {
        return new Piece(false, 1, repairCost, enchants);
    }

    public int level(String id) {
        return enchants.getOrDefault(id, 0);
    }

    public int levelSum() {
        int sum = 0;
        for (int level : enchants.values()) {
            sum += level;
        }
        return sum;
    }

    /** {@code sharpness 5, looting 3} with the namespace dropped for vanilla ids. */
    public String describe() {
        if (enchants.isEmpty()) {
            return "-";
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : enchants.entrySet()) {
            if (!sb.isEmpty()) {
                sb.append(", ");
            }
            String id = entry.getKey();
            sb.append(id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id).append(' ').append(entry.getValue());
        }
        return sb.toString();
    }
}
