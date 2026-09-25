package dev.skirmish.module.worldmap;

/**
 * How a map pixel is shaded, the way vanilla map items do it. Pure Java: brightness ids are
 * {@code MapColor.Brightness} ids (0 LOW, 1 NORMAL, 2 HIGH).
 */
final class MapShade {
    static final int LOW = 0;
    static final int NORMAL = 1;
    static final int HIGH = 2;

    private MapShade() {
    }

    /** Land: brighter when higher than the block to the north, darker when lower. */
    static int land(int height, int northHeight) {
        return height > northHeight ? HIGH : height < northHeight ? LOW : NORMAL;
    }

    /** Water: shallow is bright, deep is dark, with vanilla's checkerboard dither. */
    static int water(int depth, int x, int z) {
        double f = depth * 0.1 + ((x + z) & 1) * 0.2;
        return f < 0.5 ? HIGH : f > 0.9 ? LOW : NORMAL;
    }

    /** Region tile index of a chunk coordinate (32 chunks = 512 blocks per tile). */
    static int tileOf(int chunk) {
        return chunk >> 5;
    }

    /** Pixel inside a tile of a block coordinate. */
    static int pixelOf(int block) {
        return block & 511;
    }

    /** File-name safe form of a server key or dimension id. */
    static String safe(String s) {
        return s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
