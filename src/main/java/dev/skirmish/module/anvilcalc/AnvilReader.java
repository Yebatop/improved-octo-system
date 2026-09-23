package dev.skirmish.module.anvilcalc;

import dev.skirmish.module.anvilcalc.calc.AnvilInput;
import dev.skirmish.module.anvilcalc.calc.EnchantInfo;
import dev.skirmish.module.anvilcalc.calc.Piece;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reads the anvil's left slot and the player's books into solver input. Read-only: never touches slots. */
final class AnvilReader {
    /** {@link BookRef#inventorySlot} of a book lying in the anvil's right slot. */
    static final int RIGHT_SLOT = -1;

    enum Problem {
        EMPTY,
        NOT_ENCHANTABLE
    }

    /** @param inventorySlot index in the player inventory (0-8 hotbar, 9-35 main) or {@link #RIGHT_SLOT} */
    record BookRef(int inventorySlot, ItemStack stack) {
    }

    record Snapshot(@Nullable Problem problem, ItemStack base, @Nullable AnvilInput input, List<BookRef> books,
                    Map<String, Holder<Enchantment>> holders, String signature) {
    }

    private AnvilReader() {
    }

    static Snapshot read(AnvilMenu menu, Player player, int tooExpensiveAt, int exactLimit) {
        String signature = signature(menu, player);
        ItemStack base = menu.getSlot(AnvilMenu.INPUT_SLOT).getItem().copy();
        if (base.isEmpty()) {
            return new Snapshot(Problem.EMPTY, base, null, List.of(), Map.of(), signature);
        }
        // AnvilMenu.createResult line 125: the left item must carry an enchantment component.
        if (!EnchantmentHelper.canStoreEnchantments(base)) {
            return new Snapshot(Problem.NOT_ENCHANTABLE, base, null, List.of(), Map.of(), signature);
        }
        Map<String, Holder<Enchantment>> holders = new LinkedHashMap<>();
        Piece basePiece = new Piece(base.is(Items.ENCHANTED_BOOK), base.getCount(), repairCost(base), enchants(base, holders));

        List<BookRef> books = new ArrayList<>();
        List<Piece> pieces = new ArrayList<>();
        ItemStack right = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
        if (isBook(right)) {
            books.add(new BookRef(RIGHT_SLOT, right.copy()));
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isBook(stack)) {
                books.add(new BookRef(slot, stack.copy()));
            }
        }
        for (BookRef book : books) {
            pieces.add(new Piece(true, book.stack().getCount(), repairCost(book.stack()), enchants(book.stack(), holders)));
        }
        Map<String, EnchantInfo> catalog = new HashMap<>();
        for (Map.Entry<String, Holder<Enchantment>> entry : holders.entrySet()) {
            Holder<Enchantment> holder = entry.getValue();
            Enchantment enchantment = holder.value();
            Set<String> incompatible = new HashSet<>();
            for (Map.Entry<String, Holder<Enchantment>> other : holders.entrySet()) {
                if (!other.getKey().equals(entry.getKey()) && !Enchantment.areCompatible(holder, other.getValue())) {
                    incompatible.add(other.getKey());
                }
            }
            catalog.put(entry.getKey(), new EnchantInfo(entry.getKey(), enchantment.getAnvilCost(), enchantment.getMaxLevel(),
                    enchantment.canEnchant(base), incompatible));
        }
        AnvilInput input = new AnvilInput(basePiece, pieces, catalog, player.hasInfiniteMaterials(), tooExpensiveAt, exactLimit);
        return new Snapshot(null, base, input, List.copyOf(books), holders, signature);
    }

    /** Changes whenever anything the calculation reads changes. */
    static String signature(AnvilMenu menu, Player player) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(player.hasInfiniteMaterials()).append('|');
        append(sb, menu.getSlot(AnvilMenu.INPUT_SLOT).getItem());
        ItemStack right = menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).getItem();
        if (isBook(right)) {
            sb.append("R");
            append(sb, right);
        }
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < Inventory.INVENTORY_SIZE; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (isBook(stack)) {
                sb.append(slot);
                append(sb, stack);
            }
        }
        return sb.toString();
    }

    private static void append(StringBuilder sb, ItemStack stack) {
        if (stack.isEmpty()) {
            sb.append("-;");
            return;
        }
        sb.append(stack.getItemHolder().getRegisteredName()).append('x').append(stack.getCount())
                .append('r').append(repairCost(stack));
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
            sb.append(',').append(entry.getKey().getRegisteredName()).append('=').append(entry.getIntValue());
        }
        sb.append(';');
    }

    /** What AnvilMenu treats as a book on the right (line 132: has STORED_ENCHANTMENTS) and reads via line 173. */
    static boolean isBook(ItemStack stack) {
        return !stack.isEmpty() && stack.is(Items.ENCHANTED_BOOK) && stack.has(DataComponents.STORED_ENCHANTMENTS);
    }

    private static int repairCost(ItemStack stack) {
        return stack.getOrDefault(DataComponents.REPAIR_COST, 0);
    }

    private static Map<String, Integer> enchants(ItemStack stack, Map<String, Holder<Enchantment>> holders) {
        ItemEnchantments enchantments = EnchantmentHelper.getEnchantmentsForCrafting(stack);
        Map<String, Integer> result = new LinkedHashMap<>();
        for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
            String id = entry.getKey().getRegisteredName();
            holders.putIfAbsent(id, entry.getKey());
            result.put(id, entry.getIntValue());
        }
        return result;
    }
}
