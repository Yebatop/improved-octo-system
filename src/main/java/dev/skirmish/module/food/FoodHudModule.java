package dev.skirmish.module.food;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Util;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * «Food HUD»: the hunger row as a Skirmish bar in vanilla's place: gold for saturation, warm orange for hunger, red
 * and pulsing at 6 or less (no sprinting), green under the Hunger effect, faint ticks where vanilla's icons would end.
 * While you hold food, what it would bring glows on the bar with "+3 · +3,6" next to it. The «Иконки» style keeps
 * vanilla's drumsticks and tints the saturated ones gold instead. Food items get the same line in their tooltip.
 * Drawn only where vanilla draws its hunger row. Render-only; Feature Control id {@code food_hud}.
 */
public final class FoodHudModule extends Module {
    public static final String ID = "food_hud";
    static final String L = "layout.food.";
    private static final Identifier FOOD_FULL = Identifier.withDefaultNamespace("hud/food_full");
    private static final Identifier FOOD_HALF = Identifier.withDefaultNamespace("hud/food_half");
    private static final Identifier FOOD_FULL_HUNGER = Identifier.withDefaultNamespace("hud/food_full_hunger");

    /** A bar in the mod's style, or vanilla's icons with the saturated ones tinted gold. */
    public enum Style {
        BAR, ICONS
    }

    final EnumSetting<Style> style = add(new EnumSetting<>("style", Style.BAR));
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
        // In vanilla's place, so it shows exactly when vanilla's hunger row would (survival, not riding).
        HudElementRegistry.replaceElement(VanillaHudElements.FOOD_BAR, vanilla -> (HudElement) (graphics, delta) -> {
            if (!isEnabled() || style.get() == Style.ICONS) {
                vanilla.render(graphics, delta);
            }
            if (isEnabled()) {
                render(graphics);
            }
        });
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

