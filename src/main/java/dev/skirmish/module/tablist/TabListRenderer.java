package dev.skirmish.module.tablist;

import com.mojang.authlib.GameProfile;
import dev.skirmish.module.friends.Friends;
import dev.skirmish.module.tablist.mixin.PlayerTabOverlayAccessor;
import dev.skirmish.ui.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Draws the Tab List panel. Layout is in GUI px like vanilla's list; the server's texts (header, footer, names,
 * scores) keep the game font so their glyphs and colours show, while the panel, the row marks, the count line and
 * the ping numbers are the mod's.
 */
public final class TabListRenderer {
    private static final String L = "layout.tablist.";
    private static final int MAX_ROWS = 20;
    private static final int ROW_H = 11;
    private static final int FACE = 8;

    private TabListRenderer() {
    }

    private record Row(PlayerInfo info, Component name, int nameW, @Nullable Component score, int scoreW, boolean me, boolean friend) {
    }

    /** Draws the list; false when the module is off (vanilla draws its own). */
    public static boolean render(PlayerTabOverlay overlay, GuiGraphics graphics, int width, Scoreboard scoreboard, @Nullable Objective objective) {
        TabListModule m = TabListModule.active();
        Minecraft mc = Minecraft.getInstance();
        if (m == null || mc.player == null || mc.level == null) {
            return false;
        }
        PlayerTabOverlayAccessor access = (PlayerTabOverlayAccessor) overlay;
        Font font = mc.font;
        List<PlayerInfo> infos = access.skirmish$players();
        boolean heads = m.heads.get();
        boolean markFriends = m.friends.get();
        java.util.UUID myId = mc.player.getUUID();

        List<Row> rows = new ArrayList<>(infos.size());
        int nameW = 0;
        int scoreW = 0;
        boolean hearts = objective != null && objective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS;
        for (PlayerInfo info : infos) {
            GameProfile profile = info.getProfile();
            Component name = overlay.getNameForDisplay(info);
            Component score = null;
            if (objective != null) {
                ReadOnlyScoreInfo s = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(profile), objective);
                score = hearts
                        ? Component.literal(s == null ? "" : "❤ " + Math.round(s.value() / 2f))
                        : ReadOnlyScoreInfo.safeFormatValue(s, objective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT));
            }
            int sw = score == null ? 0 : font.width(score);
            int nw = font.width(name);
            nameW = Math.max(nameW, nw);
            scoreW = Math.max(scoreW, sw);
            boolean me = Objects.equals(profile.id(), myId);
            boolean friend = markFriends && !me && Friends.isFriend(profile.id(), profile.name());
            rows.add(new Row(info, name, nw, score, sw, me, friend));
        }

        switch (m.sort.get()) {
            case NAME -> rows.sort(java.util.Comparator.comparing(r -> r.info().getProfile().name(), String.CASE_INSENSITIVE_ORDER));
            case PING -> rows.sort(java.util.Comparator.comparingInt(r -> r.info().getLatency() < 0 ? Integer.MAX_VALUE : r.info().getLatency()));
            case SERVER -> {
            }
        }
        List<FormattedCharSequence> header = split(font, access.skirmish$header(), width - 50);
        List<FormattedCharSequence> footer = split(font, access.skirmish$footer(), width - 50);
        int textW = 0;
        for (FormattedCharSequence line : header) {
            textW = Math.max(textW, font.width(line));
        }
        for (FormattedCharSequence line : footer) {
            textW = Math.max(textW, font.width(line));
        }

        // Columns as tall as the screen allows (at least vanilla's 20), so a full server needs fewer, wider ones.
        int n = rows.size();
        int room = graphics.guiHeight() - 8 - 14 - header.size() * 9 - footer.size() * 9 - 12 - 3 - 20 - 16;
        int maxRows = Math.max(MAX_ROWS, room / ROW_H);
        int cols = Math.max(1, (n + maxRows - 1) / maxRows);
        int perCol = Math.max(1, (n + cols - 1) / cols);
        int pingW = font.width("9999") + 2;
        int fixedW = 4 + (heads ? FACE + 3 : 0) + (scoreW > 0 ? 6 + scoreW : 0) + 6 + pingW + 4;
        int colGap = 4;
        int maxW = width - 24;
        int cellW = fixedW + nameW;
        if (cols * cellW + (cols - 1) * colGap > maxW) {
            cellW = Math.max(fixedW + 30, (maxW - (cols - 1) * colGap) / cols);
        }
        int nameRoom = cellW - fixedW;
        // A name with the server's long prefix that does not fit shows as the nick alone, in the nick's own colour.
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            if (r.nameW() > nameRoom) {
                Component nick = nickOnly(r.name(), r.info().getProfile().name());
                rows.set(i, new Row(r.info(), nick, font.width(nick), r.score(), r.scoreW(), r.me(), r.friend()));
            }
        }
        int gridW = cols * cellW + (cols - 1) * colGap;

        float s = Ui.designScale();
        int pad = 7;
        int metaH = 12;
        int panelW = Math.max(gridW, Math.max(textW, 160)) + pad * 2;
        int panelX = (width - panelW) / 2;
        int panelY = 8;
        int y = panelY + pad;
        int headerY = y;
        y += header.size() * 9 + (header.isEmpty() ? 0 : 5);
        int metaY = y;
        y += metaH + 3;
        int gridX = panelX + (panelW - gridW) / 2;
        int gridY = y;
        y += perCol * ROW_H;
        int footerY = y + (footer.isEmpty() ? 0 : 5);
        y = footerY + footer.size() * 9;
        int panelH = y + pad - panelY;

        // Like vanilla's list, it covers the HUD: the mod's blocks under the panel step back while it shows.
        dev.skirmish.hud.Hud.get().cover(panelX / Ui.designScale(), panelY / Ui.designScale(), panelW / Ui.designScale(), panelH / Ui.designScale());
        // Shapes, count line and ping numbers (the mod's).
        Ui ui = Ui.begin(graphics);
        try {
            ui.pushAlpha(m.opacity.get().floatValue() / 100f);
            ui.box(panelX / s, panelY / s, panelW / s, panelH / s, ui.num(L + "radius"), ui.color("window"), ui.color("stroke"));
            ui.popAlpha();
            if (!header.isEmpty()) {
                ui.rect((panelX + pad) / s, (metaY - 3) / s, (panelW - pad * 2) / s, 1f, 0f, ui.color("stroke"));
            }
            String online = Ui.tr("skirmish.tablist.online", mc.player.connection.getListedOnlinePlayers().size());
            ui.textCentered("tab_meta", online, (panelX + pad) / s, metaY / s, metaH / s);
            PlayerInfo mine = mc.player.connection.getPlayerInfo(myId);
            if (mine != null) {
                String ping = Ui.tr("skirmish.tablist.your_ping", mine.getLatency());
                ui.textCentered("tab_meta", ping, (panelX + panelW - pad) / s - ui.textWidth("tab_meta", ping), metaY / s, metaH / s);
            }
            for (int i = 0; i < n; i++) {
                Row r = rows.get(i);
                int cx = gridX + (i / perCol) * (cellW + colGap);
                int cy = gridY + (i % perCol) * ROW_H;
                int bg = r.me() ? tint(ui.color("accent"), 0x40) : r.friend() ? tint(ui.color("good"), 0x33) : ui.color("fill_04");
                ui.rect(cx / s, cy / s, cellW / s, (ROW_H - 1) / s, ui.num(L + "row_radius"), bg);
                int latency = r.info().getLatency();
                String ping = latency < 0 ? "—" : Integer.toString(latency);
                float pw = ui.textWidth("tab_ping", ping);
                ui.textCentered("tab_ping", ping, (cx + cellW - 4) / s - pw, cy / s, (ROW_H - 1) / s, ui.color(pingColor(latency)));
            }
        } finally {
            ui.end();
        }

        // The server's texts and the heads (game font, their own colours).
        int ty = headerY;
        for (FormattedCharSequence line : header) {
            graphics.drawString(font, line, width / 2 - font.width(line) / 2, ty, -1);
            ty += 9;
        }
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            int cx = gridX + (i / perCol) * (cellW + colGap) + 4;
            int cy = gridY + (i % perCol) * ROW_H + 1;
            if (heads) {
                Player player = mc.level.getPlayerByUUID(r.info().getProfile().id());
                boolean upsideDown = player != null && AvatarRenderer.isPlayerUpsideDown(player);
                PlayerFaceRenderer.draw(graphics, r.info().getSkin().body().texturePath(), cx, cy, FACE, r.info().showHat(), upsideDown, -1);
                cx += FACE + 3;
            }
            boolean spectator = r.info().getGameMode() == GameType.SPECTATOR;
            // Each name stays in its own cell: whatever is still too long is cut at the cell's edge.
            graphics.enableScissor(cx, cy - 1, cx + nameRoom, cy + ROW_H - 1);
            graphics.drawString(font, r.name(), cx, cy, spectator ? 0x90FFFFFF : -1);
            graphics.disableScissor();
            if (r.score() != null && r.scoreW() > 0 && !spectator) {
                int right = gridX + (i / perCol) * (cellW + colGap) + cellW - 4 - pingW - 6;
                graphics.drawString(font, r.score(), right - r.scoreW(), cy, hearts ? 0xFFFF5555 : -1);
            }
        }
        ty = footerY;
        for (FormattedCharSequence line : footer) {
            graphics.drawString(font, line, width / 2 - font.width(line) / 2, ty, -1);
            ty += 9;
        }
        return true;
    }

    /** The nick alone, in the style the display name gives it (its prefixes left out). */
    static Component nickOnly(Component display, String nick) {
        Style[] found = {null};
        display.visit((style, part) -> {
            if (found[0] == null && part.contains(nick)) {
                found[0] = style;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return Component.literal(nick).withStyle(found[0] == null ? Style.EMPTY : found[0]);
    }

    private static List<FormattedCharSequence> split(Font font, @Nullable Component text, int width) {
        return text == null || text.getString().isBlank() ? List.of() : font.split(text, width);
    }

    private static int tint(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (alpha << 24);
    }

    /** Colour token for a ping: green under 100 ms, then text, amber from 250, red from 500. */
    static String pingColor(int latency) {
        if (latency < 0) {
            return "text_3";
        }
        if (latency < 100) {
            return "good";
        }
        if (latency < 250) {
            return "text_2";
        }
        return latency < 500 ? "warn" : "bad";
    }
}
