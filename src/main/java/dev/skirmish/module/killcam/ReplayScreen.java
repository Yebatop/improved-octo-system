package dev.skirmish.module.killcam;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.gui.Texts;
import dev.skirmish.ui.Icons;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.IconButton;
import dev.skirmish.ui.widget.Segmented;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Replay overlay (mockup «KillCam»): top bar with the «ПОВТОР» badge, killer and weapon, close button; bottom bar
 * with the timeline (hit, totem and death markers), step/play controls, legend, speed and camera switches. The
 * world view itself comes from the camera mixin; drag on the world to look around, wheel to zoom.
 */
final class ReplayScreen extends UiScreen {
    private static final String L = "layout.replay.";

    private final ReplaySession session;
    private final IconButton close;
    private final IconButton back;
    private final IconButton play;
    private final IconButton forward;
    private final Segmented speed;
    private final Segmented camera;
    private final Timeline timeline = new Timeline();
    private final String title;
    private boolean draggingCamera;

    ReplayScreen(ReplaySession session) {
        super(Component.translatable("skirmish.killcam.title"), null);
        this.session = session;
        this.close = new IconButton("fill_06", "fill_10", "button", (ui, b) -> {
            float s = ui.num(L + "close_icon");
            float[] at = b.iconAt(s);
            Icons.close(ui, at[0], at[1], s, 2.2f, ui.color("text"));
        }, this::onClose);
        this.back = new IconButton("fill_06", "fill_10", "control", (ui, b) -> {
            float s = ui.num(L + "control_icon");
            float[] at = b.iconAt(s);
            Icons.back(ui, at[0], at[1], s, 2.2f, ui.color("text"));
        }, () -> session.seekSeconds(-1.0));
        this.forward = new IconButton("fill_06", "fill_10", "control", (ui, b) -> {
            float s = ui.num(L + "control_icon");
            float[] at = b.iconAt(s);
            Icons.forward(ui, at[0], at[1], s, 2.2f, ui.color("text"));
        }, () -> session.seekSeconds(1.0));
        this.play = new IconButton("white", "play_hover", null, (ui, b) -> {
            float s = ui.num(L + "control_icon");
            float[] at = b.iconAt(s);
            if (session.isPlaying()) {
                Icons.pause(ui, at[0], at[1], s, ui.color("page"));
            } else {
                Icons.play(ui, at[0], at[1], s, ui.color("page"));
            }
        }, session::togglePause);
        List<ReplaySpeed> speeds = Arrays.asList(ReplaySpeed.values());
        this.speed = new Segmented(Segmented.Spec.REPLAY,
                () -> speeds.stream().map(s -> Texts.localizeDecimal(trimZeros(s.value)) + "×").toList(),
                () -> speeds.indexOf(ReplaySpeed.nearest(session.speed())),
                i -> session.setSpeed(speeds.get(i).value));
        this.camera = new Segmented(Segmented.Spec.REPLAY,
                () -> session.allowedModes().stream().map(ReplayScreen.this::cameraLabel).toList(),
                () -> session.allowedModes().indexOf(session.mode()),
                i -> session.setMode(session.allowedModes().get(i)));
        this.title = weaponText(session.killerWeapon());
    }

    /** «От убийцы», or «Мои глаза» when the killer camera is my own view (my kills and clips from the library). */
    private String cameraLabel(CameraMode mode) {
        String id = mode.name().toLowerCase(Locale.ROOT);
        if (mode == CameraMode.KILLER && session.killerIsSelf()) {
            id = "killer_self";
        }
        return Ui.tr("skirmish.killcam.camera_short." + id);
    }

