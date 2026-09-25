package dev.skirmish.module.survival;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.StringSetting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * «Счётчик предметов»: counts of totems, golden and enchanted golden apples, pearls, experience bottles and a custom
 * list of items in my own inventory (including the off hand). Items at 0 are dimmed or hidden. Read-only.
 */
public final class ItemCounterModule extends Module {
    public static final String ID = "item_counter";

    enum Layout {
        ROW, COLUMN
    }

    final BoolSetting totems = add(new BoolSetting("totems", true));
    final BoolSetting gapples = add(new BoolSetting("gapples", true));
    final BoolSetting enchantedGapples = add(new BoolSetting("enchanted_gapples", true));
    final BoolSetting pearls = add(new BoolSetting("pearls", true));
    final BoolSetting xpBottles = add(new BoolSetting("xp_bottles", true));
    final StringSetting custom = add(new StringSetting("custom", "", 256, false));
    final BoolSetting hideZero = add(new BoolSetting("hide_zero", false));
    final EnumSetting<Layout> layout = add(new EnumSetting<>("layout", Layout.ROW));

    private String parsedFrom = null;
    private List<Item> customItems = List.of();

    public ItemCounterModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        Hud.get().register(new ItemCounterHud(this));
    }

    /** Items to count, in display order. */
    List<Item> items() {
        List<Item> out = new ArrayList<>();
        if (totems.get()) {
            out.add(Items.TOTEM_OF_UNDYING);
        }
        if (gapples.get()) {
            out.add(Items.GOLDEN_APPLE);
        }
        if (enchantedGapples.get()) {
            out.add(Items.ENCHANTED_GOLDEN_APPLE);
        }
        if (pearls.get()) {
            out.add(Items.ENDER_PEARL);
        }
        if (xpBottles.get()) {
            out.add(Items.EXPERIENCE_BOTTLE);
        }
        for (Item item : customItems()) {
            if (!out.contains(item)) {
                out.add(item);
            }
        }
        return out;
    }

    private List<Item> customItems() {
        String text = custom.get();
        if (!text.equals(parsedFrom)) {
            parsedFrom = text;
            List<Item> items = new ArrayList<>();
            for (String id : ItemIds.parse(text)) {
                Identifier key = Identifier.tryParse(id);
                if (key == null) {
                    continue;
                }
                BuiltInRegistries.ITEM.getOptional(key).filter(item -> item != Items.AIR).ifPresentOrElse(items::add,
                        () -> log("unknown item id in the custom list: " + id));
            }
            customItems = List.copyOf(items);
        }
        return customItems;
    }
}
