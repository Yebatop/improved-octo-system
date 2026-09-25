package dev.skirmish.module.menus;

import dev.skirmish.combat.CombatTrackerModule;
import dev.skirmish.combat.SessionStats;
import dev.skirmish.gui.ModuleIcons;
import dev.skirmish.gui.SkirmishScreen;
import dev.skirmish.gui.WaypointListScreen;
import dev.skirmish.hud.HudEditScreen;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.util.ServerContext;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Skirmish's pause screen: the game dimmed under a faint starfield, a card with the server name, this session's
 * kills, deaths, K/D and time played, «Вернуться в игру», a grid of shortcuts and the disconnect button. Pauses a
 * singleplayer world like vanilla's; Esc returns to the game.
 */
public final class SkirmishPauseScreen extends UiScreen {
    private static final String L = "layout.pausemenu.";
    /** Optional extra shortcut (the world map registers itself here). */
    private static @Nullable Shortcut extra;

    /** A pause-menu shortcut: label key and the screen it opens (given this screen as parent). */
    public record Shortcut(String labelKey, java.util.function.Function<net.minecraft.client.gui.screens.Screen, net.minecraft.client.gui.screens.Screen> open,
                           Supplier<Boolean> visible) {
    }

    private final Button resume = new Button(() -> Ui.tr("menu.returnToGame"), true, this::onClose).layout(L);
    private final Button disconnect = new Button(this::disconnectLabel, false, this::disconnect).layout(L);
    private final List<Button> grid = new ArrayList<>();

    public SkirmishPauseScreen() {
        super(Component.translatable("menu.game"), null);
    }

    public static void registerShortcut(Shortcut shortcut) {
        extra = shortcut;
    }

