package dev.skirmish.waypoint;

import java.util.Locale;

/**
 * A saved point. Bound to a server key (see {@code ServerContext.serverKey()}) and a dimension id.
 *
 * @param source who created it: {@code manual}, {@code command}, or e.g. {@code clanshare:Nick}
 * @param color  RGB (no alpha)
 */
public record Waypoint(String id, String name, double x, double y, double z, String dimension, String server,
                       long createdAt, int color, String source) {

    public Waypoint withName(String newName) {
        return new Waypoint(id, newName, x, y, z, dimension, server, createdAt, color, source);
    }

    public double distanceSq(double px, double py, double pz) {
        double dx = x - px;
        double dy = y - py;
        double dz = z - pz;
        return dx * dx + dy * dy + dz * dz;
    }

    public String coordsText() {
        return String.format(Locale.ROOT, "%d %d %d", (long) Math.floor(x), (long) Math.floor(y), (long) Math.floor(z));
    }
}
