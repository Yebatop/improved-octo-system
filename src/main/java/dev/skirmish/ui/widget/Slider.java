package dev.skirmish.ui.widget;

import dev.skirmish.setting.NumberSetting;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;

/** Track with accent fill and a white thumb; drag or click to set a {@link NumberSetting}. */
public final class Slider extends Widget {
    private final NumberSetting setting;
    private final Runnable changed;
    private final Anim position = new Anim("toggle_ms");
    private boolean dragging;

    public Slider(NumberSetting setting, Runnable changed) {
        this.setting = setting;
        this.changed = changed;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        String l = "layout.menu.";
        float track = ui.num(l + "slider_track");
        float thumb = ui.num(l + "slider_thumb");
        float target = (float) setting.toSlider();
        float t = dragging ? target : position.target(target).value();
        if (dragging) {
            position.snap(target);
        }
        float cy = y + h / 2f;
        ui.rect(x, cy - track / 2f, w, track, track / 2f, ui.color("track"));
        ui.rect(x, cy - track / 2f, w * t, track, track / 2f, ui.color("accent"));
        float grow = hovered() > 0f || dragging ? ui.num(l + "slider_thumb_hover") * (dragging ? 1f : hovered()) : 0f;
        ui.circle(x + w * t, cy, thumb + grow, ui.color("white"));
    }

    @Override
    public boolean contains(double mx, double my) {
        float thumb = dev.skirmish.ui.Theme.get().num("layout.menu.slider_thumb");
        return mx >= x - thumb / 2f && mx < x + w + thumb / 2f && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        dragging = true;
        apply(mx);
        return true;
    }

    @Override
    public void mouseDragged(double mx, double my, int button) {
        if (dragging) {
            apply(mx);
        }
    }

    @Override
    public void mouseReleased(double mx, double my, int button) {
        dragging = false;
    }

    private void apply(double mx) {
        double before = setting.get();
        setting.setFromSlider((mx - x) / w);
        if (setting.get() != before) {
            changed.run();
        }
    }
}
