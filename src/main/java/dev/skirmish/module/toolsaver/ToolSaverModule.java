package dev.skirmish.module.toolsaver;

import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/**
 * «Tool Saver»: a pickaxe, axe or shovel about to break stops mining (and, if asked, a weapon stops hitting) with a
 * note in the action bar; press again right away to use it anyway. A pill in the top-centre column warns before
 * that. It only holds back your own click (nothing is sent or done for you); Feature Control id {@code tool_saver}.
 */
public final class ToolSaverModule extends Module {
    public static final String ID = "tool_saver";
    static final String L = "layout.toolsaver.";
    private static volatile @Nullable ToolSaverModule instance;

    final NumberSetting threshold = add(new NumberSetting("threshold", 10, 1, 100, 1));
    final BoolSetting weapons = add(new BoolSetting("weapons", false));
    final NumberSetting warnPercent = (NumberSetting) add(new NumberSetting("warn_percent", 10, 1, 50, 1).unit("%"));
    final BoolSetting sound = add(new BoolSetting("sound", true));

    private long blockedAt = -1;
    private long notifiedAt = -1;
    private boolean override;

    public ToolSaverModule() {
        super(ID, true);
    }

    public static @Nullable ToolSaverModule instance() {
        return instance;
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
        instance = this;
        Hud.get().register(new ToolSaverHud(this));
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (override && !mc.options.keyAttack.isDown()) {
            override = false;
        }
    }

    /** Uses left on a damageable item, or -1. */
    static int usesLeft(ItemStack stack) {
        if (stack.isEmpty() || !stack.isDamageableItem()) {
            return -1;
        }
        return ToolRules.usesLeft(stack.getMaxDamage(), stack.getDamageValue());
    }

    /** The main-hand item is protected against this kind of use now. */
    private boolean guarded(ItemStack stack, boolean onBlock) {
        int left = usesLeft(stack);
        if (left < 0 || !ToolRules.protect(left, threshold.get().intValue())) {
            return false;
        }
        return onBlock ? stack.has(DataComponents.TOOL)
                : weapons.get() && (stack.has(DataComponents.WEAPON) || stack.has(DataComponents.TOOL));
    }

    /** From {@code Minecraft.startAttack}: true holds the click back. */
    public boolean holdStart(Minecraft mc) {
        if (!isEnabled() || mc.player == null || mc.hitResult == null || override) {
            return false;
        }
        HitResult.Type type = mc.hitResult.getType();
        if (type == HitResult.Type.MISS) {
            return false;
        }
        ItemStack stack = mc.player.getMainHandItem();
        if (!guarded(stack, type == HitResult.Type.BLOCK)) {
            return false;
        }
        long now = Util.getMillis();
        if (ToolRules.bypass(now, blockedAt, Math.round(Theme.get().num(L + "bypass_ms")))) {
            override = true;
            log("bypassed for %s (%d uses left)", stack.getHoverName().getString(), usesLeft(stack));
            return false;
        }
        blockedAt = now;
        notify(mc, stack, now);
        return true;
    }

    /** From {@code Minecraft.continueAttack}: true stops mining this tick. */
    public boolean holdContinue(Minecraft mc, boolean held) {
        if (!held || !isEnabled() || mc.player == null || override || mc.hitResult == null
                || mc.hitResult.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        ItemStack stack = mc.player.getMainHandItem();
        if (!guarded(stack, true)) {
            return false;
        }
        long now = Util.getMillis();
        blockedAt = now;
        if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            mc.gameMode.stopDestroyBlock();
        }
        notify(mc, stack, now);
        return true;
    }

    private void notify(Minecraft mc, ItemStack stack, long now) {
        if (now - notifiedAt < Theme.get().num(L + "notify_ms")) {
            return;
        }
        notifiedAt = now;
        mc.player.displayClientMessage(Component.translatable("skirmish.tool_saver.blocked", stack.getHoverName(), usesLeft(stack)), true);
        if (sound.get()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS, 0.6f));
        }
        log("held back %s (%d uses left)", stack.getHoverName().getString(), usesLeft(stack));
    }

    /** The warning for the main-hand item: {uses left, max, protected (0/1)} or null when it is fine. */
    int @Nullable [] warning(ItemStack stack) {
        int left = usesLeft(stack);
        if (left < 0 || !(stack.has(DataComponents.TOOL) || stack.has(DataComponents.WEAPON))) {
            return null;
        }
        int limit = threshold.get().intValue();
        if (!ToolRules.warn(left, stack.getMaxDamage(), warnPercent.get(), limit)) {
            return null;
        }
        boolean guards = ToolRules.protect(left, limit) && (stack.has(DataComponents.TOOL) || weapons.get());
        return new int[]{left, stack.getMaxDamage(), guards ? 1 : 0};
    }
}
