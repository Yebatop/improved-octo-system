package dev.skirmish.studio;

import dev.skirmish.fx.FxColor;
import dev.skirmish.fx.FxSounds;
import dev.skirmish.gui.Texts;
import dev.skirmish.module.Module;
import dev.skirmish.module.killfx.KillFxModule;
import dev.skirmish.module.playerfx.PlayerFxModule;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.setting.Setting;
import dev.skirmish.ui.Anim;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.Dropdown;
import dev.skirmish.ui.widget.Segmented;
import dev.skirmish.ui.widget.Slider;
import dev.skirmish.ui.widget.Toggle;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import dev.skirmish.ui.widget.WindowFrame;
import net.fabricmc.fabric.api.client.rendering.v1.SpecialGuiElementRegistry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * «Студия»: my character and a mannequin in 3D with every Player FX and Kill FX setting applied live. Drag to turn
 * the camera, wheel to zoom; pick a pose and a backdrop; hit, crit, kill, streak and totem buttons play the effects
 * and sounds exactly as in the game. On the right, the settings by topic.
 */
public final class StudioScreen extends UiScreen {
    private static final String L = "layout.studio.";
    private static final String M = "layout.menu.";

    private enum Tab {
        TRAIL, AURA, HIT, KILL, TOTEM, HAND, CAPE
    }

    private static Tab tab = Tab.TRAIL;
    private static final StudioStage STAGE = new StudioStage();

    private final WindowFrame frame = new WindowFrame("studio", L);
    private final Segmented poses = new Segmented(Segmented.Spec.MENU,
            () -> List.of(Ui.tr("skirmish.studio.pose.stand"), Ui.tr("skirmish.studio.pose.run"), Ui.tr("skirmish.studio.pose.attack"),
                    Ui.tr("skirmish.studio.pose.jump"), Ui.tr("skirmish.studio.pose.crouch"), Ui.tr("skirmish.studio.pose.fly")),
            () -> STAGE.pose.ordinal(), i -> {
                STAGE.pose = StudioStage.Pose3.values()[i];
                FxSounds.play(FxSounds.of("studio_whoosh"), 1f, 0.4f);
            });
    private final Dropdown scenes = new Dropdown(
            () -> List.of(Ui.tr("skirmish.studio.scene.arena"), Ui.tr("skirmish.studio.scene.night"), Ui.tr("skirmish.studio.scene.sunset"),
                    Ui.tr("skirmish.studio.scene.void"), Ui.tr("skirmish.studio.scene.snow"), Ui.tr("skirmish.studio.scene.meadow")),
            () -> STAGE.scene.ordinal(), i -> STAGE.scene = StudioStage.Scene.values()[i]);
    private final Button hit = action("hit", () -> STAGE.hit(false));
    private final Button crit = action("crit", () -> STAGE.hit(true));
    private final Button kill = action("kill", STAGE::kill);
    private final Button streak = action("streak", STAGE::streakOfThree);
    private final Button totem = action("totem", STAGE::totem);
    private final Button view = new Button(() -> Ui.tr("skirmish.studio.view"), false, this::resetView).layout(M + "small_");
    private static boolean slow;
    private final Button slowMo = new Button(() -> Ui.tr("skirmish.studio.slow"), false, () -> slow = !slow)
            .layout(M + "small_").selected(() -> slow);
    private final Button back = new Button(() -> Ui.tr("skirmish.studio.show_back"), false, () -> targetYaw = 180f).layout(M + "small_");
    private final Button done = new Button(() -> Ui.tr("skirmish.menu.done"), true, this::onClose);
    private final List<TabChip> tabs = new ArrayList<>();
    private final Map<Setting<?>, Widget> controls = new IdentityHashMap<>();

    private long lastNanos = -1;
    private boolean dragging;
    private float targetYaw = Float.NaN;
    private float stageX;
    private float stageY;
    private float stageW;
    private float stageH;
    private float scroll;
    private float maxScroll;

