package dev.skirmish.module.events;

import dev.skirmish.holyworld.HolyWorld;
import dev.skirmish.ui.Ui;
import dev.skirmish.util.ServerContext;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * System chat lines ({@code ClientReceiveMessageEvents.MODIFY_GAME}): feeds the module (sub-server join lines,
 * vote starts, event coordinates) and, when enabled, puts a clickable " [+метка]" after every coordinate triple.
 * The click runs {@code /skirmish wp add …}, a client-only Fabric command that never reaches the server.
 */
final class ChatWaypoints {
    private static final int MAX_NAME = 32;

    private final EventsModule module;

    ChatWaypoints(EventsModule module) {
        this.module = module;
    }

    private record Segment(Style style, String text) {
    }

    Component modify(Component message, boolean overlay) {
        if (overlay || !module.isEnabled()) {
            return message;
        }
        boolean holy = HolyWorld.isConnected();
        try {
            List<Segment> segments = new ArrayList<>();
            StringBuilder plain = new StringBuilder();
            boolean ours = false;
            for (Segment s : flatten(message)) {
                segments.add(s);
                plain.append(s.text());
                if (s.style().getClickEvent() instanceof ClickEvent.RunCommand(String command)
                        && command.replaceFirst("^/", "").startsWith("skirmish")) {
                    ours = true;
                }
            }
            String text = plain.toString();
            List<ChatCoords.Coords> found = ours ? List.of() : ChatCoords.find(text);
            String event = found.isEmpty() ? null : ChatCoords.eventName(text, module.liveEventNames());
            String dimension = dimension(text, event);
            if (holy) {
                module.onChatLine(text, found, event, dimension);
            }
            if (found.isEmpty() || !module.waypoints.get() || !(holy || module.everywhere.get())) {
                return message;
            }
            module.log("chat coordinates %s, event %s, dimension %s (line: %s)", found.getFirst().text(), event, dimension, text);
            return withLinks(segments, found, event, dimension);
        } catch (RuntimeException e) {
            module.error("chat line not processed", e);
            return message;
        }
    }

    private static List<Segment> flatten(Component message) {
        List<Segment> out = new ArrayList<>();
        message.visit((style, text) -> {
            if (!text.isEmpty()) {
                out.add(new Segment(style, text));
            }
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    /** Named in the line, else the overworld for event announcements, else where the player is. */
    private static String dimension(String text, @Nullable String event) {
        String hint = ChatCoords.dimensionHint(text);
        if (hint != null) {
            return hint;
        }
        if (event != null) {
            return "minecraft:overworld";
        }
        String here = ServerContext.dimension();
        return here.equals(ServerContext.UNKNOWN) ? "minecraft:overworld" : here;
    }

    /** The original segments with a link inserted after each match. */
    private static Component withLinks(List<Segment> segments, List<ChatCoords.Coords> found, @Nullable String event, String dimension) {
        MutableComponent out = Component.empty();
        int offset = 0;
        int next = 0;
        for (Segment segment : segments) {
            String text = segment.text();
            int start = offset;
            int end = offset + text.length();
            int cut = 0;
            while (next < found.size() && found.get(next).end() <= end) {
                int at = found.get(next).end() - start;
                if (at > cut) {
                    out.append(Component.literal(text.substring(cut, at)).withStyle(segment.style()));
                    cut = at;
                }
                out.append(link(found.get(next), event, dimension, found.size() > 1 ? next + 1 : 0));
                next++;
            }
            if (cut < text.length()) {
                out.append(Component.literal(text.substring(cut)).withStyle(segment.style()));
            }
            offset = end;
        }
        while (next < found.size()) {
            out.append(link(found.get(next), event, dimension, found.size() > 1 ? next + 1 : 0));
            next++;
        }
        return out;
    }

    private static Component link(ChatCoords.Coords c, @Nullable String event, String dimension, int index) {
        int y = c.y() != null ? c.y() : fallbackY();
        String base = event != null ? event : Ui.tr("skirmish.events.waypoint.default");
        String name = clean(index > 0 && event == null ? base + " " + index : base);
        String command = "/skirmish wp add " + c.x() + " " + y + " " + c.z() + " \"" + dimension + "\" " + name;
        Component hover = Component.translatable("skirmish.events.chat.hover",
                Component.literal(name).withStyle(ChatFormatting.WHITE),
                Component.literal(c.x() + " " + y + " " + c.z()).withStyle(ChatFormatting.WHITE),
                Component.literal(dimension.replace("minecraft:", "")).withStyle(ChatFormatting.GRAY));
        return Component.literal(" ").append(Component.translatable("skirmish.events.chat.add").withStyle(style -> style
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(false)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(hover))));
    }

    /** No height in the message: the player's own block Y (the arrow is what matters), else sea level + 1. */
    private static int fallbackY() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null ? mc.player.getBlockY() : 64;
    }

    private static String clean(String name) {
        String n = name.replace('"', '\'').replaceAll("[\\p{Cntrl}§]", "").trim();
        if (n.isEmpty()) {
            n = "Waypoint";
        }
        return n.length() > MAX_NAME ? n.substring(0, MAX_NAME) : n;
    }
}
