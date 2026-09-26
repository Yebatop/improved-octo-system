package dev.skirmish.module.menus;

import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * «Loading & Transitions»: connecting, loading-terrain and saving screens get the Skirmish space backdrop, the logo
 * and a HolyWorld tip instead of dirt, panorama or the chunk grid, and the world fades in from dark after joining,
 * respawning or changing dimension. Render-only; Feature Control id {@code transitions}.
 */
public final class TransitionsModule extends Module {
    public static final String ID = "transitions";
    private static volatile @Nullable TransitionsModule instance;

    final BoolSetting loading = add(new BoolSetting("loading", true));
    final BoolSetting fade = add(new BoolSetting("fade", true));

    private @Nullable ClientLevel lastLevel;
    private boolean pending;
    private long fadeStart = -1;

    public TransitionsModule() {
        super(ID, true);
    }

    @Override
    public Category category() {
        return Category.INTERFACE;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        instance = this;
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("skirmish", "transition_fade"), this::renderFade);
    }

    public static boolean loadingScreens() {
        TransitionsModule m = instance;
        return m != null && m.isEnabled() && m.loading.get();
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            if (mc.level != null) {
                pending = true;
            }
        }
    }

    /** Fade-in factor 1 → 0 after a world appears; also used by tests. */
    static float fadeAlpha(long sinceMs, float durationMs) {
        if (sinceMs < 0 || sinceMs >= durationMs) {
            return 0f;
        }
        float f = 1f - sinceMs / durationMs;
        return f * f;
    }

    private void renderFade(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!isEnabled() || !fade.get()) {
            pending = false;
            return;
        }
        long now = Util.getMillis();
        if (pending && mc.screen == null && mc.level != null) {
            pending = false;
            fadeStart = now;
        }
        if (fadeStart < 0) {
            return;
        }
        float a = fadeAlpha(now - fadeStart, Theme.get().num("layout.menus.fade_ms"));
        if (a <= 0f) {
            fadeStart = -1;
            return;
        }
        Ui ui = Ui.begin(graphics);
        try {
            ui.pushAlpha(a);
            MenuBackdrop.draw(ui, ui.width(), ui.height(), now, true, false);
            ui.popAlpha();
        } finally {
            ui.end();
        }
    }
}
