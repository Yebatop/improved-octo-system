package dev.skirmish.module.events;

import dev.skirmish.module.events.mixin.PlayerTabOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Collects what the client shows about the current sub-server (sidebar title and lines, tab header/footer,
 * server brand, recent join lines from chat) and feeds it to {@link ServerParser}. Client thread only.
 */
final class ServerDetector {
    private static final String[] JOIN_WORDS = {"подключ", "переход", "перемещ", "добро пожаловать", "вы на ",
            "вы зашли", "вы вошли", "телепорт", "connect", "welcome", "сервер"};

    private ServerParser.@Nullable ServerRef fromChat;
    private ServerParser.Mode lastMode = ServerParser.Mode.UNKNOWN;

    void reset() {
        fromChat = null;
        lastMode = ServerParser.Mode.UNKNOWN;
    }

    /** Mode named by the sidebar, tab list or brand at the last {@link #detect}. */
    ServerParser.Mode mode() {
        return lastMode;
    }

    /** A system chat line: remembers a sub-server named in a join/transfer message. */
    void onChat(String plain, Map<String, String> servers) {
        String lower = ServerParser.normalize(plain);
        boolean joinLike = false;
        for (String word : JOIN_WORDS) {
            if (lower.contains(word)) {
                joinLike = true;
                break;
            }
        }
        if (!joinLike) {
            return;
        }
        ServerParser.ServerRef ref = ServerParser.parse(plain, servers, false, ServerParser.Mode.UNKNOWN, "chat");
        if (ref != null) {
            fromChat = ref;
        }
    }

    /** Best guess now: sidebar and tab list (current state) first, then the last join line. */
    ServerParser.@Nullable ServerRef detect(Minecraft mc, Map<String, String> servers) {
        List<String[]> texts = texts(mc);
        ServerParser.Mode hint = ServerParser.Mode.UNKNOWN;
        for (String[] t : texts) {
            ServerParser.Mode m = ServerParser.mode(t[1]);
            if (m != ServerParser.Mode.UNKNOWN) {
                hint = m;
                break;
            }
        }
        if (hint == ServerParser.Mode.UNKNOWN && mc.getConnection() != null) {
            String brand = mc.getConnection().serverBrand();
            if (brand != null) {
                hint = ServerParser.mode(brand);
            }
        }
        lastMode = hint;
        for (String[] t : texts) {
            ServerParser.ServerRef ref = ServerParser.parse(t[1], servers, true, hint, t[0]);
            if (ref != null) {
                return ref;
            }
        }
        return fromChat;
    }

    /** (source, text) pairs in priority order. */
    private static List<String[]> texts(Minecraft mc) {
        List<String[]> out = new ArrayList<>();
        if (mc.level != null) {
            Scoreboard scoreboard = mc.level.getScoreboard();
            Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
            if (sidebar != null) {
                out.add(new String[]{"sidebar title", sidebar.getDisplayName().getString()});
                for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
                    if (entry.isHidden()) {
                        continue;
                    }
                    PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
                    out.add(new String[]{"sidebar", PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString()});
                }
            }
        }
        PlayerTabOverlayAccessor tab = (PlayerTabOverlayAccessor) mc.gui.getTabList();
        add(out, "tab header", tab.skirmish$header());
        add(out, "tab footer", tab.skirmish$footer());
        return out;
    }

    private static void add(List<String[]> out, String source, @Nullable Component text) {
        if (text != null) {
            for (String line : text.getString().split("\n")) {
                if (!line.isBlank()) {
                    out.add(new String[]{source, line});
                }
            }
        }
    }
}
