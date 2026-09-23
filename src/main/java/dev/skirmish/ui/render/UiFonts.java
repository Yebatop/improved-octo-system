package dev.skirmish.ui.render;

import dev.skirmish.ui.Theme;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Face;

/**
 * Glyph loading tweaks for the UI kit's own TTF fonts (Manrope, JetBrains Mono) so they look like the browser
 * rendering of the mockup; other fonts are untouched. Rendering only.
 */
public final class UiFonts {
    /** FreeType FT_LOAD_NO_HINTING: fractional advances and unsnapped stems. */
    private static final int FT_LOAD_NO_HINTING = 1 << 1;
    private static volatile byte[] coverageTable;

    private UiFonts() {
    }

    public static boolean isUiFont(FT_Face face) {
        String family = face.family_nameString();
        return family != null && (family.startsWith("Manrope") || family.startsWith("JetBrains Mono"));
    }

    public static int loadFlags(FT_Face face, int flags) {
        return isUiFont(face) ? flags | FT_LOAD_NO_HINTING : flags;
    }

    /**
     * Applies {@code font.coverage_gamma} to an 8-bit coverage bitmap. Browsers boost the coverage of light text on
     * dark backgrounds; without it the same outlines look a weight lighter in game.
     */
    public static void adjustCoverage(long pixels, int count) {
        byte[] table = coverageTable;
        if (table == null) {
            table = new byte[256];
            double gamma = Theme.get().num("font.coverage_gamma");
            for (int i = 0; i < 256; i++) {
                table[i] = (byte) Math.round(255.0 * Math.pow(i / 255.0, 1.0 / gamma));
            }
            coverageTable = table;
        }
        for (int i = 0; i < count; i++) {
            long address = pixels + i;
            MemoryUtil.memPutByte(address, table[MemoryUtil.memGetByte(address) & 0xFF]);
        }
    }
}
