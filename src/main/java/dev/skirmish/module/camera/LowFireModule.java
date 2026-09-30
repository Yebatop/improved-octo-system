package dev.skirmish.module.camera;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.effect.MobEffects;
import org.jspecify.annotations.Nullable;

/**
 * Lower, fainter first-person fire overlay, like a "low fire" resource pack: the render-only mixin
 * {@code mixin.LowFireMixin} moves the two fire quads down and scales their alpha. You still see that you burn.
 */
public final class LowFireModule extends Module {
    public static final String ID = "low_fire";
    private static @Nullable LowFireModule instance;

    final NumberSetting height = add(new NumberSetting("lower_by", 30, 0, 50, 5).unit("%"));
    final NumberSetting opacity = add(new NumberSetting("opacity", 60, 20, 100, 5).unit("%"));
    final BoolSetting hideWhenResistant = add(new BoolSetting("hide_when_resistant", false));

    public LowFireModule() {
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

    private static @Nullable LowFireModule active() {
        LowFireModule module = instance;
        return module != null && module.isEnabled() ? module : null;
    }

    /** Vertical shift of the overlay in its own units (the quads are 1 unit tall; vanilla already lowers them 0.3). */
    public static float offset() {
        LowFireModule module = active();
        return module == null ? 0f : (float) (module.height.get() / 100.0);
    }

    /** Multiplier for the overlay's alpha (vanilla 0.9). */
    public static float alpha() {
        LowFireModule module = active();
        return module == null ? 1f : (float) (module.opacity.get() / 100.0);
    }

    /** With Fire Resistance the burning costs nothing, so the overlay may be dropped altogether (optional). */
    public static boolean hidden() {
        LowFireModule module = active();
        if (module == null || !module.hideWhenResistant.get()) {
            return false;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && player.hasEffect(MobEffects.FIRE_RESISTANCE);
    }
}
