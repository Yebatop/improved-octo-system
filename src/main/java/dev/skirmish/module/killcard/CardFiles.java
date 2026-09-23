package dev.skirmish.module.killcard;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** File names and writing of card PNGs. */
public final class CardFiles {
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH.mm.ss", Locale.ROOT);
    private static final int MAX_NAME = 32;
    private static final int MAX_ATTEMPTS = 1000;

    private CardFiles() {
    }

    /** {@code killcard_2026-09-23_14.05.33_Notch} (no extension). */
    public static String baseName(String victim, ZonedDateTime time) {
        return "killcard_" + STAMP.format(time) + "_" + sanitize(victim);
    }

    /** Letters, digits, '-' and '_' (Unicode letters kept, so Cyrillic nicks stay readable); anything else becomes '_'. */
    public static String sanitize(String name) {
        StringBuilder out = new StringBuilder();
        name.codePoints().limit(MAX_NAME).forEach(cp -> {
            boolean ok = cp == '-' || cp == '_' || (Character.isLetterOrDigit(cp) && Character.isBmpCodePoint(cp));
            out.append(ok ? Character.toString(cp) : "_");
        });
        String result = out.toString();
        return result.isEmpty() || result.chars().allMatch(c -> c == '_') ? "unknown" : result;
    }

    /** Writes {@code png} as {@code <base>.png}, or {@code <base>_2.png}, ... when taken. Never overwrites. */
    public static Path writeUnique(Path directory, String baseName, byte[] png) throws IOException {
        Files.createDirectories(directory);
        for (int i = 1; i <= MAX_ATTEMPTS; i++) {
            Path target = directory.resolve(i == 1 ? baseName + ".png" : baseName + "_" + i + ".png");
            try {
                Files.write(target, png, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                return target;
            } catch (FileAlreadyExistsException ignored) {
                // next suffix
            }
        }
        throw new IOException("No free file name for " + baseName + " in " + directory);
    }
}
