package dev.skirmish.module.fullbright;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.setting.NumberSetting;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.Nullable;

/**
 * Brighter world in caves and at night: the lightmap is computed with a higher gamma than the video settings allow
 * (render-only mixin on LightTexture, see {@code mixin.LightTextureMixin}). Options.gamma is never written. The
 * Darkness effect fades the boost out (see {@link FullbrightMath#gamma}); Blindness and Darkness fog are untouched.
 */
public final class FullbrightModule extends Module {
    public static final String ID = "fullbright";
    private static @Nullable FullbrightModule instance;

    final NumberSetting strength = add(new NumberSetting("strength", 100, 10, 100, 5).unit("%"));
    final KeySetting key = add(new KeySetting("key", "key.skirmish.fullbright.toggle"));

    public FullbrightModule() {
        super(ID, false);
        instance = this;
    }

    @Override
    public Category category() {
        return Category.VISUAL;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        // The toggle key works while the module is off too, so it is polled here instead of in tick().
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (SkirmishKeys.FULLBRIGHT_TOGGLE.consumeClick()) {
                if (isBlocked() || mc.player == null) {
                    continue;
                }
                toggle();
                mc.player.displayClientMessage(Component.translatable(isEnabled()
                        ? "skirmish.fullbright.toggled_on" : "skirmish.fullbright.toggled_off"), true);
            }
        });
    }

    /** Called from the LightTexture mixin with the user's gamma; returns the gamma to use this frame. */
    public static float gamma(float vanillaGamma, float partialTick) {
        FullbrightModule module = instance;
        if (module == null || !module.isEnabled()) {
            return vanillaGamma;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        float darkness = player == null ? 0f : player.getEffectBlendFactor(MobEffects.DARKNESS, partialTick);
        return FullbrightMath.gamma(vanillaGamma, FullbrightMath.targetGamma(module.strength.get()), darkness);
    }
}