    private static String trimZeros(double v) {
        String s = String.format(Locale.ROOT, "%.2f", v);
        return s.replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** "Незеритовый меч, Острота V" (item name and up to two enchantments). */
    private static String weaponText(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add(stack.getHoverName().getString());
        ItemEnchantments enchantments = stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
        int shown = 0;
        for (var entry : enchantments.entrySet()) {
            if (shown++ >= 2) {
                break;
            }
            Holder<Enchantment> holder = entry.getKey();
            parts.add(Enchantment.getFullname(holder, entry.getIntValue()).getString());
        }
        return String.join(", ", parts);
    }

    /** m:ss,d */
    private static String time(double seconds) {
        long tenths = Math.max(0, Math.round(seconds * 10));
        return Texts.localizeDecimal(String.format(Locale.ROOT, "%d:%02d.%d", tenths / 600, (tenths / 10) % 60, tenths % 10));
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        float w = ui.width();
        float h = ui.height();
        float stroke = ui.num("stroke.width");
        ui.rect(0, 0, w, ui.num(L + "top_bar"), 0, ui.color("bar"));
        ui.rect(0, h - ui.num(L + "bottom_bar"), w, ui.num(L + "bottom_bar"), 0, ui.color("bar"));

        // Top bar: badge + "Вас убил NAME · weapon", close button.
        float edge = ui.num(L + "edge_x");
        float top = ui.num(L + "top_y");
        String badge = Ui.tr(session.saved != null ? "skirmish.killcam.badge_saved" : "skirmish.killcam.badge");
        float bpx = ui.num(L + "badge_pad_x");
        float bpy = ui.num(L + "badge_pad_y");
        float dot = ui.num(L + "badge_dot");
        float bgap = ui.num(L + "badge_gap");
        float bh = ui.lineHeight("replay_badge") + bpy * 2;
        float bw = bpx * 2 + dot + bgap + ui.textWidth("replay_badge", badge);
        float rowH = Math.max(bh, ui.lineHeight("replay_title"));
        float by = top + (rowH - bh) / 2f;
        ui.rect(edge, by, bw, bh, bh / 2f, ui.color("rec_16"));
        ui.circle(edge + bpx + dot / 2f, by + bh / 2f, dot, ui.color("rec"));
        ui.text("replay_badge", badge, edge + bpx + dot + bgap, by + bpy);
        float tx = edge + bw + ui.num(L + "top_gap");
        float ty = top + (rowH - ui.lineHeight("replay_title")) / 2f;
        float titleMax = w - edge - ui.num(L + "close_size") - ui.num(L + "close_right");
        if (session.saved != null) {
            savedTitle(ui, session.saved.header(), tx, ty, titleMax);
        } else if (session.killerName.isEmpty()) {
            Component message = session.deathMessage;
            ui.text("replay_title", message == null ? Ui.tr("skirmish.killcam.died") : message.getString(), tx, ty);
        } else {
            float nx = ui.text("replay_title", Ui.tr("skirmish.killcam.killed_by") + " ", tx, ty);
            nx = ui.text("replay_name", session.killerName, nx, ty);
            if (!title.isEmpty()) {
                String rest = " · " + title;
                float maxW = w - edge - ui.num(L + "close_size") - ui.num(L + "close_right") - nx;
                ui.text("replay_title", ui.ellipsize("replay_title", rest, maxW), nx, ty);
            }
        }
        float cs = ui.num(L + "close_size");
        close.bounds(w - ui.num(L + "close_right") - cs, ui.num(L + "close_top"), cs, cs);
        widget(ui, close, mx, my);

        // Camera note / free-camera help under the top bar.
        String note = session.cameraNote();
        float noteY = ui.num(L + "top_bar") + ui.num(L + "note_gap");
        if (!note.isEmpty()) {
            ui.text("replay_note", Ui.tr("skirmish.killcam.camera_note." + note), edge, noteY);
            noteY += ui.lineHeight("replay_note");
        }
        if (session.mode() == CameraMode.FREE) {
            ui.text("replay_note", Ui.tr("skirmish.killcam.help_free"), edge, noteY);
            noteY += ui.lineHeight("replay_note");
        }
        if (session.saved != null && session.saved.elsewhere()) {
            ui.text("replay_note", Ui.tr("skirmish.killcam.saved_note.elsewhere"), edge, noteY);
        }

        // Bottom: timeline row, then controls row.
        float controlsH = Math.max(ui.num(L + "play_size"), speed.preferredHeight(ui));
        float bottomTop = h - ui.num(L + "bottom_edge") - controlsH - ui.num(L + "bottom_gap") - ui.num(L + "timeline_height");
        float timeW = ui.num(L + "time_width");
        float lineH = ui.num(L + "timeline_height");
        ui.textCentered("replay_time", time(session.elapsed()), edge, bottomTop, lineH);
        String end = time(session.duration());
        ui.textCentered("replay_time_end", end, w - edge - ui.textWidth("replay_time_end", end), bottomTop, lineH);
        float trackX = edge + timeW + ui.num(L + "timeline_gap");
        float trackW = w - edge * 2 - (timeW + ui.num(L + "timeline_gap")) * 2;
        timeline.bounds(trackX, bottomTop, trackW, lineH);
        widget(ui, timeline, mx, my);

        float cy = bottomTop + lineH + ui.num(L + "bottom_gap");
        float step = ui.num(L + "step_size");
        float playSize = ui.num(L + "play_size");
        float gap = ui.num(L + "controls_gap");
        float cx = edge;
        back.bounds(cx, cy + (controlsH - step) / 2f, step, step);
        cx += step + gap;
        play.bounds(cx, cy + (controlsH - playSize) / 2f, playSize, playSize);
        cx += playSize + gap;
        forward.bounds(cx, cy + (controlsH - step) / 2f, step, step);
        cx += step + gap;
        widget(ui, back, mx, my);
        widget(ui, play, mx, my);
        widget(ui, forward, mx, my);

        // Legend.
        cx += ui.num(L + "legend_margin");
        cx = legend(ui, cx, cy, controlsH, "marker", false, Ui.tr("skirmish.killcam.legend.hit"));
        cx = legend(ui, cx + ui.num(L + "legend_gap"), cy, controlsH, "warn", true, Ui.tr("skirmish.killcam.legend.totem"));
        legend(ui, cx + ui.num(L + "legend_gap"), cy, controlsH, "rec", false, Ui.tr("skirmish.killcam.legend.death"));

        // Speed and camera switches, right aligned.
        float camW = camera.preferredWidth(ui);
        float spW = speed.preferredWidth(ui);
        float segH = speed.preferredHeight(ui);
        camera.at(w - edge - camW, cy + (controlsH - segH) / 2f);
        speed.at(camera.x - gap - spW, cy + (controlsH - segH) / 2f);
        widget(ui, speed, mx, my);
        widget(ui, camera, mx, my);
    }

    /** Top bar of a saved replay: «Вы убили Notch · меч», «Вас убил Notch · меч» or «Клип · бой с Notch · 24.09 14:05». */
    private void savedTitle(Ui ui, dev.skirmish.module.killcam.library.ReplayHeader header, float tx, float ty, float maxRight) {
        String rest = title.isEmpty() ? "" : " · " + title;
        switch (header.kind) {
            case KILL -> {
                float nx = ui.text("replay_title", Ui.tr("skirmish.killcam.saved_title.kill") + " ", tx, ty);
                nx = ui.text("replay_name", ui.ellipsize("replay_name", header.opponent, Math.max(0, maxRight - nx)), nx, ty);
                ui.text("replay_title", ui.ellipsize("replay_title", rest, Math.max(0, maxRight - nx)), nx, ty);
            }
            case DEATH -> {
                if (session.killerName.isEmpty()) {
                    ui.text("replay_title", Ui.tr("skirmish.killcam.died"), tx, ty);
                } else {
                    float nx = ui.text("replay_title", Ui.tr("skirmish.killcam.killed_by") + " ", tx, ty);
                    nx = ui.text("replay_name", session.killerName, nx, ty);
                    ui.text("replay_title", ui.ellipsize("replay_title", rest, Math.max(0, maxRight - nx)), nx, ty);
                }
            }
            case CLIP -> {
                float nx = ui.text("replay_title", Ui.tr("skirmish.killcam.saved_title.clip"), tx, ty);
                if (!header.opponent.isEmpty()) {
                    nx = ui.text("replay_title", " · " + Ui.tr("skirmish.killcam.saved_title.clip_with") + " ", nx, ty);
                    nx = ui.text("replay_name", ui.ellipsize("replay_name", header.opponent, Math.max(0, maxRight - nx)), nx, ty);
                }
                String date = " · " + ReplayTexts.date(header.createdMs);
                ui.text("replay_title", ui.ellipsize("replay_title", date, Math.max(0, maxRight - nx)), nx, ty);
            }
        }
    }

    private static float legend(Ui ui, float x, float y, float h, String color, boolean diamond, String label) {
        float mark;
        if (diamond) {
            mark = ui.num(L + "legend_totem");
            diamond(ui, x + mark / 2f, y + h / 2f, mark, ui.theme().radius("marker"), ui.color(color));
        } else {
            mark = ui.num(L + "hit_width");
            float mh = ui.num(L + "legend_mark_height");
            ui.rect(x, y + (h - mh) / 2f, mark, mh, ui.theme().radius("marker"), ui.color(color));
        }
        float tx = x + mark + ui.num(L + "legend_item_gap");
        return ui.textCentered("replay_legend", label, tx, y, h);
    }

    /** Square of side {@code size} rotated 45° around its center (CSS transform: rotate(45deg)). */
    private static void diamond(Ui ui, float cx, float cy, float size, float radius, int color) {
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        pose.translate(cx, cy);
        pose.rotate((float) Math.toRadians(45));
        ui.rect(-size / 2f, -size / 2f, size, size, radius, color);
        pose.popMatrix();
    }

    /** Scrub bar with event markers; click or drag to seek. */
    private final class Timeline extends Widget {
        private boolean dragging;

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float track = ui.num(L + "track");
            float cy = y + h / 2f;
            ui.rect(x, cy - track / 2f, w, track, track / 2f, ui.color("track"));
            float progress = (float) session.progress();
            ui.rect(x, cy - track / 2f, w * progress, track, track / 2f, ui.color("accent"));

            ReplayBuffer buffer = session.buffer;
            long span = Math.max(1, session.endTick - session.startTick);
            float hitW = ui.num(L + "hit_width");
            float hitH = ui.num(L + "hit_height");
            float radius = ui.theme().radius("marker");
            List<Float> totems = new ArrayList<>();
            for (int i = 0; i < buffer.eventCount(); i++) {
                long tick = buffer.eventTick(i);
                if (tick < session.startTick || tick > session.endTick) {
                    continue;
                }
                float px = x + (tick - session.startTick) * w / span;
                switch (buffer.eventType(i)) {
                    case ReplayBuffer.EVENT_HIT -> ui.rect(px, cy - hitH / 2f, hitW, hitH, radius, ui.color("marker"));
                    case ReplayBuffer.EVENT_TOTEM -> totems.add(px);
                    default -> {
                    }
                }
            }
            float totem = ui.num(L + "totem");
            for (float px : totems) {
                // Mockup: left = position, margin-left −3 px, then rotated around its center.
                diamond(ui, px - 3f + totem / 2f, cy, totem, ui.theme().radius("totem_marker"), ui.color("warn"));
            }
            if (session.deathTick >= session.startTick && session.deathTick <= session.endTick) {
                float px = x + (session.deathTick - session.startTick) * w / span;
                float dh = ui.num(L + "death_height");
                ui.rect(px, cy - dh / 2f, hitW, dh, radius, ui.color("rec"));
            }
            float thumb = ui.num(L + "thumb");
            ui.circle(x + w * progress, cy, thumb + (dragging ? 2f : hovered() * 2f), ui.color("white"));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button != 0) {
                return false;
            }
            dragging = true;
            session.seekProgress((mx - x) / w, false);
            return true;
        }

