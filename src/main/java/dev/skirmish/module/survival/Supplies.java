package dev.skirmish.module.survival;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Reads the local player's own inventory (36 slots + off hand). Never moves or uses anything. */
public final class Supplies {
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private Supplies() {
    }

    public static int count(Player player, Item item) {
        int n = 0;
        Inventory inventory = player.getInventory();
        for (ItemStack stack : inventory.getNonEquipmentItems()) {
            if (stack.is(item)) {
                n += stack.getCount();
            }
        }
        ItemStack offhand = player.getOffhandItem();
        if (offhand.is(item)) {
            n += offhand.getCount();
        }
        return n;
    }

    /** Lowest remaining durability fraction among worn armor with durability; 1 when none is worn. */
    public static double worstArmor(Player player) {
        double worst = 1.0;
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty() && stack.isDamageableItem() && stack.getMaxDamage() > 0) {
                double left = Math.max(0, stack.getMaxDamage() - stack.getDamageValue()) / (double) stack.getMaxDamage();
                worst = Math.min(worst, left);
            }
        }
        return worst;
    }
}
