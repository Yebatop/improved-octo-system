package dev.skirmish.module.navigator;

import org.junit.jupiter.api.Test;

import java.util.List;

import static dev.skirmish.module.navigator.RoutePlanner.NETHER;
import static dev.skirmish.module.navigator.RoutePlanner.OVERWORLD;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoutePlannerTest {
    private static final double PORTAL = 16;

    @Test
    void sameDimensionGoesStraight() {
        RoutePlanner.Route r = RoutePlanner.plan(new RoutePlanner.Point(OVERWORLD, 0, 0),
                new RoutePlanner.Point(OVERWORLD, 30, 40), List.of(), PORTAL);
        assertNotNull(r);
        assertEquals(1, r.legs().size());
        assertEquals(50, r.total(), 1e-9);
        assertFalse(r.viaPortals());
    }

    @Test
    void farTargetGoesThroughTheNether() {
        // Two portal pairs 8000 blocks apart in the Overworld, 1000 apart in the Nether.
        List<RoutePlanner.Link> links = List.of(
                new RoutePlanner.Link(0, 0, 0, 0),
                new RoutePlanner.Link(8000, 0, 1000, 0));
        RoutePlanner.Route r = RoutePlanner.plan(new RoutePlanner.Point(OVERWORLD, 10, 0),
                new RoutePlanner.Point(OVERWORLD, 8010, 0), links, PORTAL);
        assertNotNull(r);
        assertTrue(r.viaPortals());
        assertEquals(3, r.legs().size());
        assertEquals(OVERWORLD, r.legs().get(0).dim());
        assertTrue(r.legs().get(0).portal());
        assertEquals(NETHER, r.legs().get(1).dim());
        assertTrue(r.legs().get(1).portal());
        assertEquals(OVERWORLD, r.legs().get(2).dim());
        assertFalse(r.legs().get(2).portal());
        assertEquals(10 + PORTAL + 1000 + PORTAL + 10, r.total(), 1e-9);
    }

    @Test
    void nearTargetIgnoresPortals() {
        List<RoutePlanner.Link> links = List.of(
                new RoutePlanner.Link(0, 0, 0, 0),
                new RoutePlanner.Link(20, 0, 2.5, 0));
        // Through the Nether: 16 + 2.5 + 16 = 34.5, more than walking 20.
        RoutePlanner.Route r = RoutePlanner.plan(new RoutePlanner.Point(OVERWORLD, 0, 0),
                new RoutePlanner.Point(OVERWORLD, 20, 0), links, PORTAL);
        assertNotNull(r);
        assertFalse(r.viaPortals());
        assertEquals(20, r.total(), 1e-9);
    }

    @Test
    void otherDimensionNeedsAPortal() {
        assertNull(RoutePlanner.plan(new RoutePlanner.Point(OVERWORLD, 0, 0),
                new RoutePlanner.Point(NETHER, 5, 5), List.of(), PORTAL));
        RoutePlanner.Route r = RoutePlanner.plan(new RoutePlanner.Point(OVERWORLD, 0, 0),
                new RoutePlanner.Point(NETHER, 13, 4),
                List.of(new RoutePlanner.Link(3, 4, 10, 0)), PORTAL);
        assertNotNull(r);
        assertEquals(2, r.legs().size());
        assertEquals(5 + PORTAL + 5, r.total(), 1e-9);
    }

    @Test
    void convertsCoordinates() {
        assertEquals(125, RoutePlanner.convert(1000, OVERWORLD, NETHER), 1e-9);
        assertEquals(800, RoutePlanner.convert(100, NETHER, OVERWORLD), 1e-9);
        assertEquals(7, RoutePlanner.convert(7, OVERWORLD, OVERWORLD), 1e-9);
    }
}
