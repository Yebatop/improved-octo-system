package dev.skirmish.module.gearinspector;

import dev.skirmish.combat.EquipmentSnapshot;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** The inspection panel, drawn through Fabric's HUD element registry. Reads the target's equipment every frame. */
final class GearHud implements HudElement {
    private static final int PAD = 4;
    private static final int ICON = 16;
    private static final int TEXT_X = PAD + ICON + 4;
    private static final int LINE = 10;
    private static final int ROW_GAP = 2;
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFAAAAAA;
    private static final int LOCKED = 0xFFFFAA00;
    private static final int SEPARATOR = 0x40FFFFFF;
    private static final int EMPTY_ICON = 0x30FFFFFF;
    private static final long ERROR_LOG_INTERVAL_MS = 10_000;

    private record Row(ItemStack stack, @Nullable FormattedCharSequence name, Component value, int valueColor,
                       List<FormattedCharSequence> extra) {
        int height() {
            return Math.max(ICON + 2, LINE * (1 + extra.size()));
        }
    }

    private final GearInspectorModule module;
    private long lastErrorMs;

    GearHud(GearInspectorModule module) {
        this.module = module;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker tickCounter) {
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        Player target = module.tracker().shown();
        if (target == null || target.isRemoved()) {
            return;
        }
        try {
            draw(mc, graphics, target);
        } catch (Throwable t) {
            long now = Util.getMillis();
            if (now - lastErrorMs > ERROR_LOG_INTERVAL_MS) {
                lastErrorMs = now;
                module.error("panel render failed", t);
            }
        }
    }

    private void draw(Minecraft mc, GuiGraphics graphics, Player target) {
        Font font = mc.font;
        int width = module.panelWidth.getInt();
        int textWidth = width - TEXT_X - PAD;
        boolean assume = module.assumeUndamaged.get();

        List<Row> rows = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSnapshot.SLOTS) {
            if (slot.getType() == EquipmentSlot.Type.HAND && !module.showHands.get()) {
                continue;
            }
            ItemStack stack = target.getItemBySlot(slot);
            if (stack.isEmpty() && !module.showEmptySlots.get()) {
                continue;
            }
            rows.add(row(font, GearReader.read(slot, stack, assume, mc.level), textWidth));
        }

        Component distance = Component.translatable("skirmish.gearinspector.distance",
                GearFormat.distance(mc.player.distanceTo(target)));
        Component lockTag = module.tracker().isLocked() ? Component.translatable("skirmish.gearinspector.locked") : null;
        int headerRight = font.width(distance) + (lockTag != null ? font.width(lockTag) + 4 : 0);
        FormattedCharSequence title = ellipsize(font, target.getDisplayName(), width - 2 * PAD - headerRight - 4);

        int height = PAD + LINE + 3;
        for (Row row : rows) {
            height += row.height() + ROW_GAP;
        }
        if (rows.isEmpty()) {
            height += LINE;
        }
        height += PAD - ROW_GAP;

        float scale = module.scale.getFloat();
        Anchor.Placement at = module.anchor.get().place(graphics.guiWidth(), graphics.guiHeight(),
                Math.round(width * scale), Math.round(height * scale), module.offsetX.getInt(), module.offsetY.getInt());

        graphics.pose().pushMatrix();
        graphics.pose().translate(at.x(), at.y());
        graphics.pose().scale(scale, scale);

        int background = GearFormat.background(module.backgroundOpacity.getInt());
        if (background != 0) {
            graphics.fill(0, 0, width, height, background);
        }

        int y = PAD;
        graphics.drawString(font, title, PAD, y, TEXT, true);
        int right = width - PAD;
        if (lockTag != null) {
            right -= font.width(lockTag);
            graphics.drawString(font, lockTag, right, y, LOCKED, true);
            right -= 4;
        }
        graphics.drawString(font, distance, right - font.width(distance), y, DIM, true);
        y += LINE;
        graphics.fill(PAD, y, width - PAD, y + 1, SEPARATOR);
        y += 3;

