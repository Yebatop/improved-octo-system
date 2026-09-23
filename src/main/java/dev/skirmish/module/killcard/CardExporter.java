package dev.skirmish.module.killcard;

import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Render → PNG → file for one card. Blocking; runs on the card thread. */
public final class CardExporter {
    /**
     * @param file  saved PNG, null when rendering or saving failed ({@code error} is set)
     * @param image the rendered card (for the in-game preview), null on failure
     */
    public record Outcome(@Nullable Path file, int width, int height, String font, List<String> warnings,
                          long renderMs, long encodeMs, int pngBytes, long saveMs,
                          @Nullable BufferedImage image, @Nullable Throwable error) {
        public boolean saved() {
            return file != null;
        }
    }

    private CardExporter() {
    }

    public static Outcome export(KillCardData data, Path directory) {
        long t0 = System.nanoTime();
        CardRenderer.Result rendered;
        try {
            rendered = CardRenderer.render(data);
        } catch (Throwable t) {
            return new Outcome(null, 0, 0, "", List.of(), ms(t0), 0, 0, 0, null, t);
        }
        long renderMs = ms(t0);
        int w = rendered.image().getWidth();
        int h = rendered.image().getHeight();

        long t1 = System.nanoTime();
        byte[] png;
        try {
            png = encode(rendered);
        } catch (Throwable t) {
            return new Outcome(null, w, h, rendered.fontFamily(), rendered.warnings(), renderMs, ms(t1), 0, 0, null, t);
        }
        long encodeMs = ms(t1);

        long t2 = System.nanoTime();
        Path file;
        try {
            file = CardFiles.writeUnique(directory, CardFiles.baseName(data.stats().victim(), data.stats().time()), png);
        } catch (Throwable t) {
            return new Outcome(null, w, h, rendered.fontFamily(), rendered.warnings(), renderMs, encodeMs, png.length, ms(t2), null, t);
        }
        long saveMs = ms(t2);

        return new Outcome(file, w, h, rendered.fontFamily(), rendered.warnings(), renderMs, encodeMs, png.length, saveMs, rendered.image(), null);
    }

    private static byte[] encode(CardRenderer.Result rendered) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(256 * 1024);
        if (!ImageIO.write(rendered.image(), "png", out)) {
            throw new IOException("No PNG writer available");
        }
        return out.toByteArray();
    }

    private static long ms(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
