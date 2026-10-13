package dev.skirmish.module.recap;

import dev.skirmish.module.killcard.CardFiles;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Render → PNG → file, the KillCard way ({@link CardFiles#writeUnique}, never overwrites). Blocking; export thread. */
final class RecapExporter {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);

    private RecapExporter() {
    }

    /** {@code recap_2026-09-25_21.04.10_Notch} (no extension). */
    static String baseName(String player, ZonedDateTime time) {
        return "recap_" + STAMP.format(time) + "_" + CardFiles.sanitize(player);
    }

    static Path export(RecapCard.Data data, Path directory, String baseName) throws IOException {
        BufferedImage image = RecapCard.render(data);
        ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("No PNG writer available");
        }
        return CardFiles.writeUnique(directory, baseName, out.toByteArray());
    }
}