    /** Registers the scene renderer with the GUI (once, at start). */
    public static void install() {
        SpecialGuiElementRegistry.register(context -> new StudioSceneRenderer(context.vertexConsumers()));
    }

    public StudioScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.studio.title"), parent);
        for (Tab t : Tab.values()) {
            tabs.add(new TabChip(t));
        }
    }

    private static Button action(String key, Runnable run) {
        return new Button(() -> Ui.tr("skirmish.studio.action." + key), false, run).layout(M + "small_");
    }

    private void resetView() {
        targetYaw = 0f;
        STAGE.pitch = 10f;
        STAGE.zoom = 1f;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- drawing ----

    @Override
    protected void draw(Ui ui, double mx, double my) {
        long now = System.nanoTime();
        float dt = lastNanos < 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        if (!Float.isNaN(targetYaw)) {
            float d = net.minecraft.util.Mth.wrapDegrees(targetYaw - STAGE.yaw);
            STAGE.yaw += d * Math.min(1f, dt * 8f);
            if (Math.abs(d) < 0.5f) {
                STAGE.yaw = targetYaw;
                targetYaw = Float.NaN;
            }
        }
        STAGE.update(slow ? dt * 0.25f : dt);

        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("backdrop"));
        frame.layout(ui);
        widget(ui, frame.mover, mx, my);
        float w = frame.w();
        float h = frame.h();
        float ox = frame.x();
        float oy = frame.y() + Math.round((1f - appearProgress()) * ui.num("layout.appear_offset"));
        ui.box(ox, oy, w, h, ui.theme().radius("window"), ui.color("window"), ui.color("stroke_07"));
        float stroke = ui.num("stroke.width");
        float pad = ui.num(L + "pad");
        float x = ox + stroke + pad;
        float y = oy + stroke + pad;
        float cw = w - (stroke + pad) * 2;
        float ch = h - (stroke + pad) * 2;

        ui.text("menu_title", Ui.tr("skirmish.studio.title"), x, y);
        float ty = y + ui.lineHeight("menu_title") + ui.num(M + "header_text_gap");
        ui.text("menu_desc", ui.ellipsize("menu_desc", Ui.tr("skirmish.studio.subtitle"), cw * ui.num(L + "stage_share")), x, ty);
        float top = ty + ui.lineHeight("menu_desc") + ui.num(L + "gap");

        stageW = Math.round(cw * ui.num(L + "stage_share"));
        stageX = x;
        stageY = top;
        stageH = y + ch - top;
        drawStage(ui, mx, my);
        drawPanel(ui, x + stageW + ui.num(L + "gap"), top, cw - stageW - ui.num(L + "gap"), y + ch - top, mx, my);
        widget(ui, frame.grip, mx, my);
    }

    private void drawStage(Ui ui, double mx, double my) {
        float r = ui.theme().radius("panel");
        int[] c = StudioStage.colors(STAGE.scene);
        // Backdrop: a vertical gradient in bands, stars at night.
        pushClip(ui, stageX, stageY, stageX + stageW, stageY + stageH);
        int bands = 40;
        for (int i = 0; i < bands; i++) {
            float y0 = stageY + stageH * i / bands;
            ui.rect(stageX, y0, stageW, stageH / bands + 1, 0f, FxColor.lerp(c[0], c[1], i / (float) (bands - 1)));
        }
        if (STAGE.scene == StudioStage.Scene.NIGHT || STAGE.scene == StudioStage.Scene.VOID) {
            var rnd = net.minecraft.util.RandomSource.create(42);
            for (int i = 0; i < 70; i++) {
                float sx = stageX + rnd.nextFloat() * stageW;
                float sy = stageY + rnd.nextFloat() * stageH * 0.6f;
                float tw = 0.5f + 0.5f * (float) Math.sin(STAGE.clock * (1 + rnd.nextFloat() * 2) + i);
                ui.circle(sx, sy, 1.2f + rnd.nextFloat() * 1.3f, FxColor.alpha(0xFFFFFFFF, 0.25f + 0.5f * tw));
            }
        }
        popClip(ui);
        ui.cornerMask(stageX, stageY, stageW, stageH, r, ui.color("window"));

        // The 3D scene.
        float ds = Ui.designScale();
        int gx0 = Math.round(stageX * ds);
        int gy0 = Math.round(stageY * ds);
        int gx1 = Math.round((stageX + stageW) * ds);
        int gy1 = Math.round((stageY + stageH) * ds);
        float scale = sceneScale();
        float pt = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        ui.graphics().guiRenderState.submitPicturesInPictureState(new StudioSceneState(STAGE, pt, gx0, gy0, gx1, gy1, scale,
                ui.graphics().scissorStack.peek()));

        // The mannequin's health over its head.
        if (!STAGE.dummyGone()) {
            float[] head = project(StudioStage.DUMMY_X, 2.25, 0);
            float bw = ui.num(L + "hp_w");
            float bh = ui.num(L + "hp_h");
            float frac = STAGE.dummyHp / StudioStage.MAX_HP;
            float hx = head[0] - bw / 2f;
            float hy = head[1] - bh;
            ui.rect(hx - 2, hy - 2, bw + 4, bh + 4, (bh + 4) / 2f, ui.color("panel"));
            ui.rect(hx, hy, bw, bh, bh / 2f, ui.color("fill_06"));
            if (frac > 0) {
                ui.rect(hx, hy, Math.max(bh, bw * frac), bh, bh / 2f, Anim.lerpColor(ui.color("bad"), ui.color("good"), frac));
            }
            String hp = Ui.decimal(STAGE.dummyHp / 2f, 1);
            float lh = ui.lineHeight("studio_hp");
            float heart = lh * 0.7f;
            float tw = ui.textWidth("studio_hp", hp) + 3 + heart;
            float tx = head[0] - tw / 2f;
            ui.rect(tx - 5, hy - lh - 4, tw + 10, lh + 2, (lh + 2) / 2f, ui.color("panel"));
            ui.text("studio_hp", hp, tx, hy - lh - 3);
            heart(ui, tx + tw - heart, hy - lh - 3 + (lh - heart) / 2f, heart, ui.color("bad"));
        }
        if (STAGE.streak > 1 && STAGE.clock - STAGE.lastKillAt < 2.5f) {
            String s = Ui.tr("skirmish.studio.streak", STAGE.streak);
            float sw = ui.textWidth("studio_streak", s);
            ui.text("studio_streak", s, stageX + (stageW - sw) / 2f, stageY + stageH * 0.16f, ui.color("warn"));
        }

        // Controls over the stage.
        float g = ui.num(L + "gap");
        float sh = poses.preferredHeight(ui);
        poses.bounds(stageX + g, stageY + g, Math.min(poses.preferredWidth(ui), stageW * 0.66f), sh);
        plate(ui, poses);
        widget(ui, poses, mx, my);
        float dw = ui.num(L + "scene_w");
        scenes.bounds(stageX + stageW - g - dw, stageY + g, dw, scenes.preferredHeight(ui));
        plate(ui, scenes);
        widget(ui, scenes, mx, my);

        float bh = ui.num(M + "small_button_height");
        float bx = stageX + g;
        float by = stageY + stageH - g - bh;
        for (Button b : new Button[]{hit, crit, kill, streak, totem}) {
            float bw = b.preferredWidth(ui);
            b.bounds(bx, by, bw, bh);
            plate(ui, b);
            widget(ui, b, mx, my);
            bx += bw + g * 0.6f;
        }
        float vw = view.preferredWidth(ui);
        view.bounds(stageX + stageW - g - vw, by, vw, bh);
        plate(ui, view);
        widget(ui, view, mx, my);
        float sw = slowMo.preferredWidth(ui);
        slowMo.bounds(view.x - g * 0.6f - sw, by, sw, bh);
        plate(ui, slowMo);
        widget(ui, slowMo, mx, my);
        String hint = Ui.tr("skirmish.studio.hint");
        float hw = ui.textWidth("studio_hint", hint);
        float hl = ui.lineHeight("studio_hint");
        float hy = by - hl - 8;
        ui.rect(stageX + stageW - g - hw - 8, hy - 2, hw + 16, hl + 4, (hl + 4) / 2f, ui.color("panel"));
        ui.text("studio_hint", hint, stageX + stageW - g - hw, hy);
    }

    /** A dark backing under a control drawn over the scene, so it reads on light backdrops too. */
    private static void plate(Ui ui, Widget w) {
        ui.rect(w.x, w.y, w.w, w.h, Math.min(w.h / 2f, ui.theme().radius("control")), ui.color("panel"));
    }

    private static void heart(Ui ui, float x, float y, float size, int color) {
        float r = size * 0.3f;
        ui.circle(x + r, y + r, r * 2, color);
        ui.circle(x + size - r, y + r, r * 2, color);
        ui.triangle(x + 0.02f * size, y + r * 1.25f, x + size * 0.98f, y + r * 1.25f, x + size / 2f, y + size, 0.5f, color);
    }

    /** GUI px per block: the scene fits about 3.6 blocks top to bottom at zoom 1. */
    private float sceneScale() {
        return stageH * Ui.designScale() / 3.6f * STAGE.zoom;
    }

    /** Where a scene point lands on screen (design px), matching {@link StudioSceneRenderer}. */
    private float[] project(double px, double py, double pz) {
        Quaternionf q = new Quaternionf().rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(STAGE.pitch))
                .rotateY((float) Math.toRadians(STAGE.yaw));
        Vector3f v = q.transform(new Vector3f((float) px, (float) py - 1f, (float) pz));
        float s = sceneScale() / Ui.designScale();
        return new float[]{stageX + stageW / 2f + v.x * s, stageY + stageH / 2f + v.y * s};
    }

    private void drawPanel(Ui ui, float x, float y, float w, float h, double mx, double my) {
        // Tabs as chips, wrapping.
        float g = ui.num(L + "gap");
        float chipH = ui.num(L + "tab_h");
        float cx = x;
        float cy = y;
        for (TabChip chip : tabs) {
            float cw = chip.preferredWidth(ui);
            if (cx + cw > x + w && cx > x) {
                cx = x;
                cy += chipH + g * 0.6f;
            }
            chip.bounds(cx, cy, cw, chipH);
            widget(ui, chip, mx, my);
            cx += cw + g * 0.6f;
        }
        float listTop = cy + chipH + g;
        float bh = done.preferredHeight(ui);
        float bottom = y + h - bh - g;
        float dwid = done.preferredWidth(ui);
        done.bounds(x + w - dwid, y + h - bh, dwid, bh);
        widget(ui, done, mx, my);
        if (tab == Tab.CAPE) {
            float bw = back.preferredWidth(ui);
            back.bounds(x, y + h - bh + (bh - ui.num(M + "small_button_height")) / 2f, bw, ui.num(M + "small_button_height"));
            widget(ui, back, mx, my);
        }

        ui.hline(x, listTop - g * 0.5f, w, ui.color("stroke"));
        pushClip(ui, x - 2, listTop, x + w + 2, bottom);
        float ry = listTop - scroll + 4;
        String note = note(tab);
        if (note != null) {
            for (String line : ui.wrap("menu_row_desc", note, w)) {
                ui.text("menu_row_desc", line, x, ry, ui.color("accent"));
                ry += ui.lineHeight("menu_row_desc");
            }
            ry += g * 0.6f;
        }
        List<Setting<?>> rows = settings(tab);
        if (rows.isEmpty()) {
            ui.text("menu_row_desc", Ui.tr("skirmish.studio.module_off"), x, ry);
        }
        for (Setting<?> s : rows) {
            if (!s.isVisible()) {
                continue;
            }
            float indent = s.parent() != null ? ui.num(L + "indent") : 0f;
            ry += row(ui, s, x + indent, ry, w - indent, mx, my) + ui.num(M + "rows_gap");
        }
        popClip(ui);
        maxScroll = Math.max(0f, ry + scroll - bottom);
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
    }

    private static @Nullable String note(Tab t) {
        return switch (t) {
            case HAND -> Ui.tr("skirmish.studio.note.hand");
            case CAPE -> Ui.tr("skirmish.studio.note.cape");
            default -> null;
        };
    }

    /** The settings of a tab, from Player FX and Kill FX. */
    private static List<Setting<?>> settings(Tab t) {
        PlayerFxModule p = PlayerFxModule.instance();
        KillFxModule k = KillFxModule.instance();
        List<Setting<?>> out = new ArrayList<>();
        switch (t) {
            case TRAIL -> add(out, p, "trail", "trail_color", "trail_when", "trail_density");
            case AURA -> add(out, p, "aura", "aura_color", "aura_size", "aura_first_person");
            case HIT -> {
                add(out, p, "hit", "hit_color", "crit_color", "hit_size", "hit_mobs");
                add(out, k, "hit_sound", "hit_sound_type", "crit_sound", "hit_volume");
            }
            case KILL -> add(out, k, "particles", "burst", "burst_color", "amount", "lightning", "sound", "sound_type", "volume",
                    "streak_pitch", "banner");
            case TOTEM -> add(out, p, "totem", "totem_sound", "totem_hide_item", "totem_counter", "totem_others");
            case HAND -> add(out, p, "hand", "hand_scale", "hand_x", "hand_y", "hand_z");
            case CAPE -> add(out, p, "cape", "cape_elytra");
        }
        return out;
    }

    private static void add(List<Setting<?>> out, @Nullable Module module, String... ids) {
        if (module == null) {
            return;
        }
        for (String id : ids) {
            for (Setting<?> s : module.settings()) {
                if (s.id().equals(id)) {
                    out.add(s);
                }
            }
        }
    }

    /** One setting: name and hint on the left, its control on the right. Returns the row height. */
    private float row(Ui ui, Setting<?> setting, float x, float y, float w, double mx, double my) {
        Widget control = control(ui, setting, w);
        float controlW;
        float controlH;
        if (setting instanceof NumberSetting) {
            controlW = ui.num(L + "slider_w") + ui.num(M + "row_gap") + ui.num(M + "value_width");
            controlH = ui.num(M + "slider_thumb");
        } else if (control instanceof Toggle) {
            controlW = ui.num(M + "toggle_width");
            controlH = ui.num(M + "toggle_height");
        } else if (control instanceof Segmented seg) {
            controlW = seg.preferredWidth(ui);
            controlH = seg.preferredHeight(ui);
        } else if (control instanceof Dropdown dd) {
            controlW = ui.num(L + "dropdown_w");
            controlH = dd.preferredHeight(ui);
        } else {
            controlW = control.preferredWidth(ui);
            controlH = ui.num(M + "keybind_height");
        }
        float textW = w - controlW - ui.num(M + "row_gap");
        String title = Texts.settingName(setting).getString();
        List<String> titleLines = ui.wrap("menu_row_title", title, textW);
        Component tip = Texts.settingTooltip(setting);
        List<String> desc = tip == null ? List.of() : ui.wrap("studio_row_desc", tip.getString(), textW);
        if (desc.size() > 2) {
            desc = List.of(desc.get(0), ui.ellipsize("studio_row_desc", desc.get(1) + "…", textW));
        }
        float textH = ui.lineHeight("menu_row_title") * titleLines.size() + ui.lineHeight("studio_row_desc") * desc.size();
        float h = Math.max(ui.num(L + "row_min_h"), Math.max(textH, controlH));
        float ty = y + (h - textH) / 2f;
        for (String line : titleLines) {
            ui.text("menu_row_title", line, x, ty);
            ty += ui.lineHeight("menu_row_title");
        }
        for (String line : desc) {
            ui.text("studio_row_desc", line, x, ty);
            ty += ui.lineHeight("studio_row_desc");
        }
        float cx = x + w - controlW;
        float cy = y + (h - controlH) / 2f;
        if (setting instanceof NumberSetting number) {
            control.bounds(cx, cy, ui.num(L + "slider_w"), controlH);
            widget(ui, control, mx, my);
            String value = Texts.number(number).getString();
            ui.textCentered("menu_value", value, x + w - ui.textWidth("menu_value", value), y, h);
        } else {
            control.bounds(cx, cy, controlW, controlH);
            widget(ui, control, mx, my);
        }
        return h;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Widget control(Ui ui, Setting<?> setting, float rowW) {
        Widget existing = controls.get(setting);
        if (existing != null) {
            return existing;
        }
        Widget created;
        if (setting instanceof BoolSetting bool) {
            created = new Toggle(bool::get, bool::set);
        } else if (setting instanceof NumberSetting number) {
            created = new Slider(number, () -> { });
        } else if (setting instanceof EnumSetting e) {
            created = enumControl(ui, e, rowW);
        } else {
            created = new Button(() -> "—", false, () -> { });
        }
        controls.put(setting, created);
        return created;
    }

    private <E extends Enum<E>> Widget enumControl(Ui ui, EnumSetting<E> setting, float rowW) {
        Segmented seg = new Segmented(Segmented.Spec.MENU,
                () -> setting.visibleValues().stream().map(v -> Texts.enumValue(setting, v).getString()).toList(),
                () -> setting.visibleValues().indexOf(setting.get()),
                i -> setting.set(setting.visibleValues().get(i)));
        if (setting.visibleValues().size() <= 3 && seg.preferredWidth(ui) < rowW * 0.5f) {
            return seg;
        }
        return new Dropdown(
                () -> setting.visibleValues().stream().map(v -> Texts.enumValue(setting, v).getString()).toList(),
                () -> setting.visibleValues().indexOf(setting.get()),
                i -> setting.set(setting.visibleValues().get(i)));
    }

    private final class TabChip extends Widget {
        private final Tab value;

        TabChip(Tab value) {
            this.value = value;
        }

        private String label() {
            return Ui.tr("skirmish.studio.tab." + value.name().toLowerCase(java.util.Locale.ROOT));
        }

        @Override
        public float preferredWidth(Ui ui) {
            return ui.textWidth("studio_tab", label()) + ui.num(L + "tab_pad") * 2;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            boolean on = tab == value;
            ui.rect(x, y, w, h, h / 2f, on ? ui.color("accent") : Anim.lerpColor(ui.color("fill_04"), ui.color("fill_08"), hovered()));
            ui.textCentered("studio_tab", label(), x + ui.num(L + "tab_pad"), y, h, ui.color(on ? "white" : "text_2"));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                tab = value;
                scroll = 0f;
                return true;
            }
            return false;
        }
    }

    // ---- input ----

    private boolean onStage(double mx, double my) {
        return mx >= stageX && mx <= stageX + stageW && my >= stageY && my <= stageY + stageH;
    }

    @Override
    protected boolean onBackgroundClick(double mx, double my, int button) {
        if (button == 0 && onStage(mx, my)) {
            dragging = true;
            targetYaw = Float.NaN;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (super.mouseDragged(event, dx, dy)) {
            return true;
        }
        if (dragging) {
            STAGE.yaw += (float) Ui.toDesign(dx) * 0.6f;
            STAGE.pitch = Math.max(-10f, Math.min(50f, STAGE.pitch + (float) Ui.toDesign(dy) * 0.4f));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        return super.mouseReleased(event);
    }

    @Override
    protected boolean onScroll(double x, double y, double scrollX, double scrollY) {
        double mx = Ui.toDesign(x);
        double my = Ui.toDesign(y);
        if (onStage(mx, my)) {
            STAGE.zoom = Math.max(0.6f, Math.min(1.9f, STAGE.zoom * (scrollY > 0 ? 1.1f : 1 / 1.1f)));
            return true;
        }
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) scrollY * 36f));
        return true;
    }
}
