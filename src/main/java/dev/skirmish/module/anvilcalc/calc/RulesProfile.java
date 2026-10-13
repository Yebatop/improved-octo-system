package dev.skirmish.module.anvilcalc.calc;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Server-specific anvil rules layered over the vanilla port in {@link AnvilRules}: the profile only rewrites the
 * enchantment definitions the anvil reads (maximum level, exclusive sets, whether the item accepts it), so the vanilla
 * {@code createResult} logic and the solver stay the same. {@link #VANILLA} rewrites nothing, which keeps vanilla
 * results bit-identical. Pure Java, covered by tests.
 *
 * @param maxLevels            anvil maximum per enchantment id without namespace, raising the vanilla maximum
 * @param combineIncompatible  exclusive enchantments (Sharpness + Smite, Protection + Blast Protection…) may coexist
 * @param notOnArmor           enchantment ids an armour piece never accepts
 * @param armorNotRepairable   armour cannot be repaired in the anvil (a warning; the planner never repairs)
 * @param dubiousBooks         «сомнительные» books exist and follow their own rules
 */
public record RulesProfile(Kind kind, Map<String, Integer> maxLevels, boolean combineIncompatible, Set<String> notOnArmor,
                           boolean armorNotRepairable, boolean dubiousBooks) {
    public enum Kind {
        VANILLA,
        HOLYWORLD_LITE,
        HOLYWORLD_PRIME
    }

    public static final RulesProfile VANILLA = new RulesProfile(Kind.VANILLA, Map.of(), false, Set.of(), false, false);

    /**
     * HolyWorld Lite Anarchy (wiki "Зачарования → Ванильные зачарования", t.me/hwlite/496): anvil maximum Efficiency 10,
     * Sharpness 7, Protection 5, Smite 7, Bane of Arthropods 7, Unbreaking 5, Looting 5, Fortune 5, Mending 2;
     * incompatible enchantments can be combined; armour cannot take Mending and cannot be repaired in the anvil.
     */
    public static final RulesProfile HOLYWORLD_LITE = new RulesProfile(Kind.HOLYWORLD_LITE,
            Map.of("efficiency", 10, "sharpness", 7, "protection", 5, "smite", 7, "bane_of_arthropods", 7,
                    "unbreaking", 5, "looting", 5, "fortune", 5, "mending", 2),
            true, Set.of("mending"), true, false);

    /**
     * HolyWorld Prime Anarchy (native 1.21.11): vanilla anvil plus «сомнительные» books (Looting, Efficiency, Fortune,
     * Sweeping Edge up to X), which merge only with an identical book at a random ±1 and cannot be raised once applied
     * (wiki "Сомнительные зачарования"). Those books are left out of plans.
     */
    public static final RulesProfile HOLYWORLD_PRIME = new RulesProfile(Kind.HOLYWORLD_PRIME, Map.of(), false, Set.of(), false, true);

    public RulesProfile {
        maxLevels = Map.copyOf(maxLevels);
        notOnArmor = Set.copyOf(notOnArmor);
    }

    public static RulesProfile of(Kind kind) {
        return switch (kind) {
            case VANILLA -> VANILLA;
            case HOLYWORLD_LITE -> HOLYWORLD_LITE;
            case HOLYWORLD_PRIME -> HOLYWORLD_PRIME;
        };
    }

    /** {@code minecraft:sharpness} → {@code sharpness}. */
    static String path(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 && id.startsWith("minecraft") ? id.substring(colon + 1) : id;
    }

    /** Whether {@link #adjust} changes anything at all. */
    public boolean changesDefinitions() {
        return !maxLevels.isEmpty() || combineIncompatible || !notOnArmor.isEmpty();
    }

    /**
     * The catalog as this server's anvil sees it. Returns {@code catalog} itself when the profile changes nothing.
     *
     * @param baseArmor the item in the left slot is worn armour (including elytra)
     */
    public Map<String, EnchantInfo> adjust(Map<String, EnchantInfo> catalog, boolean baseArmor) {
        if (!changesDefinitions()) {
            return catalog;
        }
        Map<String, EnchantInfo> out = new HashMap<>();
        for (Map.Entry<String, EnchantInfo> entry : catalog.entrySet()) {
            EnchantInfo info = entry.getValue();
            String path = path(info.id());
            int max = Math.max(info.maxLevel(), maxLevels.getOrDefault(path, 0));
            boolean supported = info.supported() && !(baseArmor && notOnArmor.contains(path));
            Set<String> incompatible = combineIncompatible ? Set.of() : info.incompatible();
            out.put(entry.getKey(), new EnchantInfo(info.id(), info.anvilCost(), max, supported, incompatible));
        }
        return out;
    }
}
