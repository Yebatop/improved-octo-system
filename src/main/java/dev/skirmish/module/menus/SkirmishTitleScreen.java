package dev.skirmish.module.menus;

import dev.skirmish.gui.ModuleIcons;
import dev.skirmish.gui.SkirmishScreen;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.UiScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.SafetyScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.List;

/**
 * Skirmish's title screen: the space backdrop with a quasar, the logo and name on the left over a column of buttons
 * («Играть на HolyWorld» first), a tip card at the bottom right and the version line. Same actions as vanilla's
 * title screen (worlds, servers, options, language, quit) plus the Skirmish menu.
 */
public final class SkirmishTitleScreen extends UiScreen {
    private static final String L = "layout.mainmenu.";
    private final long seed = Util.getMillis() / 1000;
    private final Button play = button("skirmish.menus.play", true, this::playHolyWorld);
    private final Button single = button("menu.singleplayer", false, () -> minecraft.setScreen(new SelectWorldScreen(this)));
    private final Button multi = button("menu.multiplayer", false, () -> minecraft.setScreen(
            minecraft.options.skipMultiplayerWarning ? new JoinMultiplayerScreen(this) : new SafetyScreen(this)));
    private final Button options = button("menu.options", false, () -> minecraft.setScreen(new OptionsScreen(this, minecraft.options)));
    private final Button skirmish = button("skirmish.menus.skirmish", false, () -> minecraft.setScreen(new SkirmishScreen(this)));
    private final Button language = button("options.language", false,
            () -> minecraft.setScreen(new LanguageSelectScreen(this, minecraft.options, minecraft.getLanguageManager())));
    private final Button quit = button("menu.quit", false, () -> minecraft.stop());

    public SkirmishTitleScreen() {
        super(Component.translatable("narrator.screen.title"), null);
    }

    private static Button button(String key, boolean primary, Runnable action) {
        return new Button(() -> Ui.tr(key), primary, action).layout(L);
    }

    private void playHolyWorld() {
        String host = MainMenuModule.serverAddress();
        ServerData data = new ServerData("HolyWorld", host, ServerData.Type.OTHER);
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(host), data, false, null);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        long now = Util.getMillis();
        float w = ui.width();
        float h = ui.height();
        MenuBackdrop.draw(ui, w, h, now, true, MainMenuModule.quasarOn());

        float left = Math.max(ui.num(L + "margin"), w * ui.num(L + "left"));
        float bw = ui.num(L + "button_width");
        float bh = ui.num(L + "button_height");
        float gap = ui.num(L + "button_gap");
        float logo = ui.num(L + "logo");
        float blockH = logo + ui.num(L + "brand_gap") + bh * 5 + gap * 4 + ui.num(L + "quit_gap") + bh;
        float top = Math.max(ui.num(L + "margin"), (h - blockH) / 2f - ui.num(L + "raise"));

        // Brand: emblem, name, subtitle.
        ui.rect(left, top, logo, logo, logo * 0.28f, ui.color("accent_16"));
        ModuleIcons.logo(ui, left + logo * 0.12f, top + logo * 0.12f, logo * 0.76f, ui.color("accent"), ui.color("text"));
        float tx = left + logo + ui.num(L + "logo_gap");
        ui.text("mm_title", "Skirmish", tx, top + (logo - ui.lineHeight("mm_title") - ui.lineHeight("mm_sub")) / 2f);
        ui.text("mm_sub", Ui.tr("skirmish.menus.subtitle"), tx,
                top + (logo - ui.lineHeight("mm_title") - ui.lineHeight("mm_sub")) / 2f + ui.lineHeight("mm_title"));

        float y = top + logo + ui.num(L + "brand_gap");
        for (Button b : List.of(play, single, multi)) {
            b.bounds(left, y, bw, bh);
            widget(ui, b, mx, my);
            y += bh + gap;
        }
        float half = (bw - gap) / 2f;
        options.bounds(left, y, half, bh);
        skirmish.bounds(left + half + gap, y, half, bh);
        widget(ui, options, mx, my);
        widget(ui, skirmish, mx, my);
        y += bh + gap;
        language.bounds(left, y, half, bh);
        quit.bounds(left + half + gap, y, half, bh);
        widget(ui, language, mx, my);
        widget(ui, quit, mx, my);

        if (MainMenuModule.tipsOn()) {
            drawTip(ui, w, h, now);
        }
        String version = "Minecraft " + SharedConstants.getCurrentVersion().name() + " · Fabric · Skirmish "
                + FabricLoader.getInstance().getModContainer("skirmish").map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        float fy = h - ui.num(L + "footer") - ui.lineHeight("mm_footer");
        ui.text("mm_footer", version, ui.num(L + "footer"), fy);
        String copyright = "Copyright Mojang AB. Do not distribute!";
        ui.text("mm_footer", copyright, w - ui.num(L + "footer") - ui.textWidth("mm_footer", copyright), fy);
    }

    private void drawTip(Ui ui, float w, float h, long now) {
        long period = Math.round(ui.num(L + "tip_ms"));
        int index = MenuTips.index(now, period, seed);
        float phase = (now % period) / (float) period;
        float fade = Math.min(1f, Math.min(phase * 8f, (1f - phase) * 8f));
        float cw = ui.num(L + "tip_width");
        float pad = ui.num(L + "tip_pad");
        List<String> lines = ui.wrap("mm_tip", MenuTips.text(index), cw - pad * 2);
        float ch = pad * 2 + ui.lineHeight("mm_tip_head") + ui.num(L + "tip_gap") + lines.size() * ui.lineHeight("mm_tip");
        float x = w - ui.num(L + "margin") - cw;
        float y = h - ui.num(L + "margin") - ch - ui.num(L + "footer");
        ui.box(x, y, cw, ch, ui.theme().radius("panel"), ui.color("panel"), ui.color("stroke"));
        ui.pushAlpha(fade);
        ui.text("mm_tip_head", Ui.tr("skirmish.menus.tip_head"), x + pad, y + pad, ui.color("accent"));
        float ly = y + pad + ui.lineHeight("mm_tip_head") + ui.num(L + "tip_gap");
        for (String line : lines) {
            ui.text("mm_tip", line, x + pad, ly);
            ly += ui.lineHeight("mm_tip");
        }
        ui.popAlpha();
    }
}
