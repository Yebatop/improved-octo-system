package dev.skirmish.module.killcam;

import dev.skirmish.hud.HudBlock;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.hud.Placement;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;

/** «● KillCam · последние 10 с» pill while the recorder is running; «KillCam · клип сохранён» for a moment after a save. */
final class KillCamIndicator extends HudBlock {
    private final KillCamModule module;
    private final Recorder recorder;

    KillCamIndicator(KillCamModule module, Recorder recorder) {
        super("killcam", "skirmish.hud.element.killcam", new Placement(1, 0, 1, 0, -18, 18));
        this.module = module;
        this.recorder = recorder;
    }

    @Override
    public boolean enabled() {
        return module.isEnabled() && module.hudIndicator.get();
    }

    @Override
    public boolean shown() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && mc.player.isAlive() && !recorder.isFrozen() && ReplaySession.current() == null;
    }

    @Override
    public boolean hasContent() {
        return true;
    }

    private String sub() {
        String notice = module.notice();
        if (notice != null) {
            return Ui.tr(notice);
        }
        return Ui.tr("skirmish.hud.killcam.last", Ui.decimal(module.preDeathTicks() / 20.0, 0));
    }

    @Override
    public float width(Ui ui, boolean preview) {
        String l = "layout.hud.";
        return ui.num("stroke.width") * 2 + ui.num(l + "rec_pad_x") * 2 + ui.num("layout.menu.dot") + ui.num(l + "rec_gap") * 2
                + ui.textWidth("rec_title", "KillCam") + ui.textWidth("rec_sub", sub());
    }

    @Override
    public float height(Ui ui, boolean preview) {
        return ui.num("stroke.width") * 2 + ui.num("layout.hud.rec_pad_y") * 2
                + Math.max(ui.lineHeight("rec_title"), ui.lineHeight("rec_sub"));
    }

    @Override
    public void render(Ui ui, float x, float y, boolean preview) {
        String l = "layout.hud.";
        float w = width(ui, preview);
        float h = height(ui, preview);
        HudStyle.pill(ui, x, y, w, h);
        float cx = x + ui.num("stroke.width") + ui.num(l + "rec_pad_x");
        float dot = ui.num("layout.menu.dot");
        ui.circle(cx + dot / 2f, y + h / 2f, dot, ui.color("rec"));
        cx += dot + ui.num(l + "rec_gap");
        cx = ui.textCentered("rec_title", "KillCam", cx, y, h) + ui.num(l + "rec_gap");
        ui.textCentered("rec_sub", sub(), cx, y, h);
    }
}
