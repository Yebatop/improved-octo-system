package dev.skirmish.ui.widget;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

/**
 * Puts UI-kit widgets on a vanilla screen (death screen, anvil): draws them after the screen (below its tooltip) and routes clicks to
 * them before vanilla sees them. Call {@link #attach} from {@code ScreenEvents.AFTER_INIT}; the owner lays the
 * widgets out in {@code layout} every frame.
 */
public final class ScreenWidgets {
    /** Lays out and draws the widgets (design px); runs inside {@link Ui#begin}. */
    public interface Layout {
        void draw(Ui ui, ScreenWidgets widgets, double mx, double my);
    }

    /** Layouts per screen; drawn by ScreenMixin before the deferred tooltip. Cleared on every (re)init, like Fabric's per-screen events. */
    private static final Map<Screen, List<Consumer<GuiGraphics>>> ATTACHED = new WeakHashMap<>();

    static {
        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) -> ATTACHED.remove(screen));
    }

    private final List<Widget> frame = new ArrayList<>();
    private List<Widget> hit = List.of();
    private @Nullable Widget pressed;

    private ScreenWidgets() {
    }

    public static ScreenWidgets attach(Screen screen, Layout layout) {
        ScreenWidgets widgets = new ScreenWidgets();
        ATTACHED.computeIfAbsent(screen, s -> new ArrayList<>()).add(graphics -> widgets.render(graphics, layout));
        ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !widgets.click(Ui.toDesign(event.x()), Ui.toDesign(event.y()), event.button()));
        ScreenMouseEvents.allowMouseRelease(screen).register((s, event) -> !widgets.release(Ui.toDesign(event.x()), Ui.toDesign(event.y()), event.button()));
        return widgets;
    }

    /** Called from ScreenMixin: after the screen's own render, before its tooltip. */
    public static void renderAttached(Screen screen, GuiGraphics graphics) {
        List<Consumer<GuiGraphics>> layouts = ATTACHED.get(screen);
        if (layouts == null) {
            return;
        }
        for (Consumer<GuiGraphics> layout : List.copyOf(layouts)) {
            layout.accept(graphics);
        }
    }

    /** Draws a widget at its bounds and makes it clickable this frame. */
    public void widget(Ui ui, Widget widget, double mx, double my) {
        frame.add(widget);
        widget.render(ui, mx, my, true);
    }

    private void render(GuiGraphics graphics, Layout layout) {
        Minecraft mc = Minecraft.getInstance();
        double mx = Ui.toDesign(mc.mouseHandler.getScaledXPos(mc.getWindow()));
        double my = Ui.toDesign(mc.mouseHandler.getScaledYPos(mc.getWindow()));
        frame.clear();
        Ui ui = Ui.begin(graphics);
        try {
            layout.draw(ui, this, mx, my);
        } finally {
            ui.end();
        }
        hit = List.copyOf(frame);
        Widget over = at(mx, my);
        if (over != null && over.clickable()) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    private @Nullable Widget at(double mx, double my) {
        for (int i = hit.size() - 1; i >= 0; i--) {
            Widget w = hit.get(i);
            if (w.enabled && w.contains(mx, my)) {
                return w;
            }
        }
        return null;
    }

    private boolean click(double mx, double my, int button) {
        Widget target = at(mx, my);
        if (target != null && target.mouseClicked(mx, my, button)) {
            pressed = target;
            return true;
        }
        return false;
    }

    private boolean release(double mx, double my, int button) {
        if (pressed != null) {
            pressed.mouseReleased(mx, my, button);
            pressed = null;
            return true;
        }
        return false;
    }
}
