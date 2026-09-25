package dev.skirmish.module.food;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * «Food HUD», like AppleSkin: saturation drawn as golden frames over the hunger icons, and while you hold food the
 * hunger and saturation it would bring pulse on the bar, with "+3 · +3,6" next to it. Food items get the same line
 * in their tooltip. Drawn right after vanilla's hunger bar (only when that bar is shown). Render-only; Feature
 * Control id {@code food_hud}.
 */
public final class FoodHudModule extends Module {
    public static final String ID = "food_hud";
    static final String L = "layout.food.";
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");

    final BoolSetting saturation = add(new BoolSetting("saturation", true));
    final BoolSetting preview = add(new BoolSetting("preview", true));
    final BoolSetting label = add(new BoolSetting("label", true));
    final BoolSetting tooltip = add(new BoolSetting("tooltip", true));

    public FoodHudModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.UTILITY;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        HudElementRegistry.attachElementAfter(VanillaHudElements.FOOD_BAR, Identifier.fromNamespaceAndPath("skirmish", ID), this::render);
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (isEnabled() && tooltip.get()) {
                FoodProperties food = stack.get(DataComponents.FOOD);
                if (food != null && food.nutrition() > 0) {
                    lines.add(Component.translatable("skirmish.food.tooltip", food.nutrition(), Ui.decimal(food.saturation(), 1))
                            .withStyle(ChatFormatting.GRAY));
                }
            }
        });
    }

    /** The food in hand that would be eaten (main hand first), or null. */
    private static @Nullable FoodProperties heldFood(Player player) {
        for (ItemStack stack : new ItemStack[]{player.getMainHandItem(), player.getOffhandItem()}) {
            FoodProperties food = stack.get(DataComponents.FOOD);
            if (food != null) {
                return food;
            }
        }
        return null;
    }

    private void render(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (!isEnabled() || player == null || mc.options.hideGui) {
            return;
        }
        Theme theme = Theme.get();
        FoodData data = player.getFoodData();
        int food = data.getFoodLevel();
        float sat = data.getSaturationLevel();
        int top = g.guiHeight() - 39;
        int right = g.guiWidth() / 2 + 91;
        int gold = theme.color("food_saturation");
        FoodProperties held = preview.get() ? heldFood(player) : null;
        float pulse = FoodMath.pulse(Util.getMillis(), theme.num(L + "pulse_ms"));

        if (held != null && food < FoodMath.MAX) {
            int after = FoodMath.foodAfter(food, held.nutrition());
            int alpha = Math.round(255 * (0.25f + 0.55f * pulse));
            for (int i = 0; i < FoodMath.ICONS; i++) {
                if (FoodMath.gains(food, after, i)) {
                    int x = right - i * 8 - 9;
                    Identifier sprite = FoodMath.onIcon(after, i) >= 2 ? FOOD_FULL : FOOD_HALF;
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, top, 9, 9, ARGB.color(alpha, 0xFFFFFF));
                }
            }
        }
        if (saturation.get()) {
            for (int i = 0; i < FoodMath.ICONS; i++) {
                int points = FoodMath.onIcon(sat, i);
                if (points > 0) {
                    frame(g, right - i * 8 - 9, top, withAlpha(gold, points >= 2 ? 230 : 120));
                }
            }
            if (held != null) {
                float satAfter = FoodMath.saturationAfter(food, sat, held.nutrition(), held.saturation());
                int alpha = Math.round(255 * (0.3f + 0.6f * pulse));
                for (int i = 0; i < FoodMath.ICONS; i++) {
                    if (FoodMath.gains(sat, satAfter, i)) {
                        frame(g, right - i * 8 - 9, top, withAlpha(gold, alpha));
                    }
                }
            }
        }
        if (held != null && label.get()) {
            String text = "+" + held.nutrition() + " · +" + Ui.decimal(held.saturation(), 1);
            g.drawString(mc.font, text, right + 4, top + 1, withAlpha(gold, 255), true);
        }
    }

    /** A 1 px frame around a 9×9 icon. */
    private static void frame(GuiGraphics g, int x, int y, int color) {
        g.fill(x, y, x + 9, y + 1, color);
        g.fill(x, y + 8, x + 9, y + 9, color);
        g.fill(x, y + 1, x + 1, y + 8, color);
        g.fill(x + 8, y + 1, x + 9, y + 8, color);
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }
}
