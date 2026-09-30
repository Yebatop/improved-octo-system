package dev.skirmish.module.worldmap;

/**
 * How a map pixel is shaded: vanilla map items' three steps, and the smooth relief and water depth of the
 * texture-coloured map. Pure Java: brightness ids are {@code MapColor.Brightness} ids (0 LOW, 1 NORMAL, 2 HIGH).
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

    /**
     * Relief for the texture-coloured map: lit from the north-west like a hill-shaded map, brighter where the ground
     * rises towards the south-east viewer and darker where it falls (height steps against the north and west
     * neighbours, capped).
     */
    static float relief(int height, int north, int west) {
        int d = Math.max(-4, Math.min(4, height - north)) + Math.max(-4, Math.min(4, height - west));
        return Math.max(0.62f, Math.min(1.3f, 1f + d * 0.07f));
    }

    /** Water over its floor: the floor shows through shallow water, deep water is its own colour, a bit darker. */
    static int seeThrough(int water, int floor, int depth) {
        float cover = Math.min(0.92f, 0.58f + depth * 0.07f);
        int mixed = lerp(floor, water, cover);
        return scale(mixed, 1f - Math.min(0.3f, depth * 0.02f));
    }

    /** Channel-wise product of two RGB colours (a texture and its biome tint). */
    static int multiply(int rgb, int tint) {
        int r = ((rgb >> 16) & 0xFF) * ((tint >> 16) & 0xFF) / 255;
        int g = ((rgb >> 8) & 0xFF) * ((tint >> 8) & 0xFF) / 255;
        int b = (rgb & 0xFF) * (tint & 0xFF) / 255;
        return r << 16 | g << 8 | b;
    }

    static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * k));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * k));
        int b = Math.min(255, Math.round((rgb & 0xFF) * k));
        return (rgb & 0xFF000000) | r << 16 | g << 8 | b;
    }

    static int lerp(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (a & 0xFF000000) | r << 16 | g << 8 | bl;
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
