package dev.skirmish.hud;

import dev.skirmish.ui.Theme;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.UiScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * HUD edit mode: every enabled element is shown (with sample data when there is nothing live), outlined, and can be
 * dragged. Edges snap to the screen margin, the screen center and other elements; double-click resets one element.
 * The handle on each frame's bottom-right corner scales the element (top-left corner stays put); double-click on
 * the handle returns it to scale 1.
 */
public final class HudEditScreen extends UiScreen {
    /** One element this frame: top-left, scaled size and scale. */
    private record Rect(HudBlock block, float x, float y, float w, float h, float scale) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }

        /** Unscaled size. */
        float baseW() {
            return w / scale;
        }

        float baseH() {
            return h / scale;
        }
    }

    private final Button resetAll;
    private final Button done;
    private List<Rect> rects = List.of();
    private @Nullable HudBlock dragging;
    private float grabX;
    private float grabY;
    private float dragX;
    private float dragY;
    private float guideX = Float.NaN;
    private float guideY = Float.NaN;
    private @Nullable HudBlock resizing;
    private float resizeGrabX;
    private float resizeGrabY;
    private float resizeScale = 1f;
    private @Nullable HudBlock lastClicked;
    private boolean lastClickOnHandle;
    private long lastClickMs;

    public HudEditScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.hud.edit.title"), parent);
        this.resetAll = new Button(() -> Ui.tr("skirmish.hud.edit.reset"), false, () -> Hud.get().resetAll());
        this.done = new Button(() -> Ui.tr("skirmish.menu.done"), true, this::onClose);
    }

    @Override
    protected void init() {
        super.init();
        Hud.get().setEditing(true);
    }

    @Override
    protected void onClosing() {
        Hud.get().save();
    }

    @Override
    public void removed() {
        Hud.get().setEditing(false);
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        float sw = ui.width();
        float sh = ui.height();
        ui.rect(0, 0, sw, sh, 0, ui.color("hud_edit_backdrop"));

        List<Rect> frame = new ArrayList<>();
        for (HudBlock block : Hud.get().blocks()) {
            if (!block.enabled()) {
                continue;
            }
            block.update(true);
            float scale = block == resizing ? resizeScale : Hud.get().scale(block);
            float w = block.width(ui, true) * scale;
            float h = block.height(ui, true) * scale;
            float[] at = block == dragging || block == resizing ? new float[]{dragX, dragY} : Hud.get().position(block, w, h, sw, sh);
            frame.add(new Rect(block, at[0], at[1], w, h, scale));
        }
        rects = frame;

        if (!Float.isNaN(guideX)) {
            ui.rect(guideX, 0, ui.num("stroke.width"), sh, 0, ui.color("accent"));
        }
        if (!Float.isNaN(guideY)) {
            ui.rect(0, guideY, sw, ui.num("stroke.width"), 0, ui.color("accent"));
        }

        boolean idle = dragging == null && resizing == null;
        Rect handleHover = idle ? handleAt(ui.theme(), mx, my) : null;
        Rect hovered = idle ? handleHover != null ? handleHover : topAt(mx, my) : null;
        float pad = ui.num("layout.hud.edit_pad");
        float handle = ui.num("layout.hud.edit_handle");
        for (Rect r : rects) {
            Hud.draw(ui, r.block(), r.x(), r.y(), r.scale(), true);
            boolean active = r.block() == dragging || r.block() == resizing || r == hovered;
            float radius = ui.theme().radius("panel") + pad;
            ui.rect(r.x() - pad, r.y() - pad, r.w() + pad * 2, r.h() + pad * 2, radius, ui.color(active ? "accent_16" : "accent_10"));
            ui.border(r.x() - pad, r.y() - pad, r.w() + pad * 2, r.h() + pad * 2, radius, ui.num("stroke.width"),
                    ui.color(active ? "accent" : "accent_60"));
            // Scale handle: a knob centered on the frame's bottom-right corner.
            boolean handleActive = r.block() == resizing || r == handleHover;
            float[] c = handleCenter(ui.theme(), r);
            ui.box(c[0] - handle / 2f, c[1] - handle / 2f, handle, handle, ui.theme().radius("hud_handle"),
                    ui.color(handleActive ? "accent" : "panel"), ui.color(active ? "accent" : "accent_60"));
            if (active) {
                String name = Ui.tr(r.block().nameKey());
                if (r.block() == resizing || r.scale() != 1f) {
                    name = Ui.tr("skirmish.hud.edit.scaled", name, Math.round(r.scale() * 100f));
                }
                float lh = ui.lineHeight("hud_edit_label");
                float ly = r.y() - pad - lh - ui.num("layout.hud.edit_label_gap");
                if (ly < 0) {
                    ly = r.y() + r.h() + pad + ui.num("layout.hud.edit_label_gap");
                }
                ui.text("hud_edit_label", name, r.x() - pad, ly);
            }
        }

        // Hint pill at the top and the buttons at the bottom center.
        String hint = Ui.tr("skirmish.hud.edit.hint");
        float hp = ui.num("layout.hud.rec_pad_x");
        float hh = ui.lineHeight("hud_edit_hint") + ui.num("layout.hud.rec_pad_y") * 2 + ui.num("stroke.width") * 2;
        float hw = ui.textWidth("hud_edit_hint", hint) + hp * 2 + ui.num("stroke.width") * 2;
        float hy = sh - ui.num("layout.screen_edge") - done.preferredHeight(ui) - ui.num("layout.hud.edit_gap") - hh;
        HudStyle.pill(ui, (sw - hw) / 2f, hy, hw, hh);
        ui.textCentered("hud_edit_hint", hint, (sw - hw) / 2f + hp + ui.num("stroke.width"), hy, hh);

        float bh = done.preferredHeight(ui);
        float gap = ui.num("layout.menu.footer_gap");
        float dw = done.preferredWidth(ui);
        float rw = resetAll.preferredWidth(ui);
        float bx = (sw - dw - rw - gap) / 2f;
        float by = sh - ui.num("layout.screen_edge") - bh;
        resetAll.bounds(bx, by, rw, bh);
        done.bounds(bx + rw + gap, by, dw, bh);
        ui.rect(bx, by, rw, bh, ui.theme().radius("button"), ui.color("panel"));
        widget(ui, resetAll, mx, my);
        widget(ui, done, mx, my);
        if (handleHover != null || resizing != null) {
            ui.graphics().requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.RESIZE_ALL);
        }
    }

    private static float[] handleCenter(Theme t, Rect r) {
        float pad = t.num("layout.hud.edit_pad");
        return new float[]{r.x() + r.w() + pad, r.y() + r.h() + pad};
    }

    /** Topmost element whose scale handle is under the pointer (the hit area is larger than the drawn knob). */
    private @Nullable Rect handleAt(Theme t, double mx, double my) {
        float half = t.num("layout.hud.edit_handle_hit") / 2f;
        for (int i = rects.size() - 1; i >= 0; i--) {
            float[] c = handleCenter(t, rects.get(i));
            if (Math.abs(mx - c[0]) <= half && Math.abs(my - c[1]) <= half) {
                return rects.get(i);
            }
        }
        return null;
    }

    private @Nullable Rect topAt(double mx, double my) {
        for (int i = rects.size() - 1; i >= 0; i--) {
            if (rects.get(i).contains(mx, my)) {
                return rects.get(i);
            }
        }
        return null;
    }

    @Override
    protected boolean onBackgroundClick(double mx, double my, int button) {
        if (button != 0) {
            return false;
        }
        Rect handleRect = handleAt(Theme.get(), mx, my);
        Rect r = handleRect != null ? handleRect : topAt(mx, my);
        if (r == null) {
            return false;
        }
        boolean onHandle = handleRect != null;
        long now = Util.getMillis();
        if (r.block() == lastClicked && onHandle == lastClickOnHandle && now - lastClickMs < 350) {
            if (onHandle) {
                resetScale(r);
            } else {
                Hud.get().resetPlacement(r.block());
            }
            lastClicked = null;
            return true;
        }
        lastClicked = r.block();
        lastClickOnHandle = onHandle;
        lastClickMs = now;
        if (onHandle) {
            resizing = r.block();
            resizeScale = r.scale();
            // Offset of the pointer from the element's scaled corner, so the corner does not jump to the pointer.
            resizeGrabX = (float) mx - (r.x() + r.w());
            resizeGrabY = (float) my - (r.y() + r.h());
            dragX = r.x();
            dragY = r.y();
            return true;
        }
        dragging = r.block();
        grabX = (float) mx - r.x();
        grabY = (float) my - r.y();
        dragX = r.x();
        dragY = r.y();
        return true;
    }

    private @Nullable Rect rect(HudBlock block) {
        for (Rect r : rects) {
            if (r.block() == block) {
                return r;
            }
        }
        return null;
    }

    /** Double-click on the handle: back to scale 1 around the same anchor (a default-placed element stays default). */
    private void resetScale(Rect r) {
        Hud hud = Hud.get();
        if (hud.hasPlacement(r.block())) {
            hud.setPlacement(r.block(), hud.placement(r.block()).withScale(1f));
        }
    }

    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
        if (resizing != null) {
            Rect r = rect(resizing);
            if (r != null) {
                float sw = (float) Ui.toDesign(width);
                float sh = (float) Ui.toDesign(height);
                float cornerX = (float) Ui.toDesign(event.x()) - resizeGrabX - dragX;
                float cornerY = (float) Ui.toDesign(event.y()) - resizeGrabY - dragY;
                // Never grows past the screen edge from the fixed top-left corner.
                float fit = Math.min((sw - dragX) / r.baseW(), (sh - dragY) / r.baseH());
                resizeScale = Placement.scaleForCorner(cornerX, cornerY, r.baseW(), r.baseH(), fit);
            }
            return true;
        }
        if (dragging == null) {
            return super.mouseDragged(event, dx, dy);
        }
        Rect r = rect(dragging);
        if (r == null) {
            return true;
        }
        float sw = (float) Ui.toDesign(width);
        float sh = (float) Ui.toDesign(height);
        float x = (float) Ui.toDesign(event.x()) - grabX;
        float y = (float) Ui.toDesign(event.y()) - grabY;
        float[] snappedX = snap(x, r.w(), sw, true);
        float[] snappedY = snap(y, r.h(), sh, false);
        dragX = Math.round(Math.max(0f, Math.min(sw - r.w(), snappedX[0])));
        dragY = Math.round(Math.max(0f, Math.min(sh - r.h(), snappedY[0])));
        guideX = snappedX[1];
        guideY = snappedY[1];
        return true;
    }

    /** Snaps the start, center or end of a span to the screen margin/center or to other elements; returns {pos, guide}. */
    private float[] snap(float pos, float size, float screen, boolean horizontal) {
        Theme t = Theme.get();
        float threshold = t.num("layout.hud.snap");
        float edge = t.num("layout.screen_edge");
        List<float[]> targets = new ArrayList<>();
        targets.add(new float[]{edge, 0f});
        targets.add(new float[]{screen - edge, 1f});
        targets.add(new float[]{screen / 2f, 0.5f});
        for (Rect other : rects) {
            if (other.block() == dragging) {
                continue;
            }
            float start = horizontal ? other.x() : other.y();
            float len = horizontal ? other.w() : other.h();
            targets.add(new float[]{start, 0f});
            targets.add(new float[]{start + len, 1f});
            targets.add(new float[]{start + len / 2f, 0.5f});
        }
        float best = threshold + 1f;
        float result = pos;
        float guide = Float.NaN;
        for (float[] target : targets) {
            for (float part : new float[]{0f, 0.5f, 1f}) {
                float candidate = target[0] - size * part;
                float d = Math.abs(candidate - pos);
                if (d <= threshold && d < best) {
                    best = d;
                    result = candidate;
                    guide = target[0];
                }
            }
        }
        return new float[]{result, guide};
    }

    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        if (resizing != null) {
            Rect r = rect(resizing);
            if (r != null) {
                float sw = (float) Ui.toDesign(width);
                float sh = (float) Ui.toDesign(height);
                Hud.get().setPlacement(resizing, Placement.at(dragX, dragY, r.baseW() * resizeScale, r.baseH() * resizeScale,
                        sw, sh, resizeScale));
            }
            resizing = null;
            return true;
        }
        if (dragging != null) {
            Rect r = rect(dragging);
            if (r != null) {
                float sw = (float) Ui.toDesign(width);
                float sh = (float) Ui.toDesign(height);
                Hud.get().setPlacement(dragging, Placement.at(dragX, dragY, r.w(), r.h(), sw, sh, r.scale()));
            }
            dragging = null;
            guideX = Float.NaN;
            guideY = Float.NaN;
            return true;
        }
        return super.mouseReleased(event);
    }
}
