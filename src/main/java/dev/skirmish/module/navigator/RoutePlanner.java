package dev.skirmish.module.navigator;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Shortest way to a point across the Overworld and the Nether through the portals you have used. Nodes are the
 * start, the target and both ends of every known portal; walking inside one dimension costs the horizontal distance,
 * stepping through a portal a fixed cost. Plain Dijkstra over a few hundred nodes. Pure Java.
 */
final class RoutePlanner {
    static final String OVERWORLD = "minecraft:overworld";
    static final String NETHER = "minecraft:the_nether";

    record Point(String dim, double x, double z) {
    }

    /** A portal you went through: its Overworld and Nether ends. */
    record Link(double ox, double oz, double nx, double nz) {
    }

    /** One stretch to walk or fly in {@code dim}; {@code portal} when it ends in a portal to step through. */
    record Leg(String dim, double fx, double fz, double tx, double tz, boolean portal) {
        double length() {
            return Math.hypot(tx - fx, tz - fz);
        }
    }

    record Route(List<Leg> legs, double total) {
        boolean viaPortals() {
            return legs.stream().anyMatch(Leg::portal);
        }
    }

    private RoutePlanner() {
    }

    static @Nullable Route plan(Point start, Point target, List<Link> links, double portalCost) {
        int n = 2 + links.size() * 2;
        String[] dim = new String[n];
        double[] x = new double[n];
        double[] z = new double[n];
        dim[0] = start.dim();
        x[0] = start.x();
        z[0] = start.z();
        dim[1] = target.dim();
        x[1] = target.x();
        z[1] = target.z();
        for (int i = 0; i < links.size(); i++) {
            Link l = links.get(i);
            dim[2 + i * 2] = OVERWORLD;
            x[2 + i * 2] = l.ox();
            z[2 + i * 2] = l.oz();
            dim[3 + i * 2] = NETHER;
            x[3 + i * 2] = l.nx();
            z[3 + i * 2] = l.nz();
        }
        double[] dist = new double[n];
        int[] prev = new int[n];
        boolean[] done = new boolean[n];
        Arrays.fill(dist, Double.POSITIVE_INFINITY);
        Arrays.fill(prev, -1);
        dist[0] = 0;
        for (int iter = 0; iter < n; iter++) {
            int u = -1;
            for (int i = 0; i < n; i++) {
                if (!done[i] && (u < 0 || dist[i] < dist[u])) {
                    u = i;
                }
            }
            if (u < 0 || dist[u] == Double.POSITIVE_INFINITY) {
                break;
            }
            done[u] = true;
            if (u == 1) {
                break;
            }
            for (int v = 0; v < n; v++) {
                if (done[v]) {
                    continue;
                }
                double w = cost(u, v, dim, x, z, portalCost);
                if (dist[u] + w < dist[v]) {
                    dist[v] = dist[u] + w;
                    prev[v] = u;
                }
            }
        }
        if (dist[1] == Double.POSITIVE_INFINITY) {
            return null;
        }
        List<Integer> path = new ArrayList<>();
        for (int v = 1; v >= 0; v = prev[v]) {
            path.add(0, v);
        }
        List<Leg> legs = new ArrayList<>();
        for (int i = 0; i + 1 < path.size(); i++) {
            int a = path.get(i);
            int b = path.get(i + 1);
            if (isPortalPair(a, b)) {
                continue;
            }
            boolean portal = i + 2 < path.size() && isPortalPair(b, path.get(i + 2));
            legs.add(new Leg(dim[a], x[a], z[a], x[b], z[b], portal));
        }
        return new Route(legs, dist[1]);
    }

    private static boolean isPortalPair(int a, int b) {
        return a >= 2 && b >= 2 && (a - 2) / 2 == (b - 2) / 2 && a != b;
    }

    private static double cost(int a, int b, String[] dim, double[] x, double[] z, double portalCost) {
        if (isPortalPair(a, b)) {
            return portalCost;
        }
        if (!dim[a].equals(dim[b])) {
            return Double.POSITIVE_INFINITY;
        }
        return Math.hypot(x[b] - x[a], z[b] - z[a]);
    }

    /** Nether ↔ Overworld coordinate of the same place (×8 / ÷8), for hints when no portal is known. */
    static double convert(double coord, String fromDim, String toDim) {
        if (fromDim.equals(OVERWORLD) && toDim.equals(NETHER)) {
            return coord / 8.0;
        }
        if (fromDim.equals(NETHER) && toDim.equals(OVERWORLD)) {
            return coord * 8.0;
        }
        return coord;
    }
}
