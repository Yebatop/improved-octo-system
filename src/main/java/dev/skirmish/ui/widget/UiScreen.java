package dev.skirmish.ui.widget;

import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Screen drawn entirely with the UI kit (no vanilla widgets or background). Subclasses lay out and draw in
 * {@link #draw} and register the widgets they drew via {@link #widget}; input is routed to those widgets in design
 * px. Appearing and closing fade over {@code motion.appear_ms}.
 */
public abstract class UiScreen extends Screen {
    protected final @Nullable Screen parent;
    private record Hit(Widget widget, float @Nullable [] clip) {
    }

    private final List<Hit> frameWidgets = new ArrayList<>();
    private List<Hit> hitWidgets = List.of();
    private final java.util.ArrayDeque<float[]> clips = new java.util.ArrayDeque<>();
    private final Anim appear = new Anim("appear_ms");
    private @Nullable Widget pressed;
    private @Nullable Widget focused;
    private boolean closing;

    protected UiScreen(Component title, @Nullable Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void init() {
        appear.snap(appear.target() > 0f ? appear.value() : 0f);
        appear.target(1f);
        closing = false;
    }

    /** Current mouse position in design px (full precision, not the rounded GUI ints). */
    protected double[] mouse() {
        Minecraft mc = Minecraft.getInstance();
        double gx = mc.mouseHandler.getScaledXPos(mc.getWindow());
        double gy = mc.mouseHandler.getScaledYPos(mc.getWindow());
        return new double[]{Ui.toDesign(gx), Ui.toDesign(gy)};
    }

    /** Fade factor of the appear/close animation. */
    protected float appearProgress() {
        return appear.value();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public final void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (closing && !appear.running()) {
            minecraft.setScreen(parent);
            return;
        }
        double[] m = mouse();
        frameWidgets.clear();
        clips.clear();
        Ui ui = Ui.begin(graphics);
        try {
            ui.pushAlpha(appear.value());
            draw(ui, m[0], m[1]);
            ui.popAlpha();
        } finally {
            ui.end();
        }
        hitWidgets = List.copyOf(frameWidgets);
        Widget hovered = topAt(m[0], m[1]);
        if (hovered != null && hovered.clickable()) {
            graphics.requestCursor(CursorTypes.POINTING_HAND);
        }
    }

    /** Draws the screen; {@code mx/my} are design px. */
    protected abstract void draw(Ui ui, double mx, double my);

    /** Draws a widget at its current bounds and makes it receive input this frame. */
    protected void widget(Ui ui, Widget widget, double mx, double my) {
        float[] clip = clips.peek();
        frameWidgets.add(new Hit(widget, clip));
        boolean inClip = clip == null || (mx >= clip[0] && mx < clip[2] && my >= clip[1] && my < clip[3]);
        widget.render(ui, mx, my, inClip && (pressed == null || pressed == widget));
    }

    /** Clips drawing (scissor, 2 design px precision) and input of the widgets registered until {@link #popClip}. */
    protected void pushClip(Ui ui, float x0, float y0, float x1, float y1) {
        clips.push(new float[]{x0, y0, x1, y1});
        ui.graphics().enableScissor((int) Math.floor(x0), (int) Math.floor(y0), (int) Math.ceil(x1), (int) Math.ceil(y1));
    }

    protected void popClip(Ui ui) {
        clips.pop();
        ui.graphics().disableScissor();
    }

    private @Nullable Widget topAt(double mx, double my) {
        for (int i = hitWidgets.size() - 1; i >= 0; i--) {
            Hit hit = hitWidgets.get(i);
            float[] c = hit.clip();
            if (c != null && !(mx >= c[0] && mx < c[2] && my >= c[1] && my < c[3])) {
                continue;
            }
            if (hit.widget().enabled && hit.widget().contains(mx, my)) {
                return hit.widget();
            }
        }
        return null;
    }

    protected void focus(@Nullable Widget widget) {
        if (focused != null && focused != widget) {
            focused.setFocused(false);
        }
        focused = widget;
        if (widget != null) {
            widget.setFocused(true);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = Ui.toDesign(event.x());
        double my = Ui.toDesign(event.y());
        if (focused instanceof KeybindButton keybind && keybind.isListening()) {
            keybind.mouseClicked(mx, my, event.button());
            return true;
        }
        Widget target = topAt(mx, my);
        focus(target != null && target.focusable() ? target : null);
        if (target != null && target.mouseClicked(mx, my, event.button())) {
            pressed = target;
            return true;
        }
        return onBackgroundClick(mx, my, event.button());
    }

    /** Clicks that hit no widget. */
    protected boolean onBackgroundClick(double mx, double my, int button) {
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (pressed != null) {
            pressed.mouseReleased(Ui.toDesign(event.x()), Ui.toDesign(event.y()), event.button());
            pressed = null;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressed != null) {
            pressed.mouseDragged(Ui.toDesign(event.x()), Ui.toDesign(event.y()), event.button());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (focused != null && focused.keyPressed(event)) {
            return true;
        }
        if (focused != null && event.isEscape()) {
            focus(null);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return focused != null && focused.charTyped(event);
    }

    /** Fades out, then returns to the parent screen. */
    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            appear.target(0f);
            onClosing();
        }
    }

    protected void onClosing() {
    }

}
