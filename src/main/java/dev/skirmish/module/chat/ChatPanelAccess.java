package dev.skirmish.module.chat;

import dev.skirmish.ui.Ui;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Vanilla's chat drawing with one rounded panel behind the lines instead of a black strip per line: the strips'
 * rectangles are collected (vanilla draws them all before any text) and the panel goes down in their place right
 * before the first line of text. Lines fading out on their own do not stretch the panel; the grey «system» bar in
 * front of server lines is left out (other marks, like a mention, stay). Text, scrollbar and hover go to vanilla.
 */
final class ChatPanelAccess implements ChatComponent.ChatGraphicsAccess {
    /** Strips fainter than this share of the brightest one are not under the panel (they are fading out). */
    private static final float FADED = 0.35f;
    private static final int SYSTEM_BAR = 0xD0D0D0;

    private final ChatComponent.ChatGraphicsAccess vanilla;
    private final GuiGraphics graphics;
    private final float opacity;
    private final List<float[]> strips = new ArrayList<>();
    private boolean drawn;

    ChatPanelAccess(ChatComponent.ChatGraphicsAccess vanilla, GuiGraphics graphics, float opacity) {
        this.vanilla = vanilla;
        this.graphics = graphics;
        this.opacity = opacity;
    }

    @Override
    public void updatePose(Consumer<Matrix3x2f> consumer) {
        vanilla.updatePose(consumer);
    }

    @Override
    public void fill(int x0, int y0, int x1, int y1, int color) {
        if ((color & 0xFFFFFF) == 0 && !drawn) {
            // A line's black strip: remember where it is, in screen coordinates.
            Matrix3x2f pose = graphics.pose();
            Vector2f a = pose.transformPosition(x0, Math.min(y0, y1), new Vector2f());
            Vector2f b = pose.transformPosition(x1, Math.max(y0, y1), new Vector2f());
            strips.add(new float[]{a.x, a.y, b.x, b.y, (color >>> 24) / 255f});
            return;
        }
        vanilla.fill(x0, y0, x1, y1, color);
    }

    @Override
    public boolean handleMessage(int y, float alpha, FormattedCharSequence text) {
        if (!drawn) {
            drawn = true;
            drawPanel();
        }
        return vanilla.handleMessage(y, alpha, text);
    }

    @Override
    public void handleTag(int x0, int y0, int x1, int y1, float alpha, GuiMessageTag tag) {
        if (tag.indicatorColor() == SYSTEM_BAR && "System".equals(tag.logTag())) {
            return;
        }
        vanilla.handleTag(x0, y0, x1, y1, alpha, tag);
    }

    @Override
    public void handleTagIcon(int x, int y, boolean hovered, GuiMessageTag tag, GuiMessageTag.Icon icon) {
        vanilla.handleTagIcon(x, y, hovered, tag, icon);
    }

    private void drawPanel() {
        float top = Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        float left = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float brightest = 0f;
        for (float[] s : strips) {
            brightest = Math.max(brightest, s[4]);
        }
        if (brightest <= 0f) {
            return;
        }
        for (float[] s : strips) {
            if (s[4] >= brightest * FADED) {
                left = Math.min(left, s[0]);
                top = Math.min(top, s[1]);
                right = Math.max(right, s[2]);
                bottom = Math.max(bottom, s[3]);
            }
        }
        if (top >= bottom) {
            return;
        }
        // Vanilla's strip darkness (the «text background» option, faded with the lines) times the module's opacity.
        float alpha = Math.min(1f, brightest / 0.5f) * opacity;
        graphics.pose().pushMatrix();
        graphics.pose().identity();
        Ui ui = Ui.begin(graphics);
        try {
            float s = Ui.designScale();
            float pad = ui.num("layout.chat.pad");
            float x = left / s - pad;
            float y = top / s - pad;
            float w = (right - left) / s + pad * 2;
            float h = (bottom - top) / s + pad * 2;
            ui.pushAlpha(alpha);
            ui.box(x, y, w, h, ui.num("layout.chat.radius"), ui.color("chat_bg"), ui.color("chat_stroke"));
            ui.popAlpha();
        } finally {
            ui.end();
            graphics.pose().popMatrix();
        }
    }
}
