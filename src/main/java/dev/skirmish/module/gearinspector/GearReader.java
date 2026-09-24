package dev.skirmish.module.gearinspector;

import dev.skirmish.module.gearinspector.holy.HolyGear;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Reads the six equipment stacks of a player strictly from the data components the client received. */
final class GearReader {
    /** @param holy HolyWorld knowledge, {@link HolyGear#NONE} while the HolyWorld profile is off */
    record SlotView(EquipmentSlot slot, ItemStack stack, Durability durability, EnchantmentStatus enchantStatus,
                    List<Component> enchantments, HolyGear holy) {
    }

    private GearReader() {
    }

    static SlotView read(EquipmentSlot slot, ItemStack stack, boolean assumeUndamaged, @Nullable Level level, boolean holy) {
        Durability durability = Durability.classify(damageFacts(stack), assumeUndamaged);
        ItemEnchantments enchantments = enchantments(stack);
        EnchantmentStatus status = enchantStatus(stack, enchantments);
        List<Component> names = new ArrayList<>();
        if (status == EnchantmentStatus.LISTED) {
            // Same order and names as the vanilla tooltip (enchantment tooltip_order tag of the synced registry).
            enchantments.addToTooltip(Item.TooltipContext.of(level), names::add, TooltipFlag.NORMAL, stack);
        }
        return new SlotView(slot, stack, durability, status, names,
                holy ? HolyGear.read(slot, stack, enchantments) : HolyGear.NONE);
    }

    static DamageFacts damageFacts(ItemStack stack) {
        if (stack.isEmpty()) {
            return DamageFacts.EMPTY;
        }
        Integer damage = stack.get(DataComponents.DAMAGE);
        return new DamageFacts(false,
                stack.has(DataComponents.UNBREAKABLE),
                stack.getPrototype().has(DataComponents.MAX_DAMAGE),
                stack.get(DataComponents.MAX_DAMAGE),
                damage,
                damage != null && stack.hasNonDefault(DataComponents.DAMAGE));
    }

    /** Enchantments of the stack, or the stored enchantments of a book when the stack itself has none. */
    static ItemEnchantments enchantments(ItemStack stack) {
        ItemEnchantments own = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (own.isEmpty()) {
            ItemEnchantments stored = stack.get(DataComponents.STORED_ENCHANTMENTS);
            if (stored != null && !stored.isEmpty()) {
                return stored;
            }
        }
        return own;
    }

    static EnchantmentStatus enchantStatus(ItemStack stack, ItemEnchantments enchantments) {
        if (stack.isEmpty()) {
            return EnchantmentStatus.NOT_APPLICABLE;
        }
        boolean enchantable = stack.has(DataComponents.ENCHANTABLE)
                || stack.getPrototype().has(DataComponents.ENCHANTABLE)
                || stack.getPrototype().has(DataComponents.STORED_ENCHANTMENTS);
        boolean glint = Boolean.TRUE.equals(stack.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE));
        return EnchantmentStatus.classify(false, enchantments.size(), enchantable, glint);
    }

    /**
     * One line for debug.log: item id, durability decision and enchantment ids with levels. With the HolyWorld profile
     * active it also logs the custom name, the lore and what was recognised, so real item samples can be collected.
     */
    static String describe(EquipmentSlot slot, ItemStack stack, boolean assumeUndamaged, boolean holy) {
        if (stack.isEmpty()) {
            return Durability.classify(DamageFacts.EMPTY, assumeUndamaged).describe();
        }
        StringBuilder out = new StringBuilder(stack.getItemHolder().getRegisteredName());
        if (stack.getCount() > 1) {
            out.append(" x").append(stack.getCount());
        }
        out.append(": durability ").append(Durability.classify(damageFacts(stack), assumeUndamaged).describe());
        ItemEnchantments enchantments = enchantments(stack);
        EnchantmentStatus status = enchantStatus(stack, enchantments);
        out.append("; ").append(status.describe());
        if (status == EnchantmentStatus.LISTED) {
            List<String> ids = new ArrayList<>();
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                ids.add(entry.getKey().getRegisteredName() + " " + entry.getIntValue());
            }
            ids.sort(null);
            out.append(' ').append(ids);
        }
        if (holy) {
            out.append("; holyworld ").append(HolyGear.describe(slot, stack, enchantments));
        }
        return out.toString();
    }

    static String slotName(EquipmentSlot slot) {
        return slot.getName().toLowerCase(Locale.ROOT);
    }
}
