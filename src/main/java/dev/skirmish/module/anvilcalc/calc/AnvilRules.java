package dev.skirmish.module.anvilcalc.calc;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One anvil operation, a line-by-line port of {@code AnvilMenu.createResult} (1.21.11) for the case the calculator
 * plans: an enchanted book in the right slot, no rename. Material/durability repair (item + same item) is not planned.
 * A {@link RulesProfile} adapts it to a server by rewriting the enchantment definitions, never the logic.
 */
public final class AnvilRules {
    public enum Failure {
        /** A non-book in the right slot next to a different item or a book: createResult line 151. */
        INVALID_ORDER,
        /** Every enchantment of the right input was rejected: lines 216-219. */
        NOTHING_APPLIES,
        /** Nothing charged, so no result: lines 236-239 (e.g. a book without enchantments). */
        NO_CHANGE
    }

    /** {@code failure == null} means the result slot is filled; {@code cost} is the level cost shown by the anvil. */
    public record Outcome(Failure failure, int cost, Piece result) {
        public boolean ok() {
            return failure == null;
        }
    }

    private final Map<String, EnchantInfo> catalog;
    private final boolean creative;

    /** @param creative {@code player.hasInfiniteMaterials()}: every enchantment counts as applicable (line 184) */
    public AnvilRules(Map<String, EnchantInfo> catalog, boolean creative) {
        this(catalog, creative, RulesProfile.VANILLA, false);
    }

    /**
     * @param profile   server rules, see {@link RulesProfile#adjust}
     * @param baseArmor the item in the left slot is armour
     */
    public AnvilRules(Map<String, EnchantInfo> catalog, boolean creative, RulesProfile profile, boolean baseArmor) {
        this.catalog = Map.copyOf(profile.adjust(catalog, baseArmor));
        this.creative = creative;
    }

    public boolean creative() {
        return creative;
    }

    public EnchantInfo info(String id) {
        EnchantInfo info = catalog.get(id);
        if (info == null) {
            throw new IllegalArgumentException("No definition for enchantment " + id);
        }
        return info;
    }

    public boolean applicable(Piece base, String id) {
        return creative || base.book() || info(id).supported();
    }

    /** Left and right slot contents; the result keeps the left input's kind and count (it is a copy of it, line 126). */
    public Outcome combine(Piece left, Piece right) {
        if (!right.book()) {
            // Line 151: without STORED_ENCHANTMENTS the right input must be the same damageable item (a durability
            // merge, which the planner never schedules).
            return new Outcome(Failure.INVALID_ORDER, 0, left);
        }
        long priorWork = (long) left.repairCost() + right.repairCost();
        Map<String, Integer> mutable = new LinkedHashMap<>(left.enchants());
        int charged = 0;
        boolean applied = false;
        boolean rejected = false;
        for (Map.Entry<String, Integer> entry : right.enchants().entrySet()) {
            String id = entry.getKey();
            EnchantInfo info = info(id);
            int current = mutable.getOrDefault(id, 0);
            int level = entry.getValue();
            level = current == level ? level + 1 : Math.max(level, current);
            boolean accept = creative || left.book() || info.supported();
            for (String other : mutable.keySet()) {
                if (!other.equals(id) && !(info.compatibleWith(other) && info(other).compatibleWith(id))) {
                    accept = false;
                    charged++;
                }
            }
            if (!accept) {
                rejected = true;
                continue;
            }
            applied = true;
            if (level > info.maxLevel()) {
                level = info.maxLevel();
            }
            mutable.put(id, level);
            int perLevel = info.anvilCost();
            if (right.book()) {
                perLevel = Math.max(1, perLevel / 2);
            }
            charged += perLevel * level;
            if (left.count() > 1) {
                charged = 40;
            }
        }
        if (rejected && !applied) {
            return new Outcome(Failure.NOTHING_APPLIES, 0, left);
        }
        if (charged <= 0) {
            return new Outcome(Failure.NO_CHANGE, 0, left);
        }
        int cost = (int) Math.min(priorWork + charged, Integer.MAX_VALUE);
        int repairCost = increasedRepairCost(Math.max(left.repairCost(), right.repairCost()));
        return new Outcome(null, cost, new Piece(left.book(), left.count(), repairCost, mutable));
    }

    /** {@code AnvilMenu.calculateIncreasedRepairCost}: 2n + 1, saturating. */
    public static int increasedRepairCost(int repairCost) {
        return (int) Math.min(repairCost * 2L + 1L, Integer.MAX_VALUE);
    }
}
