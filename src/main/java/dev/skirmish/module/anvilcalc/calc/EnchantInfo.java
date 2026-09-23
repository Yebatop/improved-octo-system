package dev.skirmish.module.anvilcalc.calc;

import java.util.Set;

/**
 * What the anvil needs to know about one enchantment, taken from its registry definition.
 *
 * @param supported    {@code Enchantment.canEnchant(base item)}: the definition's supported items contain the item
 *                     in the anvil's left slot
 * @param incompatible ids for which {@code Enchantment.areCompatible} is false (symmetric, never contains {@code id})
 */
public record EnchantInfo(String id, int anvilCost, int maxLevel, boolean supported, Set<String> incompatible) {
    public EnchantInfo {
        incompatible = Set.copyOf(incompatible);
    }

    public boolean compatibleWith(String other) {
        return !incompatible.contains(other);
    }
}
