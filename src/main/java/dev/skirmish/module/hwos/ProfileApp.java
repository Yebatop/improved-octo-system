package dev.skirmish.module.hwos;

import dev.skirmish.combat.CombatTrackerModule;
import dev.skirmish.combat.SessionStats;
import dev.skirmish.hud.SidebarBounds;
import dev.skirmish.module.ModuleManager;
import dev.skirmish.module.base.BaseModule;
import dev.skirmish.module.events.EventsModule;
import dev.skirmish.module.market.MarketModule;
import dev.skirmish.module.menus.PauseMenuModule;
import dev.skirmish.module.navigator.NavigatorModule;
import dev.skirmish.ui.Ui;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Profile app: you on this server — face, nick, anarchy, this session's kills, deaths, K/D and time, the sidebar's
 * «Label: value» lines (balance, clan, group…) and what Skirmish keeps for you here (base, portals, waypoints,
 * prices).
 */
final class ProfileApp implements HwOsScreen.OsApp {
    @Override
    public String id() {
        return "profile";
    }

    @Override
    public void draw(HwOsScreen s, Ui ui, float x, float y, float w, float h, double mx, double my) {
        String L = HwOsScreen.L;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        float gap = ui.num(L + "col_gap");
        s.clip(ui, x - 4, y, x + w + 4, y + h);
        float top = y - s.scroll();
        float cy = top;

        // Head: face, nick, server.
        float face = ui.num(L + "face");
        PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        if (info != null) {
            PlayerFaceRenderer.draw(ui.graphics(), info.getSkin(), Math.round(x), Math.round(cy), Math.round(face));
        }
        float tx = x + face + 14;
        ui.text("hwos_nick", mc.player.getGameProfile().name(), tx, cy + 2);
        EventsModule ev = ModuleManager.get().byId(EventsModule.ID) instanceof EventsModule e && e.isEnabled() ? e : null;
        String server = ev == null ? null : ev.currentServerName();
        ui.text("menu_row_desc", server == null ? Ui.tr("skirmish.hwos.profile.server_unknown") : server, tx,
                cy + 4 + ui.lineHeight("hwos_nick"), ui.color("accent"));
        cy += face + gap;

        // Session tiles.
        SessionStats st = ModuleManager.get().byId("combat") instanceof CombatTrackerModule combat ? combat.session() : null;
        int kills = st == null ? 0 : st.kills();
        int deaths = st == null ? 0 : st.deaths();
        long played = PauseMenuModule.playedMs();
        String time = played < 0 ? "—" : played >= 3_600_000L
                ? played / 3_600_000L + ":" + String.format(java.util.Locale.ROOT, "%02d", (played / 60_000L) % 60) + " ч" : Ui.duration(played);
        float tileH = ui.num(L + "tile_height");
        float tw = (w - gap * 3) / 4f;
        HwOsScreen.tile(ui, x, cy, tw, tileH, Integer.toString(kills), Ui.tr("skirmish.menus.kills"), "text");
        HwOsScreen.tile(ui, x + (tw + gap), cy, tw, tileH, Integer.toString(deaths), Ui.tr("skirmish.menus.deaths"), "text");
        HwOsScreen.tile(ui, x + (tw + gap) * 2, cy, tw, tileH, Ui.decimal(deaths == 0 ? kills : kills / (double) deaths, 2), "K/D", "accent");
        HwOsScreen.tile(ui, x + (tw + gap) * 3, cy, tw, tileH, time, Ui.tr("skirmish.menus.played"), "text");
        cy += tileH + gap;

        // Left: the sidebar's lines. Right: what Skirmish keeps.
        float colW = (w - gap) / 2f;
        float lh = ui.lineHeight("hwos_row") + 5;
        float ly = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.profile.sidebar"), x, cy);
        SidebarBounds.Sidebar sidebar = SidebarBounds.read(mc);
        List<String> lines = new ArrayList<>();
        if (sidebar != null) {
            for (SidebarBounds.Row row : sidebar.rows()) {
                lines.add(row.name().getString());
            }
        }
        List<OsData.Pair> pairs = OsData.pairs(lines);
        if (pairs.isEmpty()) {
            ly = HwOsScreen.para(ui, "menu_row_desc", Ui.tr("skirmish.hwos.profile.no_sidebar"), x, ly, colW, ui.color("text_3"));
        }
        for (OsData.Pair p : pairs) {
            float vw = Math.min(colW * 0.6f, ui.textWidth("hwos_value", p.value()));
            ui.text("hwos_value", ui.ellipsize("hwos_value", p.value(), colW * 0.6f), x + colW - vw, ly);
            ui.text("hwos_row", ui.ellipsize("hwos_row", p.label(), colW - vw - 10), x, ly, ui.color("text_2"));
            ly += lh;
        }

        float rx = x + colW + gap;
        float ry = HwOsScreen.section(ui, Ui.tr("skirmish.hwos.profile.skirmish"), rx, cy);
        BaseModule.Summary base = BaseModule.summary();
        ry = fact(ui, rx, ry, colW, Ui.tr("skirmish.hwos.profile.base"), base == null ? Ui.tr("skirmish.hwos.profile.no_base") : base.region());
        if (base != null) {
            ry = fact(ui, rx, ry, colW, Ui.tr("skirmish.hwos.profile.chests_label"), Integer.toString(base.chests()));
        }
        if (base != null && base.priced() > 0) {
            ry = fact(ui, rx, ry, colW, Ui.tr("skirmish.hwos.profile.storage"), EconomyApp.money(base.value()));
        }
        NavigatorModule nav = NavigatorModule.instance();
        ry = fact(ui, rx, ry, colW, Ui.tr("skirmish.hwos.profile.portals"), nav == null ? "—" : Integer.toString(nav.portalCount()));
        ry = fact(ui, rx, ry, colW, Ui.tr("skirmish.hwos.profile.waypoints"), Integer.toString(WaypointManager.get().currentServer().size()));
        var home = NavigatorModule.home();
        ry = fact(ui, rx, ry, colW, Ui.tr("skirmish.hwos.profile.home"), home == null ? Ui.tr("skirmish.hwos.profile.no_home") : home.coordsText());
        ry = fact(ui, rx, ry, colW, Ui.tr("skirmish.hwos.profile.prices"), Integer.toString(MarketModule.historyKeys().size()));
        s.unclip(ui);
        s.content(Math.max(ly, ry) - top, h);
    }

    private static float fact(Ui ui, float x, float y, float w, String label, String value) {
        float vw = Math.min(w * 0.65f, ui.textWidth("hwos_value", value));
        ui.text("hwos_value", ui.ellipsize("hwos_value", value, w * 0.65f), x + w - vw, y);
        ui.text("hwos_row", ui.ellipsize("hwos_row", label, w - vw - 10), x, y, ui.color("text_2"));
        return y + ui.lineHeight("hwos_row") + 5;
    }
}
