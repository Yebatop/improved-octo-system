package dev.skirmish.module.pvp;

import dev.skirmish.module.pvp.mixin.BossHealthOverlayAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Reads what the vanilla HUD shows: the sidebar (same objective, order and 15-line limit as {@code Gui}) and the
 * boss bars. Read only; called on the client thread.
 */
final class ServerSurfaces {
    private static final Comparator<PlayerScoreEntry> SIDEBAR_ORDER = Comparator.comparing(PlayerScoreEntry::value)
            .reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static final int SIDEBAR_LINES = 15;

    /** Sidebar title, line texts and the score numbers next to them (for the debug dump). */
    record Board(String title, List<String> lines, List<Integer> scores) {
        static final Board EMPTY = new Board("", List.of(), List.of());
    }

    private ServerSurfaces() {
    }

    static Board board(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return Board.EMPTY;
        }
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = sidebar(scoreboard, mc.player.getScoreboardName());
        if (objective == null) {
            return Board.EMPTY;
        }
        List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(SIDEBAR_ORDER)
                .limit(SIDEBAR_LINES)
                .toList();
        List<String> lines = new ArrayList<>(entries.size());
        List<Integer> scores = new ArrayList<>(entries.size());
        for (PlayerScoreEntry entry : entries) {
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            lines.add(PlayerTeam.formatNameForTeam(team, entry.ownerName()).getString());
            scores.add(entry.value());
        }
        return new Board(objective.getDisplayName().getString(), lines, scores);
    }

    /** The team-colour sidebar if the player's team has one, else the normal sidebar (as {@code Gui} picks it). */
    private static @Nullable Objective sidebar(Scoreboard scoreboard, String playerName) {
        PlayerTeam team = scoreboard.getPlayersTeam(playerName);
        if (team != null) {
            DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
            if (slot != null) {
                Objective objective = scoreboard.getDisplayObjective(slot);
                if (objective != null) {
                    return objective;
                }
            }
        }
        return scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
    }

    static List<TagParser.BossBar> bossBars(Minecraft mc) {
        List<TagParser.BossBar> bars = new ArrayList<>();
        for (LerpingBossEvent event : ((BossHealthOverlayAccessor) mc.gui.getBossOverlay()).skirmish$events().values()) {
            bars.add(new TagParser.BossBar(event.getName().getString(), event.getProgress()));
        }
        return bars;
    }
}
