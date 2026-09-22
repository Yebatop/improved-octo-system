package dev.skirmish.combat;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Copies of the six visible equipment slots as the client last received them. Empty stacks mean "nothing seen". */
public final class EquipmentSnapshot {
    /** Display order: head, chest, legs, feet, main hand, off hand. */
    public static final List<EquipmentSlot> SLOTS = List.of(
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET,
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND);

    private final Map<EquipmentSlot, ItemStack> items;
    private final long timeMs;

    private EquipmentSnapshot(Map<EquipmentSlot, ItemStack> items, long timeMs) {
        this.items = items;
        this.timeMs = timeMs;
    }

    public static EquipmentSnapshot capture(LivingEntity entity, long timeMs) {
        Map<EquipmentSlot, ItemStack> items = new EnumMap<>(EquipmentSlot.class);
        for (EquipmentSlot slot : SLOTS) {
            items.put(slot, entity.getItemBySlot(slot).copy());
        }
        return new EquipmentSnapshot(items, timeMs);
    }

    /** True if every slot still holds an equal stack (item, count and components). */
    public boolean matches(LivingEntity entity) {
        for (EquipmentSlot slot : SLOTS) {
            if (!ItemStack.matches(items.get(slot), entity.getItemBySlot(slot))) {
                return false;
            }
        }
        return true;
    }

    public ItemStack get(EquipmentSlot slot) {
        return items.getOrDefault(slot, ItemStack.EMPTY);
    }

    public boolean isEmpty() {
        for (ItemStack stack : items.values()) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    public long timeMs() {
        return timeMs;
    }
}
