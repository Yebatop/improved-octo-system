package dev.skirmish.module.killcam;

import dev.skirmish.module.killcam.library.ReplayHeader;
import dev.skirmish.ui.Ui;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Texts of saved replays shared by the library and the replay bar. */
final class ReplayTexts {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM", Locale.ROOT);
    private static final DateTimeFormatter DAY_YEAR = DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ROOT);

    private ReplayTexts() {
    }

    /** «сегодня 14:05», «вчера 23:10», «12.09 18:00», «12.09.2025 18:00». */
    static String date(long epochMs) {
        ZonedDateTime at = Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault());
        ZonedDateTime now = ZonedDateTime.now();
        String time = TIME.format(at);
        if (at.toLocalDate().equals(now.toLocalDate())) {
            return Ui.tr("skirmish.killcam.library.today", time);
        }
        if (at.toLocalDate().equals(now.toLocalDate().minusDays(1))) {
            return Ui.tr("skirmish.killcam.library.yesterday", time);
        }
        return (at.getYear() == now.getYear() ? DAY : DAY_YEAR).format(at) + " " + time;
    }

    /** m:ss of a recording. */
    static String duration(ReplayHeader header) {
        return Ui.duration(Math.round(header.seconds() * 1000));
    }

    /** Badge label: Победа / Смерть / Клип. */
    static String badge(ReplayHeader header) {
        return Ui.tr("skirmish.killcam.library.badge." + header.kind.id());
    }

    /** The user's name for it, or «Notch» / «Клип». */
    static String title(ReplayHeader header) {
        if (!header.title.isBlank()) {
            return header.title;
        }
        return switch (header.kind) {
            case KILL -> header.opponent.isEmpty() ? Ui.tr("skirmish.killcam.library.auto.kill_unknown")
                    : Ui.tr("skirmish.killcam.library.auto.kill", header.opponent);
            case DEATH -> header.opponent.isEmpty() ? Ui.tr("skirmish.killcam.library.auto.death_unknown")
                    : Ui.tr("skirmish.killcam.library.auto.death", header.opponent);
            case CLIP -> header.opponent.isEmpty() ? Ui.tr("skirmish.killcam.library.auto.clip")
                    : Ui.tr("skirmish.killcam.library.auto.clip_with", header.opponent);
        };
    }

    /** «сегодня 14:05 · 0:10 · mc.holyworld.ru · 5 ударов · 1 тотем». */
    static String details(ReplayHeader header) {
        List<String> parts = new ArrayList<>();
        parts.add(date(header.createdMs));
        parts.add(duration(header));
        if (!header.server.isEmpty()) {
            parts.add(header.server + dimension(header.dimension));
        }
        if (header.hits > 0) {
            parts.add(header.hits + " " + Ui.plural("skirmish.killcam.library.count.hit", header.hits));
        }
        if (header.totems > 0) {
            parts.add(header.totems + " " + Ui.plural("skirmish.killcam.library.count.totem", header.totems));
        }
        return String.join(" · ", parts);
    }

    private static String dimension(String id) {
        return switch (id) {
            case "minecraft:overworld", "" -> "";
            case "minecraft:the_nether" -> " (" + Ui.tr("skirmish.killcam.library.dimension.nether") + ")";
            case "minecraft:the_end" -> " (" + Ui.tr("skirmish.killcam.library.dimension.end") + ")";
            default -> " (" + id.substring(id.indexOf(':') + 1) + ")";
        };
    }

    /** «12,4 МБ». */
    static String megabytes(long bytes) {
        return Ui.decimal(bytes / (1024.0 * 1024.0), 1) + " " + Ui.tr("skirmish.unit.mb");
    }
}
