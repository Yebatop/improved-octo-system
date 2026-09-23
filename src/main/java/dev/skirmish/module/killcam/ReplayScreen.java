package dev.skirmish.module.killcam;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.gui.Texts;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

/**
 * Replay controls over the live world (no background). The world view itself is produced by the camera mixin;
 * this screen only shows the overlay and turns input into {@link ReplaySession} calls.
 */
final class ReplayScreen extends Screen {
    private static final int TEXT = 0xFFFFFFFF;
    private static final int DIM = 0xFFB0B0B0;
    private static final int PANEL = 0x90000000;
    private static final int RED = 0xFFFF6060;
    private static final int GOLD = 0xFFFFD040;
    private static final int FEED_EVENTS = 5;

    private final ReplaySession session;
    private Button playButton;
    private Button speedButton;
    private Button cameraButton;
    private boolean draggingCamera;
    private final TrackSample hud = new TrackSample();

    ReplayScreen(ReplaySession session) {
        super(Component.translatable("skirmish.killcam.title"));
        this.session = session;
    }

    @Override
    protected void init() {
        int margin = 8;
        int gap = 4;
        int total = Math.min(width - 2 * margin, 460);
        int left = (width - total) / 2;
        int buttonY = height - 46;
        int[] weights = {2, 2, 2, 4, 2};
        int unit = (total - gap * (weights.length - 1)) / 14;
        int x = left;
        addRenderableWidget(Button.builder(Component.translatable("skirmish.killcam.restart"), b -> session.restart())
                .bounds(x, buttonY, unit * weights[0], 20).build());
        x += unit * weights[0] + gap;
        playButton = addRenderableWidget(Button.builder(Component.empty(), b -> session.togglePause())
                .bounds(x, buttonY, unit * weights[1], 20).build());
        x += unit * weights[1] + gap;
        speedButton = addRenderableWidget(Button.builder(Component.empty(), b -> session.changeSpeed(0))
                .bounds(x, buttonY, unit * weights[2], 20).build());
        x += unit * weights[2] + gap;
        cameraButton = addRenderableWidget(Button.builder(Component.empty(), b -> session.setMode(session.mode().next()))
                .bounds(x, buttonY, unit * weights[3], 20).build());
        x += unit * weights[3] + gap;
        addRenderableWidget(Button.builder(Component.translatable("skirmish.killcam.exit"), b -> onClose())
                .bounds(x, buttonY, left + total - x, 20).build());
        addRenderableWidget(new TimelineWidget(session, left, height - 20, total, 12));
        updateLabels();
    }

    private void updateLabels() {
        playButton.setMessage(Component.translatable(session.isPlaying() ? "skirmish.killcam.pause" : "skirmish.killcam.play"));
        speedButton.setMessage(Component.literal(formatSpeed(session.speed())));
        cameraButton.setMessage(Component.translatable("skirmish.killcam.camera",
                Component.translatable("skirmish.module.killcam.setting.default_camera." + session.mode().name().toLowerCase(Locale.ROOT))));
    }

