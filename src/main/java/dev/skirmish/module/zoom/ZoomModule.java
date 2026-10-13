package dev.skirmish.module.zoom;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.KeySetting;
import dev.skirmish.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Hold-to-zoom: while the zoom key (vanilla KeyMapping, default C) is held the world FOV is divided by the factor.
 * The wheel changes the factor while zooming (the scroll is then not passed to the hotbar); smoothing, a lower
 * mouse sensitivity and hiding the hand are optional. Everything happens in rendering and camera input scaling via
 * the mixins in {@code mixin}; no key is pressed and nothing is sent.
 */
public final class ZoomModule extends Module {
    public static final String ID = "zoom";
    private static @Nullable ZoomModule instance;

    final NumberSetting factor = add(new NumberSetting("factor", 4, 2, 10, 0.5).unit("x"));
    final BoolSetting scroll = add(new BoolSetting("scroll", true));
    final BoolSetting smooth = add(new BoolSetting("smooth", true));
    final BoolSetting lowerSensitivity = add(new BoolSetting("lower_sensitivity", true));
    final BoolSetting hideHand = add(new BoolSetting("hide_hand", true));
    final KeySetting key = add(new KeySetting("key", "key.skirmish.zoom"));

    private boolean zooming;
    /** Factor of the current press: starts at the setting, changed by the wheel, forgotten on release. */
    private double sessionFactor = 4;
    /** FOV multiplier (1 / factor) last tick and this tick, for smoothing. */
    private double previous = 1;
    private double current = 1;

    public ZoomModule() {
        super(ID, true);
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
    protected void onDisable() {
        zooming = false;
        previous = 1;
        current = 1;
    }

    /** Reads the key (render or client thread); starts a new zoom session on press. */
    private void poll() {
        boolean held = SkirmishKeys.ZOOM.isDown() && Minecraft.getInstance().screen == null;
        if (held && !zooming) {
            sessionFactor = ZoomMath.clampFactor(factor.get());
            log("zoom in x%.2f", sessionFactor);
        }
        zooming = held;
    }

    private double target() {
        return zooming ? 1.0 / sessionFactor : 1.0;
    }

    @Override
    public void tick() {
        poll();
        previous = current;
        current = smooth.get() ? ZoomMath.approach(current, target()) : target();
    }

    /** FOV multiplier for this frame (1 = no zoom). */
    private double multiplier(float partialTick) {
        if (!smooth.get()) {
            poll();
            return target();
        }
        return ZoomMath.lerp(Math.max(0f, Math.min(1f, partialTick)), previous, current);
    }

    private static @Nullable ZoomModule active() {
        ZoomModule module = instance;
        return module != null && module.isEnabled() ? module : null;
    }

    // ---- hooks called from the mixins (render / input thread = client thread) ----

    /** GameRenderer.getFov for the world (not the hand): the zoomed FOV. */
    public static float fov(float fov, float partialTick) {
        ZoomModule module = active();
        if (module == null) {
            return fov;
        }
        double m = module.multiplier(partialTick);
        return m >= 1.0 ? fov : (float) (fov * m);
    }

    /** MouseHandler.turnPlayer: the sensitivity option value to use this frame. */
    public static double sensitivity(double sensitivity) {
        ZoomModule module = active();
        if (module == null || !module.lowerSensitivity.get()) {
            return sensitivity;
        }
        return ZoomMath.scaledSensitivity(sensitivity, module.current);
    }

    /** MouseHandler.onScroll with no screen open: true when the wheel changed the zoom and must not reach the hotbar. */
    public static boolean scroll(double vertical) {
        ZoomModule module = active();
        if (module == null || !module.scroll.get() || vertical == 0) {
            return false;
        }
        module.poll();
        if (!module.zooming) {
            return false;
        }
        module.sessionFactor = ZoomMath.scroll(module.sessionFactor, vertical);
        return true;
    }

    /** ItemInHandRenderer: skip the first-person hands while zoomed in. */
    public static boolean hidesHand() {
        ZoomModule module = active();
        return module != null && module.hideHand.get() && (module.zooming || module.current < 0.99);
    }
}
