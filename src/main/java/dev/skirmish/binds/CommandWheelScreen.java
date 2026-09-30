package dev.skirmish.binds;

import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.UiScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The command wheel: hold its key, point the mouse at one of your command binds around the ring and let go — that
 * one command is sent, once, like pressing its own key (a click does the same; Esc or letting go in the middle
 * sends nothing). For commands you use now and then without a key of their own.
 */
final class CommandWheelScreen extends UiScreen {
    private static final String L = "layout.wheel.";
    private final CommandBindsModule module;
    private final List<CommandBindsModule.Slot> items = new ArrayList<>();
    private int hovered = -1;
    private boolean done;
    private final Button openBinds = new Button(() -> Ui.tr("skirmish.binds.wheel.open_binds"), true,
            () -> minecraft.setScreen(new BindsScreen(null))).layout(L);

    CommandWheelScreen(CommandBindsModule module) {
        super(Component.translatable("skirmish.binds.wheel.title"), null);
        this.module = module;
        for (CommandBindsModule.Slot s : module.slots()) {
            if (!s.command().get().isBlank()) {
                items.add(s);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The angle of item i: the first at the top, then clockwise. */
    private double angle(int i) {
        return -Math.PI / 2 + i * 2 * Math.PI / items.size();
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        float w = ui.width();
        float h = ui.height();
        ui.rect(0, 0, w, h, 0f, ui.color("wheel_dim"));
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = ui.num(L + "radius");
        float center = ui.num(L + "center");

        if (items.isEmpty()) {
            String text = Ui.tr("skirmish.binds.wheel.empty");
            float tw = ui.textWidth("wheel_center", text);
            ui.text("wheel_center", text, cx - tw / 2f, cy - 40);
            float bw = openBinds.preferredWidth(ui);
            float bh = ui.num(L + "button_height");
            openBinds.bounds(cx - bw / 2f, cy, bw, bh);
            widget(ui, openBinds, mx, my);
            return;
        }

        // Which item the mouse points at: the nearest by angle, once out of the middle.
        double dx = mx - cx;
        double dy = my - cy;
        hovered = -1;
        if (dx * dx + dy * dy > ui.num(L + "dead") * ui.num(L + "dead")) {
            double a = Math.atan2(dy, dx);
            double best = Double.MAX_VALUE;
            for (int i = 0; i < items.size(); i++) {
                double d = Math.abs(Math.atan2(Math.sin(a - angle(i)), Math.cos(a - angle(i))));
                if (d < best) {
                    best = d;
                    hovered = i;
                }
            }
        }

        // Ring, a pointer towards the chosen item, the centre with what will be sent.
        ui.ring(cx, cy, radius * 2f, ui.num(L + "ring"), ui.color("stroke_10"));
        if (hovered >= 0) {
            double a = angle(hovered);
            ui.line(cx + (float) Math.cos(a) * center / 2f, cy + (float) Math.sin(a) * center / 2f,
                    cx + (float) Math.cos(a) * (radius - 18), cy + (float) Math.sin(a) * (radius - 18), 2f, ui.color("accent"));
        }
        ui.circle(cx, cy, center, ui.color("panel"));
        ui.ring(cx, cy, center, 1.5f, ui.color(hovered >= 0 ? "accent" : "stroke_10"));
        String label = hovered >= 0 ? Ui.tr(items.get(hovered).send().get() ? "skirmish.binds.wheel.send" : "skirmish.binds.wheel.to_chat")
                : Ui.tr("skirmish.binds.wheel.title");
        List<String> lines = ui.wrap("wheel_center", label, center - 16);
        float lh = ui.lineHeight("wheel_center");
        float ty = cy - lines.size() * lh / 2f;
        for (String line : lines) {
            float tw = ui.textWidth("wheel_center", line);
            ui.text("wheel_center", line, cx - tw / 2f, ty, ui.color(hovered >= 0 ? "white" : "text_2"));
            ty += lh;
        }

        float ph = ui.num(L + "pill_h");
        float pad = ui.num(L + "pill_pad");
        for (int i = 0; i < items.size(); i++) {
            CommandBindsModule.Slot s = items.get(i);
            double a = angle(i);
            float px = cx + (float) Math.cos(a) * radius;
            float py = cy + (float) Math.sin(a) * radius;
            String text = ui.ellipsize("wheel_item", s.command().get().strip(), ui.num(L + "pill_max"));
            String num = Integer.toString(s.index() + 1);
            float numW = ui.textWidth("wheel_num", num);
            float pw = pad * 2 + numW + 8 + ui.textWidth("wheel_item", text);
            boolean on = i == hovered;
            float x0 = px - pw / 2f;
            float y0 = py - ph / 2f;
            ui.box(x0, y0, pw, ph, ph / 2f, ui.color(on ? "accent" : "panel"), ui.color(on ? "accent" : "stroke_10"));
            ui.textCentered("wheel_num", num, x0 + pad, y0, ph, ui.color(on ? "white" : "text_3"));
            ui.textCentered("wheel_item", text, x0 + pad + numW + 8, y0, ph, ui.color(on ? "white" : "text"));
        }

        String hint = Ui.tr("skirmish.binds.wheel.hint");
        float hw = ui.textWidth("wheel_hint", hint) + 24;
        float hh = ui.lineHeight("wheel_hint") + 10;
        float hy = cy + radius + ph / 2f + 16;
        ui.box(cx - hw / 2f, hy, hw, hh, hh / 2f, ui.color("panel"), ui.color("stroke_10"));
        ui.textCentered("wheel_hint", hint, cx - hw / 2f + 12, hy, hh);
    }

    /** Sends the chosen command (if one is chosen) and closes. */
    private void choose() {
        if (done) {
            return;
        }
        done = true;
        CommandBindsModule.Slot s = hovered >= 0 && hovered < items.size() ? items.get(hovered) : null;
        minecraft.setScreen(null);
        if (s != null) {
            module.run(minecraft, s);
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        if (CommandBindsModule.WHEEL != null && CommandBindsModule.WHEEL.matches(event)) {
            choose();
            return true;
        }
        return super.keyReleased(event);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (CommandBindsModule.WHEEL != null && CommandBindsModule.WHEEL.matchesMouse(event)) {
            choose();
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    protected boolean onBackgroundClick(double mx, double my, int button) {
        if (button == 0 && !items.isEmpty()) {
            choose();
            return true;
        }
        return false;
    }
}
