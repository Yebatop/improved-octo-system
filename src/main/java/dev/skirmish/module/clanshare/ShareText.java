package dev.skirmish.module.clanshare;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Chat components of ClanShare: the flattened text of a received line and the clickable label replacing a token. */
final class ShareText {
    static final String COMMAND = "skirmish-clanshare";

    private record Segment(Style style, String text) {
    }

    /** A received line as styled plain-text segments; offsets into {@link #plain()} match {@code ShareCodec.find}. */
    static final class Flat {
        private final List<Segment> segments = new ArrayList<>();
        private final StringBuilder plain = new StringBuilder();

        Flat(Component message) {
            message.visit((style, text) -> {
                if (!text.isEmpty()) {
                    segments.add(new Segment(style, text));
                    plain.append(text);
                }
                return Optional.empty();
            }, Style.EMPTY);
        }

        String plain() {
            return plain.toString();
        }

        /** The same line with chars [start, end) replaced by {@code replacement}; other styles are kept. */
        Component replace(int start, int end, Component replacement) {
            MutableComponent out = Component.empty();
            int offset = 0;
            boolean inserted = false;
            for (Segment segment : segments) {
                int segStart = offset;
                int segEnd = offset + segment.text().length();
                offset = segEnd;
                if (segStart < start) {
                    out.append(Component.literal(segment.text().substring(0, Math.min(segEnd, start) - segStart)).withStyle(segment.style()));
                }
                if (!inserted && segEnd >= start) {
                    out.append(replacement);
                    inserted = true;
                }
                if (segEnd > end) {
                    out.append(Component.literal(segment.text().substring(Math.max(segStart, end) - segStart)).withStyle(segment.style()));
                }
            }
            if (!inserted) {
                out.append(replacement);
            }
            return out;
        }
    }

    private ShareText() {
    }

    static MutableComponent prefix() {
        return Component.literal("[ClanShare] ").withStyle(ChatFormatting.GOLD);
    }

    static Component info(Component message) {
        return prefix().append(message.copy().withStyle(ChatFormatting.GRAY));
    }

    static Component error(Component message) {
        return prefix().append(message.copy().withStyle(ChatFormatting.RED));
    }

    /** «[Clan] Nick → name (x y z)», click = {@code /skirmish-clanshare add ...} (a client command, see ClanShareModule). */
    static Component label(SharePayload payload, String currentDimension, long ageSeconds) {
        Component name = payload.name().isEmpty()
                ? Component.translatable("skirmish.clanshare.default_name")
                : Component.literal(payload.name());
        MutableComponent coords = Component.literal(payload.coordsText());
        if (!payload.dimension().equals(currentDimension)) {
            coords.append(", ").append(dimensionName(payload.dimension()));
        }
        Component hover = Component.translatable("skirmish.clanshare.hover",
                Component.literal(payload.nick()).withStyle(ChatFormatting.WHITE),
                Component.literal(payload.coordsText()).withStyle(ChatFormatting.WHITE),
                dimensionName(payload.dimension()).copy().withStyle(ChatFormatting.WHITE),
                age(ageSeconds));
        Style style = Style.EMPTY
                .withColor(ChatFormatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/" + COMMAND + " " + payload.toCommandArgs()))
                .withHoverEvent(new HoverEvent.ShowText(hover));
        return Component.translatable("skirmish.clanshare.label",
                        Component.literal(payload.nick()).withStyle(ChatFormatting.WHITE),
                        name.copy().withStyle(ChatFormatting.YELLOW),
                        coords.withStyle(ChatFormatting.GRAY))
                .withStyle(style);
    }

    static Component dimensionName(String dimension) {
        return switch (dimension) {
            case "minecraft:overworld" -> Component.translatable("skirmish.clanshare.dimension.overworld");
            case "minecraft:the_nether" -> Component.translatable("skirmish.clanshare.dimension.the_nether");
            case "minecraft:the_end" -> Component.translatable("skirmish.clanshare.dimension.the_end");
            default -> Component.literal(dimension);
        };
    }

    private static Component age(long seconds) {
        long abs = Math.max(0, seconds);
        if (abs < 60) {
            return Component.translatable("skirmish.clanshare.age.seconds", abs);
        }
        if (abs < 3600) {
            return Component.translatable("skirmish.clanshare.age.minutes", abs / 60);
        }
        return Component.translatable("skirmish.clanshare.age.hours", abs / 3600);
    }
}
