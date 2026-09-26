package dev.skirmish.module.weaponstats;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * «Weapon Stats»: the tooltip of a sword, axe, mace or trident gets its real numbers — damage with Sharpness (and
 * your Strength), a critical hit, full-charge swings per second and damage per second, the bonus of Smite, Bane of
 * Arthropods and Sweeping Edge — and how it compares with the weapon in your hand. Tooltip only; Feature Control id
 * {@code weapon_stats}.
 */
public final class WeaponStatsModule extends Module {
    public static final String ID = "weapon_stats";

    final BoolSetting compare = add(new BoolSetting("compare", true));
    final BoolSetting effects = add(new BoolSetting("effects", true));

    public WeaponStatsModule() {
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

    /** Main-hand damage and attack speed of an item for this player, or null when it is not a melee weapon. */
    record Stats(double damage, double speed, int sharp, int smite, int bane, int sweep) {
    }

    @Override
    public void onInitialize() {
        ItemTooltipCallback.EVENT.register((stack, context, type, lines) -> {
            if (!isEnabled()) {
                return;
            }
            try {
                addLines(stack, lines);
            } catch (RuntimeException e) {
                error("tooltip failed", e);
            }
        });
    }

    static @Nullable Stats stats(ItemStack stack, @Nullable LocalPlayer player, boolean withEffects) {
        ItemAttributeModifiers mods = stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
        double damageAdd = 0;
        double speedAdd = 0;
        boolean weapon = false;
        for (ItemAttributeModifiers.Entry e : mods.modifiers()) {
            if (!e.slot().test(EquipmentSlot.MAINHAND) || e.modifier().operation() != AttributeModifier.Operation.ADD_VALUE) {
                continue;
            }
            if (e.attribute().is(Attributes.ATTACK_DAMAGE)) {
                damageAdd += e.modifier().amount();
                weapon = true;
            } else if (e.attribute().is(Attributes.ATTACK_SPEED)) {
                speedAdd += e.modifier().amount();
            }
        }
        if (!weapon) {
            return null;
        }
        double baseDamage = player != null ? player.getAttributeBaseValue(Attributes.ATTACK_DAMAGE) : 1.0;
        double baseSpeed = player != null ? player.getAttributeBaseValue(Attributes.ATTACK_SPEED) : 4.0;
        ItemEnchantments ench = stack.getEnchantments();
        int sharp = level(ench, Enchantments.SHARPNESS);
        double damage = baseDamage + damageAdd + WeaponMath.sharpness(sharp);
        if (withEffects && player != null) {
            damage += WeaponMath.effects(effectLevel(player, MobEffects.STRENGTH), effectLevel(player, MobEffects.WEAKNESS));
        }
        return new Stats(Math.max(0, damage), baseSpeed + speedAdd, sharp, level(ench, Enchantments.SMITE),
                level(ench, Enchantments.BANE_OF_ARTHROPODS), level(ench, Enchantments.SWEEPING_EDGE));
    }

    private static int level(ItemEnchantments ench, ResourceKey<Enchantment> key) {
        for (Holder<Enchantment> h : ench.keySet()) {
            if (h.is(key)) {
                return ench.getLevel(h);
            }
        }
        return 0;
    }

    private static int effectLevel(LocalPlayer player, Holder<net.minecraft.world.effect.MobEffect> effect) {
        MobEffectInstance i = player.getEffect(effect);
        return i == null ? 0 : i.getAmplifier() + 1;
    }

    private void addLines(ItemStack stack, List<Component> lines) {
        LocalPlayer player = Minecraft.getInstance().player;
        Stats s = stats(stack, player, effects.get());
        if (s == null) {
            return;
        }
        String dmg = Ui.decimal(s.damage(), 1);
        String crit = Ui.decimal(WeaponMath.crit(s.damage()), 1);
        lines.add(Component.translatable("skirmish.weapon_stats.damage", dmg, crit).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("skirmish.weapon_stats.speed", Ui.decimal(s.speed(), 2),
                Ui.decimal(WeaponMath.swingSeconds(s.speed()), 2), Ui.decimal(WeaponMath.dps(s.damage(), s.speed()), 1))
                .withStyle(ChatFormatting.GRAY));
        if (s.smite() > 0) {
            lines.add(Component.translatable("skirmish.weapon_stats.smite", Ui.decimal(WeaponMath.smite(s.smite()), 1)).withStyle(ChatFormatting.DARK_GREEN));
        }
        if (s.bane() > 0) {
            lines.add(Component.translatable("skirmish.weapon_stats.bane", Ui.decimal(WeaponMath.smite(s.bane()), 1)).withStyle(ChatFormatting.DARK_GREEN));
        }
        if (s.sweep() > 0) {
            lines.add(Component.translatable("skirmish.weapon_stats.sweep", Ui.decimal(WeaponMath.sweep(s.damage(), s.sweep()), 1)).withStyle(ChatFormatting.GRAY));
        }
        if (compare.get() && player != null) {
            ItemStack held = player.getMainHandItem();
            if (held != stack && !held.isEmpty()) {
                Stats h = stats(held, player, effects.get());
                if (h != null) {
                    double diff = WeaponMath.dps(s.damage(), s.speed()) - WeaponMath.dps(h.damage(), h.speed());
                    if (Math.abs(diff) >= 0.05) {
                        MutableComponent line = Component.translatable(diff > 0 ? "skirmish.weapon_stats.better" : "skirmish.weapon_stats.worse",
                                Ui.decimal(Math.abs(diff), 1));
                        lines.add(line.withStyle(diff > 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
                    }
                }
            }
        }
    }
}
