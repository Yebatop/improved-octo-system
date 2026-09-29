package dev.skirmish.module.menus;

import dev.skirmish.combat.CombatTrackerModule;
import dev.skirmish.combat.SessionStats;
import dev.skirmish.gui.ModuleIcons;
import dev.skirmish.gui.SkirmishScreen;
import dev.skirmish.gui.WaypointListScreen;
import dev.skirmish.hud.HudEditScreen;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.base.BaseModule;
import dev.skirmish.module.events.EventClock;
import dev.skirmish.module.events.EventsModule;
import dev.skirmish.ui.Ui;
import dev.skirmish.ui.widget.Button;
import dev.skirmish.ui.widget.UiScreen;
import dev.skirmish.ui.widget.Widget;
import dev.skirmish.util.ServerContext;
import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.screens.achievement.StatsScreen;
import net.minecraft.client.gui.screens.advancements.AdvancementsScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Skirmish's pause screen: the game dimmed under a faint starfield, a card with the server name, four tiles with
 * what matters now (kills and deaths once there are any, K/D, what the session earned, the event on your server
 * and how long it has left, the base's value, time played), «Вернуться в игру», the mod's apps as icon tiles (menu,
 * Base OS, HolyWorld OS, map, fight review, replays, waypoints, HUD), vanilla's options, advancements and
 * statistics in one row, and the disconnect button. Pauses a singleplayer world like vanilla's; Esc returns.
 */
public final class SkirmishPauseScreen extends UiScreen {
    private static final String L = "layout.pausemenu.";
    /** App tiles modules add (the world map, Base OS, HolyWorld OS, fight review, replays). */
    private static final List<Shortcut> EXTRA = new java.util.concurrent.CopyOnWriteArrayList<>();

    /**
     * A pause-menu app: label key, icon ({@link AppIcons}), place in the row (lower first), what a click does given
     * this screen as parent, and whether it shows now (its module is on).
     */
    public record Shortcut(String labelKey, String icon, int order, java.util.function.Consumer<net.minecraft.client.gui.screens.Screen> open,
                           Supplier<Boolean> visible) {
    }

    private final Button resume = new Button(() -> Ui.tr("menu.returnToGame"), true, this::onClose).layout(L);
    private final Button disconnect = new Button(this::disconnectLabel, false, this::disconnect).layout(L);
    private final List<AppTile> apps = new ArrayList<>();
    private final List<Button> system = new ArrayList<>();

    /** An app: icon over its name, lit on hover. */
    private final class AppTile extends Widget {
        private final Shortcut app;

        AppTile(Shortcut app) {
            this.app = app;
        }

        @Override
        protected void draw(Ui ui, double mx, double my) {
            float t = hover.value();
            ui.box(x, y, w, h, ui.theme().radius("tile"), blend(ui.color("tile"), ui.color("accent_16"), t),
                    blend(ui.color("stroke"), ui.color("accent"), t));
            float icon = ui.num(L + "app_icon");
            float labelH = ui.lineHeight("pm_app");
            float top = y + (h - icon - ui.num(L + "app_icon_gap") - labelH) / 2f;
            AppIcons.draw(ui, app.icon(), x + (w - icon) / 2f, top, icon, blend(ui.color("text_2"), ui.color("text"), t));
            String label = ui.ellipsize("pm_app", Ui.tr(app.labelKey()), w - 8);
            ui.text("pm_app", label, x + (w - ui.textWidth("pm_app", label)) / 2f, top + icon + ui.num(L + "app_icon_gap"),
                    blend(ui.color("text_2"), ui.color("text"), t));
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 0) {
                app.open().accept(SkirmishPauseScreen.this);
                return true;
            }
            return false;
        }
    }

    public SkirmishPauseScreen() {
        super(Component.translatable("menu.game"), null);
    }

    /** Adds an app tile (once per label). */
    public static void registerShortcut(Shortcut shortcut) {
        EXTRA.removeIf(s -> s.labelKey().equals(shortcut.labelKey()));
        EXTRA.add(shortcut);
    }

    @Override
    protected void init() {
        super.init();
        List<Shortcut> all = new ArrayList<>();
        all.add(new Shortcut("skirmish.menus.skirmish", "skirmish", 0, parent -> minecraft.setScreen(new SkirmishScreen(parent)), () -> true));
        all.add(new Shortcut("skirmish.menus.waypoints", "pin", 60, parent -> minecraft.setScreen(new WaypointListScreen(parent)), () -> true));
        all.add(new Shortcut("skirmish.menus.hud_editor", "hud", 70, parent -> minecraft.setScreen(new HudEditScreen(parent)), () -> true));
        for (Shortcut s : EXTRA) {
            if (s.visible().get()) {
                all.add(s);
            }
        }
        all.sort(java.util.Comparator.comparingInt(Shortcut::order));
        apps.clear();
        for (Shortcut s : all) {
            apps.add(new AppTile(s));
        }
        system.clear();
        system.add(open("menu.options", () -> new OptionsScreen(this, minecraft.options)));
        if (minecraft.player != null) {
            system.add(open("gui.advancements", () -> new AdvancementsScreen(minecraft.player.connection.getAdvancements(), this)));
            system.add(open("gui.stats", () -> new StatsScreen(this, minecraft.player.getStats())));
        }
        if (minecraft.hasSingleplayerServer() && minecraft.getSingleplayerServer() != null && !minecraft.getSingleplayerServer().isPublished()) {
            system.add(open("menu.shareToLan", () -> new ShareToLanScreen(this)));
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
        int perRow = ui.theme().integer(L + "apps_per_row");
        int appRows = (apps.size() + perRow - 1) / perRow;
        float appH = ui.num(L + "app_height");
        float head = ui.num(L + "head");
        float statsH = stats ? ui.num(L + "stat_h") + gap * 2 : gap;
        float ch = pad * 2 + head + statsH + bh + gap + appRows * (appH + gap) + gap + bh + gap + bh;
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

        cy += gap;
        if (stats) {
            drawStats(ui, ix, cy, iw, ui.num(L + "stat_h"));
            cy += ui.num(L + "stat_h") + gap;
        }
        resume.bounds(ix, cy, iw, bh);
        widget(ui, resume, mx, my);
        cy += bh + gap;
        float tw = (iw - gap * (perRow - 1)) / perRow;
        for (int i = 0; i < apps.size(); i++) {
            AppTile tile = apps.get(i);
            tile.bounds(ix + (i % perRow) * (tw + gap), cy + (i / perRow) * (appH + gap), tw, appH);
            widget(ui, tile, mx, my);
        }
        cy += appRows * (appH + gap) + gap;
        float sw = (iw - gap * (system.size() - 1)) / Math.max(1, system.size());
        for (int i = 0; i < system.size(); i++) {
            Button b = system.get(i);
            b.bounds(ix + i * (sw + gap), cy, sw, bh);
            widget(ui, b, mx, my);
        }
        cy += bh + gap;
        disconnect.bounds(ix, cy, iw, bh);
        widget(ui, disconnect, mx, my);
    }

    /** Four tiles, most useful first: the fight score once there is one, the session's money, the event, the base; time played last. */
    private void drawStats(Ui ui, float x, float y, float w, float h) {
        List<String[]> tiles = new ArrayList<>();
        SessionStats s = ModuleManager.get().byId("combat") instanceof CombatTrackerModule combat ? combat.session() : null;
        int kills = s == null ? 0 : s.kills();
        int deaths = s == null ? 0 : s.deaths();
        if (kills + deaths > 0) {
            tiles.add(new String[]{kills + " / " + deaths, Ui.tr("skirmish.menus.kills_deaths"), "text"});
            tiles.add(new String[]{Ui.decimal(deaths == 0 ? kills : kills / (double) deaths, 2), "K/D", "accent"});
        }
        Long coins = dev.skirmish.module.recap.SessionRecapModule.sessionCoins();
        if (coins != null && coins != 0) {
            tiles.add(new String[]{(coins > 0 ? "+" : "−") + money(Math.abs(coins)), Ui.tr("skirmish.menus.earned"), coins > 0 ? "good" : "bad"});
        }
        if (ModuleManager.get().byId(EventsModule.ID) instanceof EventsModule ev && ev.isEnabled() && ev.liteDataLoaded() && !ev.onPrime()) {
            List<String> live = ev.myLiteEventNames();
            if (!live.isEmpty()) {
                String countdown = ev.countdownText(live.getFirst());
                EventClock.Estimate lasts = ev.lastsOf(live.getFirst());
                tiles.add(new String[]{live.getFirst(), countdown != null ? countdown
                        : lasts == null ? Ui.tr("skirmish.menus.event_now") : EventsModule.lastsText(lasts), "text"});
            }
        }
        BaseModule.Summary base = BaseModule.summary();
        if (base != null && base.priced() > 0) {
            tiles.add(new String[]{money(Math.round(base.value())), Ui.tr("skirmish.menus.base_value"), "base_tone"});
        }
        while (tiles.size() > 3) {
            tiles.removeLast();
        }
        long played = PauseMenuModule.playedMs();
        String time = played < 0 ? "—" : Ui.duration(played);
        if (played >= 3_600_000L) {
            time = played / 3_600_000L + ":" + String.format(java.util.Locale.ROOT, "%02d", (played / 60_000L) % 60) + " ч";
        }
        tiles.add(new String[]{time, Ui.tr("skirmish.menus.played"), "text"});
        float gap = ui.num(L + "gap");
        int n = tiles.size();
        float tw = (w - gap * (n - 1)) / n;
        for (int i = 0; i < n; i++) {
            String[] t = tiles.get(i);
            float tx = x + i * (tw + gap);
            ui.rect(tx, y, tw, h, ui.theme().radius("tile"), ui.color("tile"));
            String value = ui.ellipsize("pm_stat", t[0], tw - 12);
            String label = ui.ellipsize("pm_stat_label", t[1], tw - 10);
            float vy = y + (h - ui.lineHeight("pm_stat") - ui.lineHeight("pm_stat_label")) / 2f;
            ui.text("pm_stat", value, tx + (tw - ui.textWidth("pm_stat", value)) / 2f, vy, ui.color(t[2]));
            ui.text("pm_stat_label", label, tx + (tw - ui.textWidth("pm_stat_label", label)) / 2f, vy + ui.lineHeight("pm_stat"));
        }
    }

    /** "12,5к", "1,2м", "940". */
    static String money(long v) {
        if (v >= 1_000_000L) {
            return Ui.decimal(v / 1_000_000.0, 1) + "м";
        }
        if (v >= 10_000L) {
            return Math.round(v / 1000.0) + "к";
        }
        if (v >= 1_000L) {
            return Ui.decimal(v / 1000.0, 1) + "к";
        }
        return Long.toString(v);
    }

    private static int blend(int a, int b, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) + Math.round(((b >>> 24) - (a >>> 24)) * k);
        int r = ((a >> 16) & 0xFF) + Math.round((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * k);
        int g = ((a >> 8) & 0xFF) + Math.round((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * k);
        int bl = (a & 0xFF) + Math.round(((b & 0xFF) - (a & 0xFF)) * k);
        return aa << 24 | r << 16 | g << 8 | bl;
    }
}