    @Override
    protected void init() {
        super.init();
        grid.clear();
        grid.add(open("skirmish.menus.skirmish", () -> new SkirmishScreen(this)));
        grid.add(open("menu.options", () -> new OptionsScreen(this, minecraft.options)));
        grid.add(open("skirmish.menus.hud_editor", () -> new HudEditScreen(this)));
        grid.add(open("skirmish.menus.waypoints", () -> new WaypointListScreen(this)));
        Shortcut s = extra;
        if (s != null && s.visible().get()) {
            grid.add(open(s.labelKey(), () -> s.open().apply(this)));
        }
        if (minecraft.player != null) {
            grid.add(open("gui.advancements", () -> new AdvancementsScreen(minecraft.player.connection.getAdvancements(), this)));
            grid.add(open("gui.stats", () -> new StatsScreen(this, minecraft.player.getStats())));
        }
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null && !minecraft.getSingleplayerServer().isPublished()) {
            grid.add(open("menu.shareToLan", () -> new ShareToLanScreen(this)));
        }
    }

    private Button open(String key, Supplier<net.minecraft.client.gui.screens.Screen> screen) {
        return new Button(() -> Ui.tr(key), false, () -> minecraft.setScreen(screen.get())).layout(L);
    }

    private String disconnectLabel() {
        return CommonComponents.disconnectButtonLabel(minecraft.isLocalServer()).getString();
    }

    private void disconnect() {
        disconnect.enabled = false;
        minecraft.getReportingContext().draftReportHandled(minecraft, this,
                () -> minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE), true);
    }

    @Override
    protected void draw(Ui ui, double mx, double my) {
        long now = Util.getMillis();
        float w = ui.width();
        float h = ui.height();
        ui.rect(0, 0, w, h, 0f, ui.color("pm_dim"));
        MenuBackdrop.draw(ui, w, h, now, false, false);

        float cw = ui.num(L + "width");
        float pad = ui.num(L + "pad");
        float bh = ui.num(L + "button_height");
        float gap = ui.num(L + "gap");
        boolean stats = PauseMenuModule.statsOn();
        int rows = (grid.size() + 1) / 2;
        float head = ui.num(L + "head");
        float statsH = stats ? ui.num(L + "stat_h") + gap * 2 : gap;
        float ch = pad * 2 + head + statsH + bh + gap + rows * (bh + gap) + gap + bh;
        float x = Math.round((w - cw) / 2f);
        float y = Math.round((h - ch) / 2f);
        ui.box(x, y, cw, ch, ui.theme().radius("window"), ui.color("window"), ui.color("stroke"));

        float ix = x + pad;
        float iw = cw - pad * 2;
        float cy = y + pad;
        float logo = ui.num(L + "logo");
        ModuleIcons.logo(ui, ix, cy + (head - logo) / 2f, logo, ui.color("accent"), ui.color("text"));
        ui.textCentered("pm_title", Ui.tr("skirmish.menus.pause"), ix + logo + ui.num(L + "logo_gap"), cy, head);
        String server = minecraft.hasSingleplayerServer() ? ServerContext.serverDisplayName() : ServerContext.serverKey();
        String shown = ui.ellipsize("pm_server", server, iw * 0.5f);
        ui.textCentered("pm_server", shown, ix + iw - ui.textWidth("pm_server", shown), cy, head);
        cy += head;

        if (stats) {
            cy += gap;
            drawStats(ui, ix, cy, iw, ui.num(L + "stat_h"));
            cy += ui.num(L + "stat_h") + gap;
        } else {
            cy += gap;
        }
        resume.bounds(ix, cy, iw, bh);
        widget(ui, resume, mx, my);
        cy += bh + gap;
        float half = (iw - gap) / 2f;
        for (int i = 0; i < grid.size(); i++) {
            Button b = grid.get(i);
            boolean lastOdd = i == grid.size() - 1 && grid.size() % 2 == 1;
            b.bounds(ix + (i % 2) * (half + gap), cy + (i / 2) * (bh + gap), lastOdd ? iw : half, bh);
            widget(ui, b, mx, my);
        }
        cy += rows * (bh + gap) + gap;
        disconnect.bounds(ix, cy, iw, bh);
        widget(ui, disconnect, mx, my);
    }

    private void drawStats(Ui ui, float x, float y, float w, float h) {
        SessionStats s = ModuleManager.get().byId("combat") instanceof CombatTrackerModule combat ? combat.session() : null;
        int kills = s == null ? 0 : s.kills();
        int deaths = s == null ? 0 : s.deaths();
        String kd = Ui.decimal(deaths == 0 ? kills : kills / (double) deaths, 2);
        long played = PauseMenuModule.playedMs();
        String time = played < 0 ? "—" : Ui.duration(played);
        if (played >= 3_600_000L) {
            time = played / 3_600_000L + ":" + String.format(java.util.Locale.ROOT, "%02d", (played / 60_000L) % 60) + " ч";
        }
        String[][] tiles = {
                {Integer.toString(kills), Ui.tr("skirmish.menus.kills"), "text"},
                {Integer.toString(deaths), Ui.tr("skirmish.menus.deaths"), "text"},
                {kd, "K/D", "accent"},
                {time, Ui.tr("skirmish.menus.played"), "text"}};
        float gap = ui.num(L + "gap");
        float tw = (w - gap * 3) / 4f;
        for (int i = 0; i < tiles.length; i++) {
            float tx = x + i * (tw + gap);
            ui.rect(tx, y, tw, h, ui.theme().radius("tile"), ui.color("tile"));
            float vy = y + (h - ui.lineHeight("pm_stat") - ui.lineHeight("pm_stat_label")) / 2f;
            float vw = ui.textWidth("pm_stat", tiles[i][0]);
            ui.text("pm_stat", tiles[i][0], tx + (tw - vw) / 2f, vy, ui.color(tiles[i][2]));
            float lw = ui.textWidth("pm_stat_label", tiles[i][1]);
            ui.text("pm_stat_label", tiles[i][1], tx + (tw - lw) / 2f, vy + ui.lineHeight("pm_stat"));
        }
    }
}
