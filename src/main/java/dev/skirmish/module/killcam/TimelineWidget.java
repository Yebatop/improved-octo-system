package dev.skirmish.module.killcam;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Scrub bar: click or drag to seek. Marks hits (red), crits (yellow), totems (green) and my death (white). */
final class TimelineWidget extends AbstractWidget {
    private static final int BACKGROUND = 0xC0101010;
    private static final int FILLED = 0xFF4A90D9;
    private static final int HIT = 0xFFE04040;
    private static final int CRIT = 0xFFFFD040;
    private static final int TOTEM = 0xFF40E060;
    private static final int DEATH = 0xFFFFFFFF;
    private static final int KNOB = 0xFFFFFFFF;

    private final ReplaySession session;

    TimelineWidget(ReplaySession session, int x, int y, int width, int height) {
        super(x, y, width, height, Component.translatable("skirmish.killcam.timeline"));
        this.session = session;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + width;
        int y1 = y0 + height;
        graphics.fill(x0, y0, x1, y1, BACKGROUND);
        int filled = x0 + (int) Math.round(session.progress() * width);
        graphics.fill(x0, y0 + height / 2 - 1, filled, y0 + height / 2 + 1, FILLED);

        ReplayBuffer buffer = session.buffer;
        long span = Math.max(1, session.endTick - session.startTick);
        for (int i = 0; i < buffer.eventCount(); i++) {
            long tick = buffer.eventTick(i);
            if (tick < session.startTick || tick > session.endTick) {
                continue;
            }
            int color = switch (buffer.eventType(i)) {
                case ReplayBuffer.EVENT_HIT -> HIT;
                case ReplayBuffer.EVENT_CRIT, ReplayBuffer.EVENT_MAGIC_CRIT -> CRIT;
                case ReplayBuffer.EVENT_TOTEM -> TOTEM;
                default -> 0;
            };
            if (color == 0) {
                continue;
            }
            int x = x0 + (int) ((tick - session.startTick) * width / span);
            boolean onMe = buffer.eventA(i) == session.victimTrack;
            graphics.fill(x, onMe ? y0 + height / 2 : y0, x + 1, onMe ? y1 : y0 + height / 2, color);
        }
        if (session.deathTick >= session.startTick && session.deathTick <= session.endTick) {
            int x = x0 + (int) ((session.deathTick - session.startTick) * width / span);
            graphics.fill(x - 1, y0 - 2, x + 1, y1 + 2, DEATH);
        }
        graphics.fill(filled - 1, y0 - 1, filled + 2, y1 + 1, KNOB);
        if (isHovered()) {
            double at = Math.max(0, Math.min(1, (mouseX - x0) / (double) width)) * session.duration();
            graphics.setTooltipForNextFrame(Component.literal(String.format(Locale.ROOT, "%.2f s", at)), mouseX, mouseY);
        }
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        seek(event.x(), true);
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        seek(event.x(), false);
    }

    @Override
    public void onRelease(MouseButtonEvent event) {
        seek(event.x(), true);
    }

    private void seek(double mouseX, boolean log) {
        session.seekProgress((mouseX - getX()) / width, log);
    }

    @Override
    public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }
}
