package dev.skirmish.module.worldmap;

/**
 * Pure maths of the minimap: turning a world offset into map pixels (north up, or turned so you face up), back
 * again for sampling the tiles, the shape's soft edge, and pinning markers that lie outside to the rim.
 */
final class MinimapMath {
    private MinimapMath() {
    }

    /**
     * Map offset {x, y} from the centre of a world offset (dx east, dz south) at {@code scale} map px per block.
     * Turned: the direction you face (yaw 0 looks south) points up and your right points right.
     */
    static float[] toMap(double dx, double dz, float yawDegrees, boolean rotate, double scale) {
        if (!rotate) {
            return new float[]{(float) (dx * scale), (float) (dz * scale)};
        }
        double yaw = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        // Your right: yaw 0 faces +Z, and then the right hand points to −X.
        double rx = -fz;
        double rz = fx;
        double forward = dx * fx + dz * fz;
        double right = dx * rx + dz * rz;
        return new float[]{(float) (right * scale), (float) (-forward * scale)};
    }

    /** The world offset {dx, dz} under the map offset (x, y): the inverse of {@link #toMap}. */
    static double[] toWorld(double x, double y, float yawDegrees, boolean rotate, double scale) {
        if (!rotate) {
            return new double[]{x / scale, y / scale};
        }
        double yaw = Math.toRadians(yawDegrees);
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        double rx = -fz;
        double rz = fx;
        double right = x / scale;
        double forward = -y / scale;
        return new double[]{right * rx + forward * fx, right * rz + forward * fz};
    }

    /**
     * Coverage (0..1) of the pixel at offset (x, y) from the centre of a map {@code size} px across: a circle, or a
     * square with corners rounded by {@code radius}; soft over one pixel so the edge is smooth.
     */
    static float coverage(float x, float y, float size, boolean round, float radius) {
        float half = size / 2f;
        float d;
        if (round) {
            d = (float) Math.sqrt(x * x + y * y) - half;
        } else {
            float r = Math.min(radius, half);
            float qx = Math.abs(x) - (half - r);
            float qy = Math.abs(y) - (half - r);
            float ox = Math.max(qx, 0f);
            float oy = Math.max(qy, 0f);
            d = (float) Math.sqrt(ox * ox + oy * oy) + Math.min(Math.max(qx, qy), 0f) - r;
        }
        return Math.max(0f, Math.min(1f, 0.5f - d));
    }

    /** Whether the offset lies inside the shape, {@code inset} px in from its edge. */
    static boolean inside(float x, float y, float size, boolean round, float inset) {
        float half = size / 2f - inset;
        return round ? x * x + y * y <= half * half : Math.abs(x) <= half && Math.abs(y) <= half;
    }

    /** The offset moved onto the edge {@code inset} px in (along the line from the centre), for markers outside. */
    static float[] pin(float x, float y, float size, boolean round, float inset) {
        float half = size / 2f - inset;
        if (round) {
            float d = (float) Math.sqrt(x * x + y * y);
            return d <= half || d == 0f ? new float[]{x, y} : new float[]{x / d * half, y / d * half};
        }
        float m = Math.max(Math.abs(x), Math.abs(y));
        return m <= half || m == 0f ? new float[]{x, y} : new float[]{x / m * half, y / m * half};
    }
}
