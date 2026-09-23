package dev.skirmish.module.gearinspector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnchorTest {
    @Test
    void cornersMeasureOffsetsFromTheirEdges() {
        assertEquals(new Anchor.Placement(4, 6), Anchor.TOP_LEFT.place(400, 300, 100, 50, 4, 6));
        assertEquals(new Anchor.Placement(296, 6), Anchor.TOP_RIGHT.place(400, 300, 100, 50, 4, 6));
        assertEquals(new Anchor.Placement(4, 244), Anchor.BOTTOM_LEFT.place(400, 300, 100, 50, 4, 6));
        assertEquals(new Anchor.Placement(296, 244), Anchor.BOTTOM_RIGHT.place(400, 300, 100, 50, 4, 6));
    }

    @Test
    void centeredAxesShiftRightAndDown() {
        assertEquals(new Anchor.Placement(160, 125), Anchor.CENTER.place(400, 300, 100, 50, 10, 0));
        assertEquals(new Anchor.Placement(150, 135), Anchor.TOP_CENTER.place(400, 300, 100, 50, 0, 135));
        assertEquals(new Anchor.Placement(290, 115), Anchor.CENTER_RIGHT.place(400, 300, 100, 50, 10, -10));
    }

    @Test
    void panelStaysOnScreen() {
        assertEquals(new Anchor.Placement(0, 0), Anchor.TOP_LEFT.place(400, 300, 100, 50, -50, -50));
        assertEquals(new Anchor.Placement(300, 250), Anchor.TOP_LEFT.place(400, 300, 100, 50, 1000, 1000));
        assertEquals(new Anchor.Placement(0, 0), Anchor.BOTTOM_RIGHT.place(80, 40, 100, 50, 0, 0));
    }
}