    private static String formatSpeed(double speed) {
        String number = speed == Math.rint(speed) ? String.format(Locale.ROOT, "%.0f", speed) : String.format(Locale.ROOT, "%.2f", speed);
        return number.replaceAll("0$", "") + Texts.unit("x");
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateLabels();
        renderOverlay(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderOverlay(GuiGraphics graphics) {
        Font font = this.font;
        int x = 8;
        int y = 8;
        int line = font.lineHeight + 2;
        String status = String.format(Locale.ROOT, "%.2f / %.2f %s", session.elapsed(), session.duration(), Texts.unit("s"));
        double toDeath = session.deathAt() - session.elapsed();
        Component header = Component.translatable("skirmish.killcam.hud.header", status,
                String.format(Locale.ROOT, "%+.2f", -toDeath), formatSpeed(session.speed()));
        int panelWidth = Math.max(font.width(header), 200) + 8;
        graphics.fill(x - 4, y - 4, x + panelWidth, y + line * 4 + 22, PANEL);
        graphics.drawString(font, header, x, y, TEXT, true);
        y += line;
        String note = session.cameraNote();
        if (!note.isEmpty()) {
            graphics.drawString(font, Component.translatable("skirmish.killcam.camera_note." + note), x, y, GOLD, true);
            y += line;
        }
        y = renderPlayer(graphics, font, x, y, session.killerTrack, "skirmish.killcam.hud.killer", RED);
        y = renderPlayer(graphics, font, x, y, session.victimTrack, "skirmish.killcam.hud.me", TEXT);
        renderFeed(graphics, font, x, y + 2);
        Component help = Component.translatable("skirmish.killcam.help");
        graphics.drawCenteredString(font, help, width / 2, height - 58, DIM);
    }

    private int renderPlayer(GuiGraphics graphics, Font font, int x, int y, int track, String key, int color) {
        if (track == ReplayBuffer.NO_TRACK) {
            if (key.endsWith("killer")) {
                graphics.drawString(font, Component.translatable("skirmish.killcam.hud.no_killer"), x, y, DIM, true);
                return y + font.lineHeight + 2;
            }
            return y;
        }
        String name = session.buffer.name(track);
        Component text;
        if (session.hudSample(track, hud)) {
            String hp = hud.absorption > 0
                    ? String.format(Locale.ROOT, "%.1f (+%.1f)", hud.health, hud.absorption)
                    : String.format(Locale.ROOT, "%.1f", hud.health);
            text = Component.translatable(key, name, hp);
        } else {
            text = Component.translatable(key, name, "-");
        }
        graphics.drawString(font, text, x, y, color, true);
        int itemX = x + font.width(text) + 6;
        for (int slot = 0; slot < ReplayBuffer.EQUIPMENT_SLOTS; slot++) {
            if (session.equipmentNow(track, slot) instanceof ItemStack stack && !stack.isEmpty()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(itemX, y - 3);
                graphics.pose().scale(0.75F, 0.75F);
                graphics.renderItem(stack, 0, 0);
                graphics.pose().popMatrix();
                itemX += 13;
            }
        }
        return y + font.lineHeight + 6;
    }

    private void renderFeed(GuiGraphics graphics, Font font, int x, int y) {
        ReplayBuffer buffer = session.buffer;
        double now = session.startTick + session.elapsed() * 20.0;
        int shown = 0;
        for (int i = buffer.eventCount() - 1; i >= 0 && shown < FEED_EVENTS; i--) {
            long tick = buffer.eventTick(i);
            if (tick > now || tick < session.startTick) {
                continue;
            }
            Component text = describe(buffer, i, (now - tick) / 20.0);
            if (text == null) {
                continue;
            }
            int alpha = shown == 0 ? 0xFF : 0xFF - shown * 0x28;
            graphics.drawString(font, text, x, y + shown * (font.lineHeight + 1), (alpha << 24) | 0xE0E0E0, true);
            shown++;
        }
    }

    private Component describe(ReplayBuffer buffer, int index, double ago) {
        String time = String.format(Locale.ROOT, "-%.1f", ago);
        String a = buffer.name(buffer.eventA(index));
        int b = buffer.eventB(index);
        String other = b == ReplayBuffer.NO_TRACK ? "?" : buffer.name(b);
        Object note = buffer.eventNote(index);
        return switch (buffer.eventType(index)) {
            case ReplayBuffer.EVENT_HIT -> Component.translatable("skirmish.killcam.event.hit", time, other, a,
                    note == null ? "" : note.toString().replace("minecraft:", ""));
            case ReplayBuffer.EVENT_CRIT -> Component.translatable("skirmish.killcam.event.crit", time, a);
            case ReplayBuffer.EVENT_MAGIC_CRIT -> Component.translatable("skirmish.killcam.event.magic_crit", time, a);
            case ReplayBuffer.EVENT_TOTEM -> Component.translatable("skirmish.killcam.event.totem", time, a);
            case ReplayBuffer.EVENT_DEATH -> Component.translatable("skirmish.killcam.event.death", time, a);
            default -> null;
        };
    }

    // ---- input ----

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (SkirmishKeys.KILLCAM_REPLAY.matches(event)) {
            onClose();
            return true;
        }
        int key = event.key();
        boolean free = session.mode() == CameraMode.FREE;
        switch (key) {
            case GLFW.GLFW_KEY_P -> session.togglePause();
            case GLFW.GLFW_KEY_SPACE -> {
                if (free) {
                    return true;
                }
                session.togglePause();
            }
            case GLFW.GLFW_KEY_LEFT -> session.seekSeconds(event.hasShiftDown() ? -0.05 : -1.0);
            case GLFW.GLFW_KEY_RIGHT -> session.seekSeconds(event.hasShiftDown() ? 0.05 : 1.0);
            case GLFW.GLFW_KEY_UP -> session.changeSpeed(1);
            case GLFW.GLFW_KEY_DOWN -> session.changeSpeed(-1);
            case GLFW.GLFW_KEY_C -> session.setMode(session.mode().next());
            case GLFW.GLFW_KEY_1 -> session.setMode(CameraMode.KILLER);
            case GLFW.GLFW_KEY_2 -> session.setMode(CameraMode.ORBIT_KILLER);
            case GLFW.GLFW_KEY_3 -> session.setMode(CameraMode.ORBIT_VICTIM);
            case GLFW.GLFW_KEY_4 -> session.setMode(CameraMode.FREE);
            case GLFW.GLFW_KEY_R -> session.restart();
            default -> {
                return super.keyPressed(event);
            }
        }
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        setFocused(null);
        draggingCamera = true;
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingCamera) {
            session.rotate(dx, dy);
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingCamera = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (vertical != 0) {
            session.scroll(vertical);
        }
        return true;
    }

    @Override
    public void tick() {
        if (ReplaySession.current() != session) {
            session.leaveScreen();
        }
    }

    @Override
    public void onClose() {
        session.stop("exit", ReplaySession.SCREEN_DEATH);
    }

    @Override
    public void removed() {
        session.stop("replay screen removed", ReplaySession.SCREEN_KEEP);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
