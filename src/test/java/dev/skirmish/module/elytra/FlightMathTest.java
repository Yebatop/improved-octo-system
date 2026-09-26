package dev.skirmish.module.elytra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlightMathTest {
    @Test
    void glideAndEta() {
        assertEquals(431, FlightMath.glideSeconds(432));
        assertEquals(0, FlightMath.glideSeconds(1));
        assertEquals(40, FlightMath.etaSeconds(1200, 30));
        assertEquals(-1, FlightMath.etaSeconds(1200, 0.5));
        assertEquals(20.0, FlightMath.smooth(-1, 20, 0.3), 1e-9);
        assertEquals(23.0, FlightMath.smooth(20, 30, 0.3), 1e-9);
    }

    @Test
    void distanceText() {
        assertEquals("850 м", FlightMath.distanceText(849.6, ',', "м", "км"));
        assertEquals("1,2 км", FlightMath.distanceText(1234, ',', "м", "км"));
    }
}
