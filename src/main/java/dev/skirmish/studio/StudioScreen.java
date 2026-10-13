package dev.skirmish.studio;

import dev.skirmish.fx.FxColor;
import dev.skirmish.fx.FxPalette;
import dev.skirmish.fx.FxSounds;
import dev.skirmish.fx.FxStyles;
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
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * «Студия»: a wardrobe for the effects. On the left, my character and a mannequin in 3D with the current effects
 * playing by themselves (hits, kills, totems, a running model for trails and capes). On the right, every style as a
 * card with its own little live scene; pointing at a card (or a colour) shows it on the big scene at once, clicking
 * puts it on. Sounds are buttons that play when picked; sizes and switches are rows under the cards.
 */
public final class StudioScreen extends UiScreen {
    private static final String L = "layout.studio.";
    private static final String M = "layout.menu.";

    private enum Tab {
        TRAIL(StudioStage.Demo.TRAIL), AURA(StudioStage.Demo.AURA), HIT(StudioStage.Demo.HIT), KILL(StudioStage.Demo.KILL),
        TOTEM(StudioStage.Demo.TOTEM), CAPE(StudioStage.Demo.CAPE), HAND(StudioStage.Demo.NONE);

        final StudioStage.Demo demo;

        Tab(StudioStage.Demo demo) {
            this.demo = demo;
        }
    }

    /** A style to pick: what it's called, whether it's worn, how to put it on, how it looks on the big scene. */
    private record Option(String id, String label, BooleanSupplier worn, Runnable wear, Consumer<StudioLook> preview, boolean off) {
    }

    /** What the mouse is over: the kind of effect and the change it makes to the look. */
    private record Hover(StudioStage.Demo demo, Consumer<StudioLook> edit) {
    }

    private static Tab tab = Tab.TRAIL;
    private static boolean slow;
    private static boolean auto = true;
    private static final StudioStage STAGE = new StudioStage(1800);
    /** How many blocks the big scene's height shows at zoom 1. */
    private static final float STAGE_BLOCKS = 4.1f;

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
    private final Button hit = action("hit", () -> STAGE.hit(false, true));
    private final Button crit = action("crit", () -> STAGE.hit(true, true));
    private final Button kill = action("kill", () -> STAGE.kill(true));
    private final Button streak = action("streak", STAGE::streakOfThree);
    private final Button totem = action("totem", () -> STAGE.totem(true));
    private final Button autoPlay = new Button(() -> Ui.tr("skirmish.studio.auto"), false, () -> auto = !auto)
            .layout(M + "small_").selected(() -> auto);
    private final Button slowMo = new Button(() -> Ui.tr("skirmish.studio.slow"), false, () -> slow = !slow)
            .layout(M + "small_").selected(() -> slow);
    private final Button view = new Button(() -> Ui.tr("skirmish.studio.view"), false, this::resetView).layout(M + "small_");
    private final Button done = new Button(() -> Ui.tr("skirmish.menu.done"), true, this::onClose);
    private final Button enable = new Button(() -> Ui.tr("skirmish.studio.enable"), true, this::enableModule).layout(M + "small_");
    private final List<TabChip> tabs = new ArrayList<>();
    private final Map<Setting<?>, Widget> controls = new IdentityHashMap<>();
    private final Map<String, StyleCard> cards = new HashMap<>();
    private final Map<String, Swatch> swatches = new HashMap<>();
    private final Map<String, Chip> chips = new HashMap<>();

