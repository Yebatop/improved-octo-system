package dev.skirmish.binds;

import dev.skirmish.gui.ModuleIcons;
import dev.skirmish.hud.Hud;
import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.module.Module;
import dev.skirmish.ui.Ui;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The short pill above the crosshair when a bind switches a module: its icon, its name and «ВКЛ» / «ВЫКЛ» (or
 * «заблокирован сервером»). Fades out after {@link #SHOW_MS}.
 */
public final class BindToast extends HudBlock {
    static final long SHOW_MS = 1600;
    private static final String L = "layout.binds.";
    private static @Nullable Module module;
    private static boolean on;
    private static boolean blocked;
    private static long shownAt;

    public BindToast() {
        super("bind_toast", "skirmish.hud.element.bind_toast", new Placement(0.5f, 0.5f, 0.5f, 1f, 0, -40));
    }

    public static void install() {
        Hud.get().register(new BindToast());
    }

    static void show(Module m, boolean enabled, boolean isBlocked) {
        module = m;
        on = enabled;
        blocked = isBlocked;
        shownAt = Util.getMillis();
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public boolean shown() {
        return module != null && Util.getMillis() - shownAt < SHOW_MS;
    }

    private static String state() {
        return Ui.tr(blocked ? "skirmish.binds.blocked" : on ? "skirmish.binds.on" : "skirmish.binds.off");
    }

    private static String name(boolean preview) {
        Module m = module;
        return m == null || preview ? "Fullbright" : Component.translatable(m.nameKey()).getString();
    }

    @Override
    public float width(Ui ui, boolean preview) {
        return ui.num(L + "toast_pad") * 2 + ui.num(L + "toast_icon") + ui.num(L + "toast_gap") * 2
                + ui.textWidth("bind_toast", name(preview)) + ui.textWidth("bind_state", preview ? Ui.tr("skirmish.binds.on") : state());
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num(L + "toast_h");
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.pill(ui, x, y, w, h);
        float pad = ui.num(L + "toast_pad");
        float icon = ui.num(L + "toast_icon");
        boolean isOn = preview || on && !blocked;
        int tone = ui.color(isOn ? "good" : blocked ? "warn" : "text_3");
        if (module != null && !preview) {
            ModuleIcons.draw(ui, module, x + pad, y + (h - icon) / 2f, icon, tone);
        }
        float tx = x + pad + icon + ui.num(L + "toast_gap");
        tx = ui.textCentered("bind_toast", name(preview), tx, y, h) + ui.num(L + "toast_gap");
        ui.textCentered("bind_state", preview ? Ui.tr("skirmish.binds.on") : state(), tx, y, h, tone);
    }
}