        @Override
        public void mouseDragged(double mx, double my, int button) {
            if (dragging) {
                session.seekProgress((mx - x) / w, false);
            }
        }

        @Override
        public void mouseReleased(double mx, double my, int button) {
            if (dragging) {
                session.seekProgress((mx - x) / w, true);
            }
            dragging = false;
        }
    }

    // ---- input ----

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (SkirmishKeys.KILLCAM_REPLAY.matches(event) || event.isEscape()) {
            onClose();
            return true;
        }
        boolean free = session.mode() == CameraMode.FREE;
        switch (event.key()) {
            case GLFW.GLFW_KEY_P -> session.togglePause();
            case GLFW.GLFW_KEY_SPACE -> {
                if (!free) {
                    session.togglePause();
                }
            }
            case GLFW.GLFW_KEY_LEFT -> session.seekSeconds(event.hasShiftDown() ? -0.05 : -1.0);
            case GLFW.GLFW_KEY_RIGHT -> session.seekSeconds(event.hasShiftDown() ? 0.05 : 1.0);
            case GLFW.GLFW_KEY_UP -> session.changeSpeed(1);
            case GLFW.GLFW_KEY_DOWN -> session.changeSpeed(-1);
            case GLFW.GLFW_KEY_C -> {
                List<CameraMode> allowed = session.allowedModes();
                session.setMode(allowed.get((allowed.indexOf(session.mode()) + 1) % allowed.size()));
            }
            case GLFW.GLFW_KEY_1 -> session.setMode(CameraMode.FREE);
            case GLFW.GLFW_KEY_2 -> session.setMode(CameraMode.KILLER);
            case GLFW.GLFW_KEY_3 -> session.setMode(CameraMode.ORBIT);
            case GLFW.GLFW_KEY_R -> session.restart();
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    protected boolean onBackgroundClick(double mx, double my, int button) {
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
        if (session.saved != null) {
            // Saved replays also run while the module is switched off, when its tick does not call this.
            session.tick();
        }
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
