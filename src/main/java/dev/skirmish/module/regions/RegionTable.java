package dev.skirmish.module.regions;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * HolyWorld region blocks and the size of the area each one protects, from the wiki's «Приваты» pages. Pure Java.
 *
 * <p>Lite: iron block 5×5, gold block 7×7, diamond block 11×11, emerald ore 21×21, netherite block 31×31 and the
 * unique region (ancient debris) 31×31; the wiki gives no height, so a Lite region is drawn as a column. Prime:
 * gold block 21³, copper block 37³, ancient debris 55³, resin block 77³ and flowering (budding) amethyst 55³. Every
 * area is centred on its block.
 */
public final class RegionTable {
    public enum Server {
        LITE, PRIME
    }

    /** A region kind: lang/theme id, side length in blocks, and whether the height is limited too (Prime cubes). */
    public record Type(String id, int size, boolean cube) {
    }

    private static final Map<String, Type> LITE = new HashMap<>();
    private static final Map<String, Type> PRIME = new HashMap<>();

    static {
        LITE.put("minecraft:iron_block", new Type("iron", 5, false));
        LITE.put("minecraft:gold_block", new Type("gold", 7, false));
        LITE.put("minecraft:diamond_block", new Type("diamond", 11, false));
        LITE.put("minecraft:emerald_ore", new Type("emerald", 21, false));
        LITE.put("minecraft:netherite_block", new Type("netherite", 31, false));
        LITE.put("minecraft:ancient_debris", new Type("unique", 31, false));

        PRIME.put("minecraft:gold_block", new Type("prime_gold", 21, true));
        Type copper = new Type("copper", 37, true);
        for (String stage : new String[]{"", "exposed_", "weathered_", "oxidized_"}) {
            PRIME.put("minecraft:" + stage + "copper_block", copper);
            PRIME.put("minecraft:waxed_" + stage + "copper_block", copper);
        }
        PRIME.put("minecraft:ancient_debris", new Type("debris", 55, true));
        PRIME.put("minecraft:resin_block", new Type("resin", 77, true));
        PRIME.put("minecraft:budding_amethyst", new Type("amethyst", 55, true));
    }

    private RegionTable() {
    }

    /** The region a block of this id makes on that server, or null. */
    public static @Nullable Type lookup(Server server, String blockId) {
        return (server == Server.PRIME ? PRIME : LITE).get(blockId);
    }

    /** Every region block of a server and what it makes (for the guide). */
    public static Map<String, Type> table(Server server) {
        return java.util.Collections.unmodifiableMap(server == Server.PRIME ? PRIME : LITE);
    }

    /** Whether the block is a region block on either server (worth looking for a hologram above). */
    public static boolean anyServer(String blockId) {
        return LITE.containsKey(blockId) || PRIME.containsKey(blockId);
    }

    /**
     * Block bounds {minX, minY, minZ, maxX, maxY, maxZ} of the area around a region block at (x, y, z), max
     * exclusive. A column (Lite) has {@link Integer#MIN_VALUE}/{@link Integer#MAX_VALUE} as its Y bounds.
     */
    public static int[] bounds(Type type, int x, int y, int z) {
        int half = (type.size() - 1) / 2;
        int minY = type.cube() ? y - half : Integer.MIN_VALUE;
        int maxY = type.cube() ? y + half + 1 : Integer.MAX_VALUE;
        return new int[]{x - half, minY, z - half, x + half + 1, maxY, z + half + 1};
    }

    /**
     * Signed distance from a point to the area's walls: positive inside (how far to the nearest wall), negative
     * outside (how far to the area). A column ignores Y.
     */
    public static double edgeDistance(int[] b, double px, double py, double pz) {
        boolean column = b[1] == Integer.MIN_VALUE;
        double dx = axis(px, b[0], b[3]);
        double dz = axis(pz, b[2], b[5]);
        double dy = column ? Double.NEGATIVE_INFINITY : axis(py, b[1], b[4]);
        double outside = 0;
        int out = 0;
        for (double d : new double[]{dx, dy, dz}) {
            if (d > 0) {
                outside += d * d;
                out++;
            }
        }
        if (out > 0) {
            return -Math.sqrt(outside);
        }
        double inner = Math.min(-dx, -dz);
        return column ? inner : Math.min(inner, -dy);
    }

    /** Distance outside [min, max] along one axis (positive outside, negative inside: minus the gap to the nearer end). */
    private static double axis(double p, int min, int max) {
        if (p < min) {
            return min - p;
        }
        if (p > max) {
            return p - max;
        }
        return -Math.min(p - min, max - p);
    }

    /** "21×21" for a column, "21×21×21" for a cube. */
    public static String sizeText(Type type) {
        String side = Integer.toString(type.size());
        return type.cube() ? side + "×" + side + "×" + side : side + "×" + side;
    }
}
