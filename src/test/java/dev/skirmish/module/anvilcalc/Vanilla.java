package dev.skirmish.module.anvilcalc;

import dev.skirmish.module.anvilcalc.calc.EnchantInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vanilla 1.21.11 enchantment data copied from net.minecraft.world.item.enchantment.Enchantments (anvil cost,
 * max level) and VanillaEnchantmentTagsProvider (exclusive sets).
 */
final class Vanilla {
    record Def(String id, int anvilCost, int maxLevel) {
    }

    static final List<Def> DEFS = List.of(
            new Def("protection", 1, 4), new Def("fire_protection", 2, 4), new Def("blast_protection", 4, 4),
            new Def("projectile_protection", 2, 4), new Def("thorns", 8, 3),
            new Def("sharpness", 1, 5), new Def("smite", 2, 5), new Def("bane_of_arthropods", 2, 5),
            new Def("knockback", 2, 2), new Def("fire_aspect", 4, 2), new Def("looting", 4, 3),
            new Def("sweeping_edge", 4, 3), new Def("efficiency", 1, 5), new Def("silk_touch", 8, 1),
            new Def("fortune", 4, 3), new Def("unbreaking", 2, 3), new Def("mending", 4, 1),
            new Def("infinity", 8, 1), new Def("power", 1, 5), new Def("vanishing_curse", 8, 1));

    static final List<List<String>> EXCLUSIVE = List.of(
            List.of("protection", "fire_protection", "blast_protection", "projectile_protection"),
            List.of("sharpness", "smite", "bane_of_arthropods"),
            List.of("fortune", "silk_touch"),
            List.of("infinity", "mending"));

    static final Set<String> SWORD = Set.of("sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect",
            "looting", "sweeping_edge", "unbreaking", "mending", "vanishing_curse");
    static final Set<String> PICKAXE = Set.of("efficiency", "silk_touch", "fortune", "unbreaking", "mending", "vanishing_curse");

    private Vanilla() {
    }

    static Map<String, EnchantInfo> catalog(Set<String> supported) {
        Map<String, EnchantInfo> catalog = new HashMap<>();
        for (Def def : DEFS) {
            Set<String> incompatible = new HashSet<>();
            for (List<String> group : EXCLUSIVE) {
                if (group.contains(def.id())) {
                    for (String other : group) {
                        if (!other.equals(def.id())) {
                            incompatible.add(other);
                        }
                    }
                }
            }
            catalog.put(def.id(), new EnchantInfo(def.id(), def.anvilCost(), def.maxLevel(), supported.contains(def.id()), incompatible));
        }
        return catalog;
    }

    /** {@code ench("sharpness", 5, "looting", 3)}. */
    static Map<String, Integer> ench(Object... pairs) {
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        return map;
    }
}
