package dev.skirmish.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.List;

/**
 * The scoreboard sidebar the server shows now, read with the same objective choice, order and 15-line limit as
 * {@code Gui.displayScoreboardSidebar}, and where vanilla draws it this frame in GUI px (same width rule). HUD
 * elements left at their default place move out of it (HolyWorld keeps its sidebar on screen all the time).
 */
public final class SidebarBounds {
    private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value)
            .reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static final int LINE = 9;

    /** One line: the holder's name formatted for its team (prefix + name + suffix) and the formatted score. */
    public record Row(Component name, Component score) {
    }

    public record Sidebar(Component title, List<Row> rows) {
    }

    private SidebarBounds() {
    }

    /** The sidebar vanilla would draw now, or null (no objective in the team's or the sidebar slot). */
    public static @Nullable Sidebar read(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            return null;
        }
        Scoreboard scoreboard = mc.level.getScoreboard();
        Objective objective = null;
        PlayerTeam team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (team != null) {
            DisplaySlot slot = DisplaySlot.teamColorToSlot(team.getColor());
            if (slot != null) {
                objective = scoreboard.getDisplayObjective(slot);
            }
        }
        if (objective == null) {
            objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        }
        if (objective == null) {
            return null;
        }
        NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
        List<Row> rows = scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(ORDER)
                .limit(15)
                .map(entry -> new Row(PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()),
                        entry.formatValue(format)))
                .toList();
        return new Sidebar(objective.getDisplayName(), rows);
    }

    /** {x, y, w, h} in GUI px of the vanilla sidebar, or null when none is shown. */
    static float @Nullable [] gui(Minecraft mc, int guiWidth, int guiHeight) {
        if (mc.options.hideGui) {
            return null;
        }
        Sidebar sidebar = read(mc);
        if (sidebar == null) {
            return null;
        }
        var font = mc.font;
        int width = font.width(sidebar.title());
        int colon = font.width(": ");
        for (Row row : sidebar.rows()) {
            int score = font.width(row.score());
            width = Math.max(width, font.width(row.name()) + (score > 0 ? colon + score : 0));
        }
        int lines = sidebar.rows().size();
        int bottom = guiHeight / 2 + lines * LINE / 3;
        int top = bottom - lines * LINE - LINE - 1;
        int left = guiWidth - width - 3 - 2;
        return new float[]{left, top, guiWidth - left, bottom - top};
    }
}
