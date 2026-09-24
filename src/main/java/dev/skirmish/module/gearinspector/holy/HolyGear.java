package dev.skirmish.module.gearinspector.holy;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * HolyWorld knowledge about one equipment stack, read only from data components the client already has: the custom
 * name ({@code custom_name}, or a server-set {@code item_name}), the lore lines and the enchantments. Nothing is sent.
 * <p>
 * <b>Clan glow is not detected.</b> {@code /clan glow} makes the server show clanmates to each other in dyed leather
 * armour (green by default, colour chosen per clan). The client receives ordinary leather armour with a
 * {@code dyed_color}; no public source documents a marker (custom data, name, lore, glint) that sets it apart from
 * real dyed leather, and the colour is configurable, so any rule would be a guess. It also only affects players in
 * the viewer's own clan. To add it, capture the equipment components of a clanmate with glow on (see the report).
 *
 * @param tier        donor tier of worn armour or a held weapon/tool, null otherwise
 * @param custom      custom enchantments found in the lore
 * @param overCap     tooltip names ({@code Острота VII}) of vanilla enchantments above their vanilla maximum level
 * @param talisman    sphere or talisman in the off hand, null otherwise
 * @param unbreaking  Unbreaking level on an armour piece, -1 for other slots
 */
public record HolyGear(@Nullable DonorTier tier, List<CustomEnchant.Found> custom, Set<String> overCap,
                       @Nullable Talisman talisman, int unbreaking) {
    public static final HolyGear NONE = new HolyGear(null, List.of(), Set.of(), null, -1);

    public static HolyGear read(EquipmentSlot slot, ItemStack stack, ItemEnchantments enchantments) {
        if (stack.isEmpty()) {
            return NONE;
        }
        String name = customName(stack);
        List<String> lore = lore(stack);
        boolean armour = slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR;
        DonorTier tier = name.isEmpty() || slot == EquipmentSlot.OFFHAND ? null : DonorTier.ofGearName(name);
        Talisman talisman = slot == EquipmentSlot.OFFHAND ? Talisman.parse(name, lore) : null;
        Set<String> overCap = new HashSet<>();
        int unbreaking = armour ? 0 : -1;
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            Holder<Enchantment> holder = entry.getKey();
            int level = entry.getIntValue();
            if (level > holder.value().getMaxLevel()) {
                overCap.add(Enchantment.getFullname(holder, level).getString());
            }
            if (armour && holder.is(Enchantments.UNBREAKING)) {
                unbreaking = level;
            }
        }
        return new HolyGear(tier, CustomEnchant.parseLore(lore), Set.copyOf(overCap), talisman, unbreaking);
    }

    /** The server-given name, or "" for an item that only has its vanilla name. */
    static String customName(ItemStack stack) {
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom != null) {
            return custom.getString();
        }
        Component itemName = stack.hasNonDefault(DataComponents.ITEM_NAME) ? stack.get(DataComponents.ITEM_NAME) : null;
        return itemName == null ? "" : itemName.getString();
    }

    static List<String> lore(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null) {
            return List.of();
        }
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) {
            lines.add(line.getString());
        }
        return lines;
    }

    /** debug.log text: raw name and lore as received, dyed colour, custom data keys, and what was recognised. */
    public static String describe(EquipmentSlot slot, ItemStack stack, ItemEnchantments enchantments) {
        if (stack.isEmpty()) {
            return "-";
        }
        HolyGear gear = read(slot, stack, enchantments);
        StringBuilder out = new StringBuilder();
        out.append("name \"").append(customName(stack)).append("\" lore ").append(lore(stack));
        var dyed = stack.get(DataComponents.DYED_COLOR);
        if (dyed != null) {
            out.append(String.format(java.util.Locale.ROOT, " dyed #%06X", dyed.rgb() & 0xFFFFFF));
        }
        var customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            out.append(" custom_data ").append(customData.copyTag().keySet());
        }
        out.append(" -> tier ").append(gear.tier() == null ? "-" : gear.tier().name())
                .append(", custom ").append(gear.custom().stream().map(CustomEnchant.Found::text).toList())
                .append(", over cap ").append(gear.overCap());
        if (gear.talisman() != null) {
            out.append(", ").append(gear.talisman());
        }
        return out.toString();
    }

    public boolean isEmpty() {
        return tier == null && custom.isEmpty() && overCap.isEmpty() && talisman == null;
    }
}
