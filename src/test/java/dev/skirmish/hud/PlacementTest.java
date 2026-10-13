package dev.skirmish.hud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlacementTest {
    private static final float EPS = 1e-4f;

    @Test
    void scaleSurvivesJsonRoundTrip() {
        Placement p = new Placement(1f, 0.5f, 1f, 0.5f, -18f, 12f, 1.35f);
        Placement back = Placement.fromJson(JsonParser.parseString(p.toJson().toString()).getAsJsonObject());
        assertEquals(p, back);
        assertEquals(1.35f, back.scale(), EPS);
    }

    @Test
    void unscaledPlacementWritesNoScaleField() {
        JsonObject json = new Placement(0f, 0f, 0f, 0f, 18f, 150f).toJson();
        assertFalse(json.has("scale"));
        assertTrue(new Placement(0f, 0f, 0f, 0f, 18f, 150f, 0.8f).toJson().has("scale"));
    }

    @Test
    void oldFilesWithoutScaleReadAsOne() {
        JsonObject old = JsonParser.parseString("{\"ax\":0.5,\"ay\":0.0,\"px\":0.5,\"py\":0.0,\"dx\":0.0,\"dy\":18.0}").getAsJsonObject();
        Placement p = Placement.fromJson(old);
        assertEquals(1f, p.scale());
        assertEquals(new Placement(0.5f, 0f, 0.5f, 0f, 0f, 18f), p);
    }

    @Test
    void badScaleValuesAreClampedOrIgnored() {
        assertEquals(Placement.MAX_SCALE, new Placement(0, 0, 0, 0, 0, 0, 5f).scale());
        assertEquals(Placement.MIN_SCALE, new Placement(0, 0, 0, 0, 0, 0, 0.1f).scale());
        assertEquals(1f, new Placement(0, 0, 0, 0, 0, 0, Float.NaN).scale());
        assertEquals(1f, new Placement(0, 0, 0, 0, 0, 0, Float.POSITIVE_INFINITY).scale());
        JsonObject edited = JsonParser.parseString("{\"ax\":0,\"ay\":0,\"px\":0,\"py\":0,\"dx\":0,\"dy\":0,\"scale\":9}").getAsJsonObject();
        assertEquals(Placement.MAX_SCALE, Placement.fromJson(edited).scale());
        JsonObject text = JsonParser.parseString("{\"ax\":0,\"ay\":0,\"px\":0,\"py\":0,\"dx\":0,\"dy\":0,\"scale\":\"big\"}").getAsJsonObject();
        assertEquals(1f, Placement.fromJson(text).scale());
    }

    @Test
    void droppedScaledElementKeepsItsTopLeft() {
        float sw = 1280, sh = 720, w = 200 * 1.5f, h = 120 * 1.5f;
        for (float[] at : new float[][]{{10, 10}, {600, 300}, {1200 - w, 700 - h}}) {
            Placement p = Placement.at(at[0], at[1], w, h, sw, sh, 1.5f);
            assertEquals(1.5f, p.scale(), EPS);
            assertEquals(at[0], p.x(sw, w), EPS);
            assertEquals(at[1], p.y(sh, h), EPS);
        }
    }

    @Test
    void withScaleKeepsTheAnchor() {
        Placement p = new Placement(1f, 1f, 1f, 1f, -20f, -30f, 1.4f).withScale(1f);
        assertEquals(new Placement(1f, 1f, 1f, 1f, -20f, -30f), p);
    }

    @Test
    void cornerDragMapsToScale() {
        assertEquals(1f, Placement.scaleForCorner(200, 100, 200, 100, 10f), EPS);
        assertEquals(1.2f, Placement.scaleForCorner(240, 120, 200, 100, 10f), EPS);
        // Moving along one axis only still resizes (projection on the diagonal).
        float wider = Placement.scaleForCorner(260, 100, 200, 100, 10f);
        assertTrue(wider > 1f && wider < 1.3f, String.valueOf(wider));
        assertEquals(Placement.MAX_SCALE, Placement.scaleForCorner(2000, 1000, 200, 100, 10f), EPS);
        assertEquals(Placement.MIN_SCALE, Placement.scaleForCorner(-50, -50, 200, 100, 10f), EPS);
        // Room left on screen caps the scale, but never below the minimum.
        assertEquals(1.1f, Placement.scaleForCorner(400, 200, 200, 100, 1.1f), EPS);
        assertEquals(Placement.MIN_SCALE, Placement.scaleForCorner(400, 200, 200, 100, 0.2f), EPS);
        assertEquals(1f, Placement.scaleForCorner(10, 10, 0, 0, 10f), EPS);
    }

    @Test
    void detailModes() {
        assertFalse(DetailMode.COMPACT.expanded(true));
        assertFalse(DetailMode.HOLD.expanded(false));
        assertTrue(DetailMode.HOLD.expanded(true));
        assertTrue(DetailMode.FULL.expanded(false));
    }
}
