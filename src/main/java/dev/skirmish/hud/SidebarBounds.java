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
 * Where vanilla draws the scoreboard sidebar this frame, in GUI px, with the same objective choice, order, 15-line
 * limit and width rule as {@code Gui.displayScoreboardSidebar}. HUD elements left at their default place move out
 * of it (HolyWorld keeps its sidebar on screen all the time).
 */
final class SidebarBounds {
    private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value)
            .reversed()
            .thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
    private static final int LINE = 9;

    private SidebarBounds() {
    }

    /** {x, y, w, h} in GUI px, or null when no sidebar is shown. */
    static float @Nullable [] gui(Minecraft mc, int guiWidth, int guiHeight) {
        if (mc.level == null || mc.player == null || mc.options.hideGui) {
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
        List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(ORDER)
                .limit(15)
                .toList();
        var font = mc.font;
        int width = font.width(objective.getDisplayName());
        int colon = font.width(": ");
        for (PlayerScoreEntry entry : entries) {
            Component name = PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName());
            int score = font.width(entry.formatValue(format));
            width = Math.max(width, font.width(name) + (score > 0 ? colon + score : 0));
        }
        int lines = entries.size();
        int bottom = guiHeight / 2 + lines * LINE / 3;
        int top = bottom - lines * LINE - LINE - 1;
        int left = guiWidth - width - 3 - 2;
        return new float[]{left, top, guiWidth - left, bottom - top};
    }
}