    private long lastNanos = -1;
    private float frameDt;
    private boolean dragging;
    private boolean turnedByHand;
    private float targetYaw = Float.NaN;
    private float stageX;
    private float stageY;
    private float stageW;
    private float stageH;
    private float scroll;
    private float maxScroll;
    private float listTop;
    private float listBottom;
    private @Nullable Hover hover;
    private @Nullable Hover nextHover;

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
        targetYaw = STAGE.demo == StudioStage.Demo.CAPE ? 180f : 0f;
        turnedByHand = false;
        STAGE.pitch = 14f;
        STAGE.zoom = 1f;
    }

    private void enableModule() {
        Module m = tab == Tab.KILL ? KillFxModule.instance() : PlayerFxModule.instance();
        if (m != null) {
            m.setEnabled(true);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- frame ----

    @Override
    protected void draw(Ui ui, double mx, double my) {
        long now = System.nanoTime();
        frameDt = lastNanos < 0 ? 0f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;

        // The big scene wears the settings, changed by whatever the mouse was over last frame.
        StudioLook look = StudioLook.current();
        if (hover != null) {
            hover.edit().accept(look);
        }
        STAGE.look = look;
        StudioStage.Demo want = !auto ? StudioStage.Demo.NONE : hover != null ? hover.demo() : tab.demo;
        if (want != STAGE.demo) {
            StudioStage.Demo was = STAGE.demo;
            STAGE.startDemo(want);
            if (want != StudioStage.Demo.NONE) {
                STAGE.pose = StudioStage.poseFor(want, look);
            }
            if (!turnedByHand && (want == StudioStage.Demo.CAPE || was == StudioStage.Demo.CAPE)) {
                targetYaw = want == StudioStage.Demo.CAPE ? 180f : 0f;
            }
        }
        if (!Float.isNaN(targetYaw)) {
            float d = Mth.wrapDegrees(targetYaw - STAGE.yaw);
            STAGE.yaw += d * Math.min(1f, frameDt * 8f);
            if (Math.abs(d) < 0.5f) {
                STAGE.yaw = targetYaw;
                targetYaw = Float.NaN;
            }
        }
        STAGE.update(slow ? frameDt * 0.25f : frameDt);
        nextHover = null;

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
        ui.text("menu_desc", ui.ellipsize("menu_desc", Ui.tr("skirmish.studio.subtitle"), cw), x, ty);
        float top = ty + ui.lineHeight("menu_desc") + ui.num(L + "gap");

        stageW = Math.round(cw * ui.num(L + "stage_share"));
        stageX = x;
        stageY = top;
        stageH = y + ch - top;
        drawStage(ui, mx, my);
        drawPanel(ui, x + stageW + ui.num(L + "gap"), top, cw - stageW - ui.num(L + "gap"), y + ch - top, mx, my);
        widget(ui, frame.grip, mx, my);
        hover = nextHover;
    }

    // ---- the big scene ----

    private void drawStage(Ui ui, double mx, double my) {
        backdrop(ui, STAGE, stageX, stageY, stageW, stageH, ui.theme().radius("panel"), true);
        submitScene(ui, STAGE, stageX, stageY, stageW, stageH, stageH * Ui.designScale() / STAGE_BLOCKS * STAGE.zoom);

        // The mannequin's health over its head.
        if (STAGE.showDummy && !STAGE.dummyGone()) {
            float[] head = project(STAGE.dummyX, 2.25, 0);
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
        // What is being tried on, while the mouse is over a card.
        if (hover != null) {
            String s = Ui.tr("skirmish.studio.trying");
            float sw = ui.textWidth("studio_hint", s) + 20;
            float sh = ui.lineHeight("studio_hint") + 8;
            float sx = stageX + (stageW - sw) / 2f;
            float sy = stageY + ui.num(L + "gap") * 1.5f + poses.preferredHeight(ui);
            ui.rect(sx, sy, sw, sh, sh / 2f, ui.color("accent"));
            ui.textCentered("studio_hint", s, sx + 10, sy, sh, ui.color("white"));
        }

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
        float rx = stageX + stageW - g;
        for (Button b : new Button[]{view, slowMo, autoPlay}) {
            float bw = b.preferredWidth(ui);
            rx -= bw;
            b.bounds(rx, by, bw, bh);
            plate(ui, b);
            widget(ui, b, mx, my);
            rx -= g * 0.6f;
        }
        String hint = Ui.tr("skirmish.studio.hint");
        float hw = ui.textWidth("studio_hint", hint);
        float hl = ui.lineHeight("studio_hint");
        float hy = by - hl - 8;
        ui.rect(stageX + stageW - g - hw - 8, hy - 2, hw + 16, hl + 4, (hl + 4) / 2f, ui.color("panel"));
        ui.text("studio_hint", hint, stageX + stageW - g - hw, hy);
    }

    /** A scene's backdrop: a vertical gradient in bands, stars at night, rounded corners. */
    private void backdrop(Ui ui, StudioStage stage, float x, float y, float w, float h, float radius, boolean stars) {
        int[] c = StudioStage.colors(stage.scene);
        pushClip(ui, x, y, x + w, y + h);
        int bands = Math.max(8, Math.round(h / 12));
        for (int i = 0; i < bands; i++) {
            float y0 = y + h * i / bands;
            ui.rect(x, y0, w, h / bands + 1, 0f, FxColor.lerp(c[0], c[1], i / (float) (bands - 1)));
        }
        if (stars && (stage.scene == StudioStage.Scene.NIGHT || stage.scene == StudioStage.Scene.VOID)) {
            var rnd = net.minecraft.util.RandomSource.create(42);
            for (int i = 0; i < 70; i++) {
                float sx = x + rnd.nextFloat() * w;
                float sy = y + rnd.nextFloat() * h * 0.6f;
                float tw = 0.5f + 0.5f * (float) Math.sin(stage.clock * (1 + rnd.nextFloat() * 2) + i);
                ui.circle(sx, sy, 1.2f + rnd.nextFloat() * 1.3f, FxColor.alpha(0xFFFFFFFF, 0.25f + 0.5f * tw));
            }
        }
        popClip(ui);
        ui.cornerMask(x, y, w, h, radius, ui.color("window"));
    }

    /** Hands a scene to the GUI to draw in 3D into the rectangle (design px) at {@code scale} GUI px per block. */
    private void submitScene(Ui ui, StudioStage stage, float x, float y, float w, float h, float scale) {
        float ds = Ui.designScale();
        float pt = minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(true);
        ui.graphics().guiRenderState.submitPicturesInPictureState(new StudioSceneState(stage, pt,
                Math.round(x * ds), Math.round(y * ds), Math.round((x + w) * ds), Math.round((y + h) * ds), scale,
                ui.graphics().scissorStack.peek()));
    }

    /** Where a point of the big scene lands on screen (design px), matching {@link StudioSceneRenderer}. */
    private float[] project(double px, double py, double pz) {
        Quaternionf q = new Quaternionf().rotateZ((float) Math.PI)
                .rotateX((float) Math.toRadians(STAGE.pitch))
                .rotateY((float) Math.toRadians(STAGE.yaw));
        Vector3f v = q.transform(new Vector3f((float) px, (float) py - STAGE.centerY, (float) pz));
        float s = stageH / STAGE_BLOCKS * STAGE.zoom;
        return new float[]{stageX + stageW / 2f + v.x * s, stageY + stageH / 2f + v.y * s};
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

    // ---- the wardrobe ----

    private void drawPanel(Ui ui, float x, float y, float w, float h, double mx, double my) {
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
        float bh = done.preferredHeight(ui);
        float dwid = done.preferredWidth(ui);
        done.bounds(x + w - dwid, y + h - bh, dwid, bh);
        widget(ui, done, mx, my);

        listTop = cy + chipH + g;
        listBottom = y + h - bh - g;
        ui.hline(x, listTop - g * 0.5f, w, ui.color("stroke"));
        pushClip(ui, x - 2, listTop, x + w + 2, listBottom);
        float ry = listTop - scroll + 4;
        ry = drawTab(ui, x, ry, w, mx, my);
        popClip(ui);
        maxScroll = Math.max(0f, ry + scroll - listBottom);
        scroll = Math.max(0f, Math.min(scroll, maxScroll));
        if (maxScroll > 0) {
            float track = listBottom - listTop;
            float thumb = Math.max(24f, track * track / (track + maxScroll));
            float ty = listTop + (track - thumb) * (scroll / maxScroll);
            ui.rect(x + w + 4, ty, 3, thumb, 1.5f, ui.color("stroke_10"));
        }
    }

    /** The tab's cards, colours, sounds and rows; returns where it ends. */
    private float drawTab(Ui ui, float x, float y, float w, double mx, double my) {
        PlayerFxModule p = PlayerFxModule.instance();
        KillFxModule k = KillFxModule.instance();
        float gap = ui.num(L + "section_gap");
        Module owner = tab == Tab.KILL ? k : p;
        if (owner == null) {
            ui.text("menu_row_desc", Ui.tr("skirmish.studio.module_off"), x, y);
            return y + ui.lineHeight("menu_row_desc");
        }
        if (!owner.isEnabled()) {
            String note = Ui.tr("skirmish.studio.disabled", Component.translatable(owner.nameKey()).getString());
            float nh = ui.num(M + "small_button_height");
            float bw = enable.preferredWidth(ui);
            ui.rect(x, y, w, nh + 12, 10f, FxColor.alpha(ui.color("warn"), 0.14f));
            ui.textCentered("menu_row_desc", ui.ellipsize("menu_row_desc", note, w - bw - 30), x + 10, y + 6, nh, ui.color("warn"));
            enable.bounds(x + w - bw - 6, y + 6, bw, nh);
            widget(ui, enable, mx, my);
            y += nh + 12 + gap;
        }
        switch (tab) {
            case TRAIL -> {
                y = cards(ui, enumOptions(p.trail, FxStyles.Trail.OFF, (l, v) -> l.trail = v), StudioStage.Demo.TRAIL, x, y, w, mx, my) + gap;
                y = swatchRow(ui, "trail_color", Ui.tr("skirmish.studio.color"), p.trailColor, (l, c) -> l.trailColor = c, StudioStage.Demo.TRAIL, x, y, w, mx, my) + gap;
                y = rows(ui, x, y, w, mx, my, p.trailWhen, p.trailDensity);
            }
            case AURA -> {
                y = cards(ui, enumOptions(p.aura, FxStyles.Aura.OFF, (l, v) -> l.aura = v), StudioStage.Demo.AURA, x, y, w, mx, my) + gap;
                y = swatchRow(ui, "aura_color", Ui.tr("skirmish.studio.color"), p.auraColor, (l, c) -> l.auraColor = c, StudioStage.Demo.AURA, x, y, w, mx, my) + gap;
                y = rows(ui, x, y, w, mx, my, p.auraSize, p.auraFirstPerson);
            }
            case HIT -> {
                y = cards(ui, enumOptions(p.hit, FxStyles.Hit.OFF, (l, v) -> l.hit = v), StudioStage.Demo.HIT, x, y, w, mx, my) + gap;
                y = swatchRow(ui, "hit_color", Ui.tr("skirmish.studio.color_hit"), p.hitColor, (l, c) -> l.hitColor = c, StudioStage.Demo.HIT, x, y, w, mx, my) + gap;
                y = swatchRow(ui, "crit_color", Ui.tr("skirmish.studio.color_crit"), p.critColor, (l, c) -> l.critColor = c, StudioStage.Demo.HIT, x, y, w, mx, my) + gap;
                if (k != null) {
                    y = chipRow(ui, Ui.tr("skirmish.studio.sound_hit"), hitSoundChips(k), x, y, w, mx, my) + gap;
                    y = chipRow(ui, Ui.tr("skirmish.studio.sound_crit"), critSoundChips(k), x, y, w, mx, my) + gap;
                    y = rows(ui, x, y, w, mx, my, p.hitSize, p.hitMobs, k.hitVolume);
                } else {
                    y = rows(ui, x, y, w, mx, my, p.hitSize, p.hitMobs);
                }
            }
            case KILL -> {
                y = cards(ui, killOptions(k), StudioStage.Demo.KILL, x, y, w, mx, my) + gap;
                y = swatchRow(ui, "burst_color", Ui.tr("skirmish.studio.color"), k.burstColor, (l, c) -> l.burstColor = c, StudioStage.Demo.KILL, x, y, w, mx, my) + gap;
                y = chipRow(ui, Ui.tr("skirmish.studio.sound_kill"), killSoundChips(k), x, y, w, mx, my) + gap;
                y = rows(ui, x, y, w, mx, my, k.amount, k.lightning, k.banner, k.volume, k.streakPitch);
            }
            case TOTEM -> {
                y = cards(ui, enumOptions(p.totem, null, (l, v) -> l.totem = v), StudioStage.Demo.TOTEM, x, y, w, mx, my) + gap;
                y = chipRow(ui, Ui.tr("skirmish.studio.sound_totem"), totemSoundChips(p), x, y, w, mx, my) + gap;
                y = rows(ui, x, y, w, mx, my, p.totemHideItem, p.totemCounter, p.totemOthers);
            }
            case CAPE -> {
                y = cards(ui, enumOptions(p.cape, null, (l, v) -> l.cape = v), StudioStage.Demo.CAPE, x, y, w, mx, my) + gap;
                y = rows(ui, x, y, w, mx, my, p.capeElytra);
            }
            case HAND -> {
                for (String line : ui.wrap("menu_row_desc", Ui.tr("skirmish.studio.note.hand"), w)) {
                    ui.text("menu_row_desc", line, x, y, ui.color("accent"));
                    y += ui.lineHeight("menu_row_desc");
                }
                y = rows(ui, x, y + gap * 0.5f, w, mx, my, p.hand, p.handScale, p.handX, p.handY, p.handZ);
            }
        }
        return y;
    }

    private interface LookEdit<E> {
        void apply(StudioLook look, E value);
    }

    /** One card per value of an enum setting; {@code off} (if any) is the «none» card. */
    private static <E extends Enum<E>> List<Option> enumOptions(EnumSetting<E> setting, @Nullable E off, LookEdit<E> edit) {
        List<Option> out = new ArrayList<>();
        for (E v : setting.visibleValues()) {
            out.add(new Option(setting.id() + ":" + v.name(), Texts.enumValue(setting, v).getString(), () -> setting.get() == v,
                    () -> setting.set(v), l -> edit.apply(l, v), v == off));
        }
        return out;
    }

    private static List<Option> killOptions(KillFxModule k) {
        List<Option> out = new ArrayList<>();
        out.add(new Option("burst:off", Ui.tr("skirmish.studio.off"), () -> !k.particles.get(), () -> k.particles.set(false),
                l -> l.killParticles = false, true));
        for (KillFxModule.Burst b : k.burst.visibleValues()) {
            out.add(new Option("burst:" + b.name(), Texts.enumValue(k.burst, b).getString(), () -> k.particles.get() && k.burst.get() == b,
                    () -> {
                        k.particles.set(true);
                        k.burst.set(b);
                    }, l -> {
                        l.killParticles = true;
                        l.burst = b;
                    }, false));
        }
        return out;
    }

    /** A grid of style cards, three to a row; returns where it ends. */
    private float cards(Ui ui, List<Option> options, StudioStage.Demo demo, float x, float y, float w, double mx, double my) {
        int cols = 3;
        float gap = ui.num(L + "card_gap");
        float cw = (w - gap * (cols - 1)) / cols;
        float preview = cw * ui.num(L + "card_ratio");
        float ch = preview + ui.num(L + "card_label_h");
        for (int i = 0; i < options.size(); i++) {
            Option o = options.get(i);
            StyleCard card = cards.computeIfAbsent(o.id(), id -> new StyleCard(demo));
            card.option = o;
            card.previewH = preview;
            card.bounds(x + (i % cols) * (cw + gap), y + (i / cols) * (ch + gap), cw, ch);
            if (card.y + card.h >= listTop && card.y <= listBottom) {
                widget(ui, card, mx, my);
            }
        }
        int rowsN = (options.size() + cols - 1) / cols;
        return y + rowsN * ch + (rowsN - 1) * gap;
    }

    private interface PaletteEdit {
        void apply(StudioLook look, FxPalette value);
    }

    /** A titled row of colour dots for a palette setting. */
    private float swatchRow(Ui ui, String key, String title, EnumSetting<FxPalette> setting, PaletteEdit edit, StudioStage.Demo demo,
                            float x, float y, float w, double mx, double my) {
        ui.text("studio_section", title.toUpperCase(java.util.Locale.ROOT), x, y);
        y += ui.lineHeight("studio_section") + 6;
        float d = ui.num(L + "swatch");
        float gap = ui.num(L + "swatch_gap");
        float sx = x;
        for (FxPalette pal : setting.visibleValues()) {
            if (sx + d > x + w) {
                sx = x;
                y += d + gap;
            }
            Swatch s = swatches.computeIfAbsent(key + ":" + pal.name(), id -> new Swatch());
            s.palette = pal;
            s.worn = () -> setting.get() == pal;
            s.wear = () -> setting.set(pal);
            s.demo = demo;
            s.edit = l -> edit.apply(l, pal);
            s.label = Texts.enumValue(setting, pal).getString();
            s.bounds(sx, y, d, d);
            widget(ui, s, mx, my);
            sx += d + gap;
        }
        return y + d;
    }

    /** A titled, wrapping row of sound buttons; picking one plays it. */
    private float chipRow(Ui ui, String title, List<Chip> row, float x, float y, float w, double mx, double my) {
        ui.text("studio_section", title.toUpperCase(java.util.Locale.ROOT), x, y);
        y += ui.lineHeight("studio_section") + 6;
        float h = ui.num(L + "chip_h");
        float gap = ui.num(L + "chip_gap");
        float cx = x;
        for (Chip c : row) {
            float cw = c.preferredWidth(ui);
            if (cx + cw > x + w && cx > x) {
                cx = x;
                y += h + gap;
            }
            c.bounds(cx, y, cw, h);
            widget(ui, c, mx, my);
            cx += cw + gap;
        }
        return y + h;
    }

    private Chip chip(String id, String label, BooleanSupplier worn, Runnable pick) {
        Chip c = chips.computeIfAbsent(id, i -> new Chip());
        c.label = label;
        c.worn = worn;
        c.pick = pick;
        return c;
    }

    private List<Chip> hitSoundChips(KillFxModule k) {
        List<Chip> out = new ArrayList<>();
        out.add(chip("hit:off", Ui.tr("skirmish.studio.off"), () -> !k.hitSound.get(), () -> k.hitSound.set(false)));
        for (KillFxModule.HitSound s : k.hitSoundType.visibleValues()) {
            out.add(chip("hit:" + s.name(), Texts.enumValue(k.hitSoundType, s).getString(), () -> k.hitSound.get() && k.hitSoundType.get() == s,
                    () -> {
                        k.hitSound.set(true);
                        k.hitSoundType.set(s);
                        FxSounds.play(s.event(), s.pitch(), (float) (k.hitVolume.get() / 100.0));
                    }));
        }
        return out;
    }

    private List<Chip> critSoundChips(KillFxModule k) {
        List<Chip> out = new ArrayList<>();
        for (KillFxModule.CritSound s : k.critSound.visibleValues()) {
            out.add(chip("crit:" + s.name(), Texts.enumValue(k.critSound, s).getString(), () -> k.critSound.get() == s, () -> {
                k.critSound.set(s);
                k.playHitSound(true);
            }));
        }
        return out;
    }

    private List<Chip> killSoundChips(KillFxModule k) {
        List<Chip> out = new ArrayList<>();
        out.add(chip("kill:off", Ui.tr("skirmish.studio.off"), () -> !k.sound.get(), () -> k.sound.set(false)));
        for (KillFxModule.KillSound s : k.soundType.visibleValues()) {
            out.add(chip("kill:" + s.name(), Texts.enumValue(k.soundType, s).getString(), () -> k.sound.get() && k.soundType.get() == s,
                    () -> {
                        k.sound.set(true);
                        k.soundType.set(s);
                        FxSounds.play(s.event(), s.pitch(), (float) (k.volume.get() / 100.0));
                    }));
        }
        return out;
    }

    private List<Chip> totemSoundChips(PlayerFxModule p) {
        List<Chip> out = new ArrayList<>();
        for (PlayerFxModule.TotemSound s : p.totemSound.visibleValues()) {
            out.add(chip("totem:" + s.name(), Texts.enumValue(p.totemSound, s).getString(), () -> p.totemSound.get() == s, () -> {
                p.totemSound.set(s);
                SoundEvent e = s.event();
                FxSounds.play(e == null ? SoundEvents.TOTEM_USE : e, 1f, e == null ? 0.6f : 0.8f);
            }));
        }
        return out;
    }

    private float rows(Ui ui, float x, float y, float w, double mx, double my, Setting<?>... settings) {
        for (Setting<?> s : settings) {
            if (!s.isVisible()) {
                continue;
            }
            y += row(ui, s, x, y, w, mx, my) + ui.num(M + "rows_gap");
        }
        return y;
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
        List<String> titleLines = ui.wrap("menu_row_title", Texts.settingName(setting).getString(), textW);
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
        if (setting.visibleValues().size() <= 3 && seg.preferredWidth(ui) < rowW * 0.6f) {
            return seg;
        }
        return new Dropdown(
                () -> setting.visibleValues().stream().map(v -> Texts.enumValue(setting, v).getString()).toList(),
                () -> setting.visibleValues().indexOf(setting.get()),
                i -> setting.set(setting.visibleValues().get(i)));
    }

    /** Whether the pointer is over {@code w} and inside the scrolling list. */
    private boolean pointing(Widget w, double mx, double my) {
        return w.contains(mx, my) && my >= listTop && my <= listBottom;
    }

    // ---- widgets ----

    /** A style card: a little live scene with just this style, its name, and a mark when it's worn. */
    private final class StyleCard extends Widget {
        private final StudioStage stage;
        private final StudioStage.Demo demo;
        Option option;
        float previewH;
        private final Anim lift = new Anim("hover_ms");

        StyleCard(StudioStage.Demo demo) {
            this.demo = demo;
            this.stage = StudioStage.card(demo);
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            boolean over = pointing(this, mx, my);
            boolean worn = option.worn().getAsBoolean();
            float t = lift.target(over ? 1f : 0f).value();
            if (over) {
                nextHover = new Hover(demo, option.preview());
            }
            float r = ui.num(L + "card_r");
            ui.rect(x, y, w, h, r, Anim.lerpColor(ui.color("fill_04"), ui.color("fill_08"), t));
            float pad = 5f;
            float px = x + pad;
            float py = y + pad;
            float pw = w - pad * 2;
            float ph = previewH - pad;
            if (option.off()) {
                ui.rect(px, py, pw, ph, r - 3, ui.color("studio_card_bg"));
                float d = Math.min(pw, ph) * 0.34f;
                float cx = px + pw / 2f;
                float cy = py + ph / 2f;
                ui.ring(cx, cy, d, 2.5f, ui.color("text_3"));
                ui.line(cx - d * 0.33f, cy + d * 0.33f, cx + d * 0.33f, cy - d * 0.33f, 2.5f, ui.color("text_3"));
            } else {
                backdrop(ui, stage, px, py, pw, ph, r - 3, false);
                StudioLook look = StudioLook.current();
                option.preview().accept(look);
                stage.look = look.only(demo);
                // Hits are over in a blink: their cards run a little slower so the effect stays in view.
                stage.update(demo == StudioStage.Demo.HIT ? frameDt * 0.6f : frameDt);
                submitScene(ui, stage, px, py, pw, ph, ph * Ui.designScale() / stage.cardBlocks);
            }
            String label = ui.ellipsize("studio_card", option.label(), w - 12);
            float lw = ui.textWidth("studio_card", label);
            ui.textCentered("studio_card", label, x + (w - lw) / 2f, y + previewH, h - previewH,
                    ui.color(worn ? "white" : "text_2"));
            if (worn) {
                ui.border(x, y, w, h, r, 2f, ui.color("accent"));
                float b = ui.num(L + "badge");
                float bx = x + w - b - 8;
                float by = y + 8;
                ui.circle(bx + b / 2f, by + b / 2f, b, ui.color("accent"));
                ui.line(bx + b * 0.28f, by + b * 0.52f, bx + b * 0.44f, by + b * 0.68f, 2f, ui.color("white"));
                ui.line(bx + b * 0.44f, by + b * 0.68f, bx + b * 0.74f, by + b * 0.34f, 2f, ui.color("white"));
            } else if (t > 0f) {
                ui.border(x, y, w, h, r, 1.5f, FxColor.alpha(ui.color("accent"), 0.6f * t));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && my >= listTop && my <= listBottom) {
                option.wear().run();
                FxSounds.play(FxSounds.of("studio_whoosh"), 1.3f, 0.35f);
                return true;
            }
            return false;
        }
    }

    /** A colour dot; Rainbow is drawn as a wheel. */
    private final class Swatch extends Widget {
        FxPalette palette = FxPalette.ACCENT;
        BooleanSupplier worn = () -> false;
        Runnable wear = () -> { };
        Consumer<StudioLook> edit = l -> { };
        StudioStage.Demo demo = StudioStage.Demo.NONE;
        String label = "";

        @Override
        protected void draw(Ui ui, double mx, double my) {
            boolean over = pointing(this, mx, my);
            if (over) {
                nextHover = new Hover(demo, edit);
            }
            float cx = x + w / 2f;
            float cy = y + h / 2f;
            float d = w * (over ? 1f : 0.86f);
            if (palette == FxPalette.RAINBOW || palette == FxPalette.AUTO) {
                // Rainbow is a colour wheel; Signature is four of the styles' own colours in quarters.
                int[] quarters = {0xFFFFB627, 0xFF5AC8FA, 0xFFD7263D, 0xFF16D983};
                int n = palette == FxPalette.AUTO ? 16 : 18;
                for (int i = 0; i < n; i++) {
                    double a0 = i * Math.PI * 2 / n - Math.PI / 2;
                    double a1 = (i + 1) * Math.PI * 2 / n - Math.PI / 2;
                    float r = d / 2f;
                    int col = palette == FxPalette.AUTO ? quarters[i * 4 / n] : FxColor.hsv(i / (float) n, 0.75f, 1f);
                    ui.triangle(cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r,
                            cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r, 0.6f, col);
                }
                if (palette == FxPalette.AUTO) {
                    ui.circle(cx, cy, d * 0.36f, ui.color("window"));
                }
            } else {
                ui.circle(cx, cy, d, palette.main(0f));
                ui.circle(cx - d * 0.12f, cy - d * 0.12f, d * 0.42f, FxColor.alpha(palette.light(0f), 0.7f));
            }
            if (worn.getAsBoolean()) {
                ui.ring(cx, cy, w + 6, 2f, ui.color("white"));
            }
            if (over) {
                float tw = ui.textWidth("studio_hint", label) + 12;
                float th = ui.lineHeight("studio_hint") + 6;
                ui.rect(cx - tw / 2f, y - th - 4, tw, th, th / 2f, ui.color("panel"));
                ui.textCentered("studio_hint", label, cx - tw / 2f + 6, y - th - 4, th, ui.color("text"));
            }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && my >= listTop && my <= listBottom) {
                wear.run();
                return true;
            }
            return false;
        }
    }

    /** A sound button: plays its sound and picks it. */
    private final class Chip extends Widget {
        String label = "";
        BooleanSupplier worn = () -> false;
        Runnable pick = () -> { };

        @Override
        public float preferredWidth(Ui ui) {
            return ui.textWidth("studio_chip", label) + ui.num(L + "chip_pad") * 2 + ui.num(L + "chip_icon") + 6;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            boolean on = worn.getAsBoolean();
            float t = pointing(this, mx, my) ? 1f : hovered();
            ui.rect(x, y, w, h, h / 2f, on ? ui.color("accent") : Anim.lerpColor(ui.color("fill_04"), ui.color("fill_08"), t));
            float icon = ui.num(L + "chip_icon");
            float ix = x + ui.num(L + "chip_pad");
            float iy = y + (h - icon) / 2f;
            int c = ui.color(on ? "white" : "text_2");
            // A small speaker.
            ui.rect(ix, iy + icon * 0.32f, icon * 0.28f, icon * 0.36f, 1f, c);
            ui.triangle(ix + icon * 0.2f, iy + icon * 0.5f, ix + icon * 0.62f, iy + icon * 0.1f, ix + icon * 0.62f, iy + icon * 0.9f, 0.5f, c);
            ui.line(ix + icon * 0.8f, iy + icon * 0.3f, ix + icon * 0.8f, iy + icon * 0.7f, 1.5f, c);
            ui.textCentered("studio_chip", label, ix + icon + 6, y, h, c);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0 && my >= listTop && my <= listBottom) {
                pick.run();
                return true;
            }
            return false;
        }
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
            turnedByHand = true;
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
        scroll = Math.max(0f, Math.min(maxScroll, scroll - (float) scrollY * 40f));
        return true;
    }
}
