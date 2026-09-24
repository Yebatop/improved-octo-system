package dev.skirmish.module.coords;

/** Pure coordinate math for the coordinates HUD (unit tested). */
public final class CoordMath {
    /** Horizontal scale between the Overworld and the Nether (vanilla dimension coordinate_scale 1 vs 8). */
    public static final double NETHER_SCALE = 8.0;

    private CoordMath() {
    }

    /** Block coordinate of a position (floor, so -0.5 is block -1). */
    public static long block(double coordinate) {
        return (long) Math.floor(coordinate);
    }

    /** Overworld X/Z to the matching Nether block coordinate. */
    public static long toNether(double overworld) {
        return block(overworld / NETHER_SCALE);
    }

    /** Nether X/Z to the matching Overworld block coordinate. */
    public static long toOverworld(double nether) {
        return block(nether * NETHER_SCALE);
    }

    /** The four facings, in the order vanilla's yaw runs: 0° is south (+Z), 90° west (-X). */
    public enum Facing {
        SOUTH("+Z"), WEST("-X"), NORTH("-Z"), EAST("+X");

        private final String axis;

        Facing(String axis) {
            this.axis = axis;
        }

        public String axis() {
            return axis;
        }
    }

    /** Facing for a yaw in degrees (any range). */
    public static Facing facing(double yaw) {
        double wrapped = ((yaw % 360.0) + 360.0) % 360.0;
        int index = (int) Math.floor((wrapped + 45.0) / 90.0) & 3;
        return Facing.values()[index];
    }
}
