package dev.skirmish.module.effects;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

/**
 * One tile per worn armor piece (and optionally main/off hand): item icon, a thin durability bar and the durability
 * as percent or uses left. Default place: left of the hotbar and off-hand slot, below the chat lines.
 */
final class ArmorHud extends HudBlock {
    private static final String L = "layout.qol.";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final EquipmentSlot[] HANDS = {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND};

    private final ArmorHudModule module;
    private List<ItemStack> current = List.of();
    private List<ItemStack> last = List.of();

    ArmorHud(ArmorHudModule module) {
        super("armor", "skirmish.hud.element.armor", new Placement(0.5f, 1f, 1f, 1f, -252, -8));
        this.module = module;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled();
    }

    @Override
    public boolean shown() {
        update(false);
        return !current.isEmpty();
    }

    @Override
    public boolean hasContent() {
        return !last.isEmpty();
    }

    @Override
    public void update(boolean preview) {
        LocalPlayer player = Minecraft.getInstance().player;
        current = player == null ? List.of() : read(player);
        if (!current.isEmpty()) {
            last = current;
        }
    }

    private List<ItemStack> read(LocalPlayer player) {
        List<ItemStack> items = new ArrayList<>(6);
        for (EquipmentSlot slot : ARMOR) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        if (module.showHands.get()) {
            for (EquipmentSlot slot : HANDS) {
                ItemStack stack = player.getItemBySlot(slot);
                if (!stack.isEmpty()) {
                    items.add(stack);
                }
            }
        }
        return items;
    }

    private List<ItemStack> items(boolean preview) {
        if (preview && current.isEmpty() || last.isEmpty()) {
            return List.of(damaged(Items.NETHERITE_HELMET, 0.92), damaged(Items.NETHERITE_CHESTPLATE, 0.61),
                    damaged(Items.DIAMOND_LEGGINGS, 0.24), damaged(Items.DIAMOND_BOOTS, 0.09),
                    damaged(Items.NETHERITE_SWORD, 0.78));
        }
        return last;
    }

    private static ItemStack damaged(net.minecraft.world.item.Item item, double remaining) {
        ItemStack stack = new ItemStack(item);
        stack.setDamageValue((int) Math.round(stack.getMaxDamage() * (1 - remaining)));
        return stack;
    }

    private String label(ItemStack stack) {
        if (!stack.isDamageableItem()) {
            return stack.getCount() > 1 ? Integer.toString(stack.getCount()) : "";
        }
        if (module.mode.get() == ArmorHudModule.Mode.REMAINING) {
            return Integer.toString(Math.max(0, stack.getMaxDamage() - stack.getDamageValue()));
        }
        return EffectText.percent(stack.getMaxDamage(), stack.getDamageValue()) + "%";
    }

    private String wearColor(ItemStack stack) {
        double remaining = EffectText.remaining(stack.getMaxDamage(), stack.getDamageValue());
        return switch (EffectText.wear(remaining, module.warnPercent.get() / 100.0)) {
            case GOOD -> "good";
            case WORN -> "warn";
            case CRITICAL -> "bad";
        };
    }

    private float tileWidth(Ui ui, boolean preview) {
        float w = ui.num(L + "armor_tile");
        for (ItemStack stack : items(preview)) {
            w = Math.max(w, ui.textWidth("qol_durability", label(stack)));
        }
        return w;
    }

    @Override
    public float width(Ui ui, boolean preview) {
        int n = items(preview).size();
        return HudStyle.insetX(ui) * 2 + n * tileWidth(ui, preview) + Math.max(0, n - 1) * ui.num(L + "armor_gap");
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return HudStyle.insetY(ui) * 2 + ui.num(L + "armor_icon") + ui.num(L + "armor_bar_gap") + ui.num(L + "armor_bar")
                + ui.num(L + "armor_label_gap") + ui.lineHeight("qol_durability");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        HudStyle.panel(ui, x, y, width(ui, preview), height(ui, preview));
        float tile = tileWidth(ui, preview);
        float icon = ui.num(L + "armor_icon");
        float bar = ui.num(L + "armor_bar");
        float barW = ui.num(L + "armor_bar_width");
        float tx = x + HudStyle.insetX(ui);
        float ty = y + HudStyle.insetY(ui);
        for (ItemStack stack : items(preview)) {
            float cx = tx + tile / 2f;
            var pose = ui.graphics().pose();
            pose.pushMatrix();
            pose.translate(Math.round(cx - icon / 2f), Math.round(ty));
            pose.scale(icon / 16f, icon / 16f);
            ui.graphics().renderItem(stack, 0, 0);
            pose.popMatrix();

            float by = ty + icon + ui.num(L + "armor_bar_gap");
            if (stack.isDamageableItem()) {
                float fill = (float) EffectText.remaining(stack.getMaxDamage(), stack.getDamageValue());
                ui.rect(cx - barW / 2f, by, barW, bar, bar / 2f, ui.color("track"));
                ui.rect(cx - barW / 2f, by, barW * fill, bar, bar / 2f, ui.color(wearColor(stack)));
            }
            String label = label(stack);
            float ly = by + bar + ui.num(L + "armor_label_gap");
            String wear = stack.isDamageableItem() ? wearColor(stack) : "text_2";
            int color = ui.color(wear.equals("good") ? "text" : wear);
            ui.text("qol_durability", label, cx - ui.textWidth("qol_durability", label) / 2f, ly, color);
            tx += tile + ui.num(L + "armor_gap");
        }
    }
}
