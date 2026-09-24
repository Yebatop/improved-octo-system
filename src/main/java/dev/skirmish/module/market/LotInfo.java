package dev.skirmish.module.market;

import dev.skirmish.module.market.parse.HwText;
import dev.skirmish.module.market.parse.LotParser;
import dev.skirmish.module.market.parse.PriceHistory;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * One auction lot as read from its stack: price per unit, how it compares with the history, whether it is the
 * cheapest of its item on the page. Read only; built on the client thread.
 */
record LotInfo(String itemKey, String name, LotParser.Lot lot, int count, double unitPrice, double median,
               PriceHistory.Verdict verdict, boolean cheapest) {

    /** Median unknown. */
    static final double NO_MEDIAN = -1;

    LotInfo withCheapest(boolean value) {
        return new LotInfo(itemKey, name, lot, count, unitPrice, median, verdict, value);
    }

    boolean isBid() {
        return lot.kind() == LotParser.Kind.BID;
    }

    /** The lot on {@code stack}, or null when its lore has no price. */
    static LotParser.@Nullable Lot read(ItemStack stack) {
        ItemLore lore = stack.get(DataComponents.LORE);
        if (lore == null || lore.lines().isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>(lore.lines().size());
        for (Component line : lore.lines()) {
            lines.add(line.getString());
        }
        return LotParser.parse(lines);
    }

    /**
     * History key: the registry id, plus the cleaned custom name for renamed/custom items (HolyWorld custom items
     * are vanilla items with a name), so «Сфера Армоталити» and a plain player head are kept apart.
     */
    static String itemKey(ItemStack stack) {
        String id = stack.getItemHolder().getRegisteredName();
        Component custom = stack.get(DataComponents.CUSTOM_NAME);
        if (custom == null) {
            return id;
        }
        // A trailing "(5345)" is a per-stack amount («Бутылек с 50 ур. опыта (5345)»), not part of the item's identity.
        String name = HwText.normalize(custom.getString()).replaceAll("\\s*\\(\\d+\\)\\s*$", "");
        return name.isEmpty() ? id : id + "|" + name;
    }

    /** Displayed name in keyword form, to match purchase chat lines against lots seen on the page. */
    static String nameKey(ItemStack stack) {
        return HwText.normalize(stack.getHoverName().getString());
    }
}