    private void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.options.hideGui) {
            return;
        }
        Theme theme = Theme.get();
        FoodData data = player.getFoodData();
        int food = data.getFoodLevel();
        float sat = data.getSaturationLevel();
        int top = g.guiHeight() - 39;
        int right = g.guiWidth() / 2 + 91;
        FoodProperties held = preview.get() ? heldFood(player) : null;
        float pulse = FoodMath.pulse(Util.getMillis(), theme.num(L + "pulse_ms"));
        if (style.get() == Style.BAR) {
            bar(g, player, food, sat, held, pulse, top, right);
        } else {
            icons(g, food, sat, held, pulse, top, right);
        }
        if (held != null && label.get()) {
            Ui ui = Ui.begin(g);
            try {
                float k = 1f / Ui.designScale();
                String text = "+" + held.nutrition() + " · +" + Ui.decimal(held.saturation(), 1);
                ui.text("food_label", text, (right + 4) * k, top * k + (9 * k - ui.lineHeight("food_label")) / 2f);
            } finally {
                ui.end();
            }
        }
    }

    /**
     * The bar: a drumstick, then a rounded track over the rest of vanilla's row. Hunger fills it from the left,
     * saturation (never more than hunger) lies over that in gold; the hunger held food would add glows beyond (its
     * saturation is in the label).
     */
    private void bar(GuiGraphics g, Player player, int food, float sat, @Nullable FoodProperties held, float pulse, int top, int right) {
        Theme theme = Theme.get();
        int left = right - 81;
        boolean hunger = player.hasEffect(MobEffects.HUNGER);
        boolean low = food <= FoodMath.LOW;
        int iconAlpha = low && !hunger ? Math.round(255 * (0.55f + 0.45f * pulse)) : 255;
        g.blitSprite(RenderPipelines.GUI_TEXTURED, hunger ? FOOD_FULL_HUNGER : FOOD_FULL, left, top, 9, 9, ARGB.color(iconAlpha, 0xFFFFFF));
        Ui ui = Ui.begin(g);
        try {
            float k = 1f / Ui.designScale();
            float bx = (left + 11) * k;
            float bw = (right - left - 11) * k;
            float bh = theme.num(L + "bar_height") * k;
            float by = top * k + (9 * k - bh) / 2f;
            float r = bh / 2f;
            ui.rect(bx, by, bw, bh, r, theme.color("food_track"));
            int fill = theme.color(hunger ? "food_hunger" : low ? "food_low" : "food_fill");
            if (held != null && food < FoodMath.MAX) {
                // The glow takes the colour the bar will have after eating (out of the red once above 6).
                int after = FoodMath.foodAfter(food, held.nutrition());
                int then = theme.color(hunger ? "food_hunger" : after <= FoodMath.LOW ? "food_low" : "food_fill");
                ui.rect(bx, by, part(bw, bh, after), bh, r, withAlpha(then, Math.round(70 + 110 * pulse)));
            }
            if (food > 0) {
                int a = low && !hunger ? Math.round(255 * (0.6f + 0.4f * pulse)) : 255;
                ui.rect(bx, by, part(bw, bh, food), bh, r, withAlpha(fill, a));
            }
            if (saturation.get() && sat > 0f) {
                ui.rect(bx, by, part(bw, bh, sat), bh, r, theme.color("food_saturation"));
            }
            // Where vanilla's ten icons would end: faint ticks, so "half a bar" still reads as five drumsticks.
            int tick = theme.color("food_tick");
            for (int i = 1; i < FoodMath.ICONS; i++) {
                float tx = bx + bw * i / FoodMath.ICONS;
                ui.rect(tx - 0.5f, by + 1, 1f, bh - 2, 0f, tick);
            }
            ui.border(bx, by, bw, bh, r, theme.num("stroke.width"), theme.color("food_stroke"));
        } finally {
            ui.end();
        }
    }

    /** Width of {@code points} of 20 on a bar, never thinner than its round ends. */
    private static float part(float width, float height, float points) {
        if (points <= 0f) {
            return 0f;
        }
        return Math.max(height, width * Math.min(FoodMath.MAX, points) / FoodMath.MAX);
    }

    /** Vanilla's icons: the saturated ones tinted gold, what held food would add pulsing. */
    private void icons(GuiGraphics g, int food, float sat, @Nullable FoodProperties held, float pulse, int top, int right) {
        int gold = Theme.get().color("food_saturation");
        if (held != null && food < FoodMath.MAX) {
            int after = FoodMath.foodAfter(food, held.nutrition());
            int alpha = Math.round(255 * (0.25f + 0.55f * pulse));
            for (int i = 0; i < FoodMath.ICONS; i++) {
                if (FoodMath.gains(food, after, i)) {
                    Identifier sprite = FoodMath.onIcon(after, i) >= 2 ? FOOD_FULL : FOOD_HALF;
                    g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, right - i * 8 - 9, top, 9, 9, ARGB.color(alpha, 0xFFFFFF));
                }
            }
        }
        if (!saturation.get()) {
            return;
        }
        float satAfter = held == null ? sat : FoodMath.saturationAfter(food, sat, held.nutrition(), held.saturation());
        for (int i = 0; i < FoodMath.ICONS; i++) {
            int points = FoodMath.onIcon(sat, i);
            int alpha = points >= 2 ? 150 : points == 1 ? 90 : 0;
            if (alpha == 0 && FoodMath.gains(sat, satAfter, i)) {
                alpha = Math.round(40 + 110 * pulse);
            }
            if (alpha > 0) {
                Identifier sprite = FoodMath.onIcon(Math.max(sat, satAfter), i) >= 2 ? FOOD_FULL : FOOD_HALF;
                g.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, right - i * 8 - 9, top, 9, 9, ARGB.color(alpha, gold));
            }
        }
    }

    private static int withAlpha(int argb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (argb & 0xFFFFFF);
    }
}
