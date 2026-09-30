package dev.skirmish.module.coords;

import dev.skirmish.waypoint.Waypoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Which death waypoints to drop so only the newest N per server remain (pure, unit tested). */
public final class DeathWaypoints {
    public static final String SOURCE = "death";

    private DeathWaypoints() {
    }

    /** Ids of the oldest death waypoints of {@code server} beyond the newest {@code keep}; other waypoints are never touched. */
    public static List<String> excess(List<Waypoint> all, String server, int keep) {
        List<Waypoint> deaths = new ArrayList<>();
        for (Waypoint waypoint : all) {
            if (SOURCE.equals(waypoint.source()) && server.equals(waypoint.server())) {
                deaths.add(waypoint);
            }
        }
        deaths.sort(Comparator.comparingLong(Waypoint::createdAt).reversed());
        List<String> drop = new ArrayList<>();
        for (int i = Math.max(0, keep); i < deaths.size(); i++) {
            drop.add(deaths.get(i).id());
        }
        return drop;
    }
}
