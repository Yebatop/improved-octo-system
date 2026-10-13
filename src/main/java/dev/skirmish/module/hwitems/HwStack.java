package dev.skirmish.module.hwitems;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Reads {@link HwItemInfo} from an item stack's data components (custom name or server-set item name, lore,
 * equippable slot). Results are cached per stack object and recomputed when its name or lore component object
 * changes, so slot badges drawn every frame do not re-parse text. Client/render thread only.
 */
public final class HwStack {
    private record Cached(Object name, Object lore, HwItemInfo info) {
    }

    private static final Map<ItemStack, Cached> CACHE = new WeakHashMap<>();

    private HwStack() {
    }

    public static HwItemInfo info(ItemStack stack) {
        if (stack.isEmpty()) {
            return HwItemInfo.NONE;
        }
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        Object nameRef = custom != null ? custom : stack.hasNonDefault(DataComponents.ITEM_NAME) ? stack.get(DataComponents.ITEM_NAME) : null;
        ItemLore lore = stack.get(DataComponents.LORE);
        Cached cached = CACHE.get(stack);
        if (cached != null && cached.name() == nameRef && cached.lore() == lore) {
            return cached.info();
        }
        HwItemInfo info = HwItemInfo.of(name(stack), lore(lore), armor(stack), HwItemTable.get());
        if (CACHE.size() > 4096) {
            CACHE.clear();
        }
        CACHE.put(stack, new Cached(nameRef, lore, info));
        return info;
    }

    /** The server-given name, or "" for an item that only has its vanilla name. */
    public static String name(ItemStack stack) {
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null) {
            return custom.getString();
        }
        Component itemName = stack.hasNonDefault(DataComponents.ITEM_NAME) ? stack.get(DataComponents.ITEM_NAME) : null;
        return itemName == null ? "" : itemName.getString();
    }

    private static List<String> lore(ItemLore lore) {
        if (lore == null || lore.lines().isEmpty()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) {
            lines.add(line.getString());
        }
        return lines;
    }

    private static boolean armor(ItemStack stack) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        return equippable != null && equippable.slot().getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
    }
}
