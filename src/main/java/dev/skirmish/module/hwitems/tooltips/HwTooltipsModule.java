package dev.skirmish.module.hwitems.tooltips;

import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.hwitems.HwItemInfo;
import dev.skirmish.module.hwitems.HwItemTable;
import dev.skirmish.module.hwitems.HwStack;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * «HolyWorld Tooltips»: wiki facts under the tooltip of HolyWorld items — what a TNT type breaks, the stun's cube and
 * time, sphere rarities and conversion costs, talisman and rune effects, mystery egg odds, backpack slots, armour
 * repair rules. The table is {@value HwItemTable#RESOURCE} (built from the wiki), the texts are in the lang files.
 * Lines are appended through {@link ItemTooltipCallback} after the other modules' (the market adds auction prices the
 * same way; both only append). Read only.
 */
public final class HwTooltipsModule extends Module {
    public static final String ID = "hw_tooltips";

    /** When the facts are shown. */
    public enum When {
        ALWAYS, SHIFT
    }

    final EnumSetting<When> when = add(new EnumSetting<>("when", When.SHIFT));
    final BoolSetting shiftHint = add(new BoolSetting("shift_hint", true));
    final BoolSetting armorFacts = add(new BoolSetting("armor_facts", true));
    final BoolSetting holyworldOnly = add(new BoolSetting("holyworld_only", true));

    public HwTooltipsModule() {
        super(ID, true);
        shiftHint.under(when).visibleWhen(() -> when.get() == When.SHIFT);
    }

    @Override
    public Category category() {
        return Category.UTILITY;
    }

    @Override
    public void onInitialize() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (isEnabled()) {
                try {
                    addLines(stack, lines);
                } catch (RuntimeException e) {
                    error("tooltip failed", e);
                }
            }
        });
    }

    private void addLines(ItemStack stack, List<Component> lines) {
        if (holyworldOnly.get() && !HolyWorld.isConnected()) {
            return;
        }
        HwItemInfo info = HwStack.info(stack);
        if (info.isEmpty()) {
            return;
        }
        HwItemTable table = HwItemTable.get();
        List<String> keys = TooltipFacts.keys(info, table, armorFacts.get());
        if (keys.isEmpty()) {
            return;
        }
        Theme theme = Theme.get();
        boolean shown = when.get() == When.ALWAYS || Minecraft.getInstance().hasShiftDown();
        if (!shown) {
            if (shiftHint.get()) {
                lines.add(colored(Ui.tr("skirmish.hw_items.tooltip.shift"), theme.color("hwt_hint")));
            }
            return;
        }
        lines.add(colored(Ui.tr("skirmish.hw_items.tooltip.header"), theme.color("hwt_header")));
        for (String text : TooltipFacts.texts(info, keys, Ui::tr)) {
            lines.add(colored(Ui.tr("skirmish.hw_items.tooltip.bullet") + text, theme.color("hwt_line")));
        }
    }

    private static Component colored(String text, int argb) {
        return Component.literal(text).withStyle(Style.EMPTY.withColor(argb & 0xFFFFFF));
    }
}