        if (rows.isEmpty()) {
            graphics.drawString(font, Component.translatable("skirmish.gearinspector.no_equipment"), PAD, y, GearFormat.NO_DATA, true);
        }
        for (Row row : rows) {
            drawRow(graphics, font, row, y, width);
            y += row.height() + ROW_GAP;
        }
        graphics.pose().popMatrix();
    }

    private Row row(Font font, GearReader.SlotView view, int textWidth) {
        Durability durability = view.durability();
        Component value = valueText(durability);
        ItemStack stack = view.stack();

        FormattedCharSequence name = null;
        if (module.showItemNames.get() || stack.isEmpty()) {
            Component label = stack.isEmpty()
                    ? Component.translatable("skirmish.gearinspector.slot." + GearReader.slotName(view.slot())).withStyle(ChatFormatting.DARK_GRAY)
                    : stack.getStyledHoverName();
            name = ellipsize(font, label, textWidth - font.width(value) - 4);
        }

        List<FormattedCharSequence> extra = new ArrayList<>();
        if (module.showEnchantments.get()) {
            switch (view.enchantStatus()) {
                case LISTED -> extra.addAll(font.split(join(view.enchantments()), textWidth));
                case NO_DATA -> {
                    if (module.showMissingEnchantments.get()) {
                        extra.addAll(font.split(Component.translatable("skirmish.gearinspector.enchantments_no_data")
                                .withColor(GearFormat.NO_DATA), textWidth));
                    }
                }
                case GLINT_ONLY -> {
                    if (module.showMissingEnchantments.get()) {
                        extra.addAll(font.split(Component.translatable("skirmish.gearinspector.enchantments_glint_only")
                                .withColor(GearFormat.NO_DATA), textWidth));
                    }
                }
                case NOT_APPLICABLE -> {
                }
            }
        }
        return new Row(stack, name, value, durability.color(), extra);
    }

    private Component valueText(Durability durability) {
        return switch (durability.kind()) {
            case PERCENT -> Component.literal(module.showAbsolute.get()
                    ? durability.percentText() + " " + durability.remaining() + "/" + durability.max()
                    : durability.percentText());
            case ASSUMED_FULL -> Component.literal(durability.percentText());
            case UNBREAKABLE -> Component.translatable("skirmish.gearinspector.unbreakable");
            case NOT_DAMAGEABLE -> Component.translatable("skirmish.gearinspector.not_damageable");
            case NO_DATA -> Component.translatable("skirmish.gearinspector.no_data");
        };
    }

    private static void drawRow(GuiGraphics graphics, Font font, Row row, int y, int width) {
        if (row.stack().isEmpty()) {
            graphics.fill(PAD, y + 1, PAD + ICON, y + 1 + ICON, EMPTY_ICON);
        } else {
            graphics.renderItem(row.stack(), PAD, y + 1);
            graphics.renderItemDecorations(font, row.stack(), PAD, y + 1);
        }
        int textY = row.extra().isEmpty() ? y + 5 : y;
        int valueWidth = font.width(row.value());
        FormattedCharSequence name = row.name();
        if (name != null) {
            graphics.drawString(font, name, TEXT_X, textY, TEXT, true);
            graphics.drawString(font, row.value(), width - PAD - valueWidth, textY, row.valueColor(), true);
        } else {
            graphics.drawString(font, row.value(), TEXT_X, textY, row.valueColor(), true);
        }
        int lineY = textY + LINE;
        for (FormattedCharSequence line : row.extra()) {
            graphics.drawString(font, line, TEXT_X, lineY, DIM, true);
            lineY += LINE;
        }
    }

    private static Component join(List<Component> parts) {
        MutableComponent joined = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                joined.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
            }
            joined.append(parts.get(i));
        }
        return joined;
    }

    private static FormattedCharSequence ellipsize(Font font, Component text, int maxWidth) {
        if (maxWidth <= 0) {
            return FormattedCharSequence.EMPTY;
        }
        if (font.width(text) <= maxWidth) {
            return text.getVisualOrderText();
        }
        FormattedText cut = font.substrByWidth(text, Math.max(0, maxWidth - font.width(CommonComponents.ELLIPSIS)));
        return Language.getInstance().getVisualOrder(FormattedText.composite(cut, CommonComponents.ELLIPSIS));
    }
}
