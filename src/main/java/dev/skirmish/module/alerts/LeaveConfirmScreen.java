package dev.skirmish.module.alerts;

import dev.skirmish.module.alerts.parse.LossItems;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.UiScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * «Точно выйти?»: why leaving now is costly, with «Остаться» (back to the pause menu, also Esc) and «Выйти всё
 * равно», which presses the vanilla disconnect button. Only asks; the player decides.
 */
final class LeaveConfirmScreen extends UiScreen {
    private static final String L = "layout.alerts.";
    private static final String M = "layout.menu.";

    private final AlertsModule module;
    private final net.minecraft.client.gui.components.Button disconnect;
    private final Button stay = new Button(() -> Ui.tr("skirmish.alerts.confirm.stay"), true, this::onClose);
    private final Button leave = new Button(() -> Ui.tr("skirmish.alerts.confirm.leave"), false, this::leave);
    private boolean left;

    LeaveConfirmScreen(AlertsModule module, PauseScreen parent, net.minecraft.client.gui.components.Button disconnect) {
        super(Component.translatable("skirmish.alerts.confirm.title"), parent);
        this.module = module;
        this.disconnect = disconnect;
    }

    private void leave() {
        if (left) {
            return;
        }
        left = true;
        module.log("player chose to leave anyway");
        disconnect.onPress(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0));
    }

    private List<String> reasons() {
        List<String> out = new ArrayList<>();
        LossItems.Tally tally = module.tally();
        if (module.logoutRisk()) {
            out.add(Ui.tr("skirmish.alerts.banner.body", AlertText.losses(tally)));
        }
        int fights = module.fightsForConfirm();
        if (fights > 0) {
            out.add(Ui.tr("skirmish.alerts.confirm.fight", fights + " " + Ui.plural("skirmish.alerts.count.opponent", fights)));
        }
        return out;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        ui.rect(0, 0, ui.width(), ui.height(), 0, ui.color("backdrop"));
        float stroke = ui.num("stroke.width");
        float padX = ui.num(M + "content_pad_x");
        float padY = ui.num(M + "content_pad_y");
        float w = Math.min(ui.num(L + "confirm_width"), ui.width() - ui.num("layout.screen_edge") * 2);
        float icon = ui.num(L + "confirm_icon");
        float gap = ui.num(M + "content_gap");
        float textX = stroke + padX + icon + ui.num(M + "header_gap");
        float textW = w - textX - stroke - padX;

        List<String> lines = new ArrayList<>();
        for (String reason : reasons()) {
            lines.addAll(ui.wrap("confirm_line", reason, textW));
        }
        List<String> hint = ui.wrap("menu_desc", Ui.tr("skirmish.alerts.confirm.hint"), textW);
        float lineGap = ui.num(L + "confirm_line_gap");
        float bh = stay.preferredHeight(ui);
        float textH = ui.lineHeight("menu_title") + ui.num(M + "header_text_gap")
                + lines.size() * (ui.lineHeight("confirm_line") + lineGap) + hint.size() * ui.lineHeight("menu_desc");
        float h = stroke * 2 + padY * 2 + Math.max(icon, textH) + gap + bh;
        float ox = Math.round((ui.width() - w) / 2f);
        float oy = Math.round((ui.height() - h) / 2f) + Math.round((1f - appearProgress()) * ui.num("layout.appear_offset"));
        ui.box(ox, oy, w, h, ui.theme().radius("window"), ui.color("window"), ui.color("stroke_07"));

        float x = ox + stroke + padX;
        float y = oy + stroke + padY;
        AlertText.warningIcon(ui, x, y, icon, ui.color("warn"));
        float tx = ox + textX;
        float ty = y;
        ui.text("menu_title", Ui.tr("skirmish.alerts.confirm.title"), tx, ty);
        ty += ui.lineHeight("menu_title") + ui.num(M + "header_text_gap");
        for (String line : lines) {
            ui.text("confirm_line", line, tx, ty);
            ty += ui.lineHeight("confirm_line") + lineGap;
        }
        for (String line : hint) {
            ui.text("menu_desc", line, tx, ty);
            ty += ui.lineHeight("menu_desc");
        }

        float by = oy + h - stroke - padY - bh;
        float right = ox + w - stroke - padX;
        float sw = stay.preferredWidth(ui);
        float lw = leave.preferredWidth(ui);
        stay.bounds(right - sw, by, sw, bh);
        leave.bounds(right - sw - ui.num(M + "footer_gap") - lw, by, lw, bh);
        widget(ui, leave, mx, my);
        widget(ui, stay, mx, my);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
