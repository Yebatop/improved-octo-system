package dev.skirmish.fx;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FxColorTest {
    @Test
    void lerpBlendsEveryChannel() {
        assertEquals(0xFF808080, FxColor.lerp(0xFF000000, 0xFFFFFFFF, 0.5f), 0x010101);
        assertEquals(0x00FF0000, FxColor.lerp(0xFFFF0000, 0x00FF0000, 1f));
        assertEquals(0xFFFF0000, FxColor.lerp(0xFFFF0000, 0x00FF0000, -1f));
    }

    @Test
    void hsvWalksTheWheel() {
        assertEquals(0xFFFF0000, FxColor.hsv(0f, 1f, 1f));
        assertEquals(0xFF00FF00, FxColor.hsv(1f / 3f, 1f, 1f));
        assertEquals(0xFF0000FF, FxColor.hsv(2f / 3f, 1f, 1f));
        assertEquals(0xFFFF0000, FxColor.hsv(1f, 1f, 1f));
    }

    @Test
    void alphaScalesOnlyAlpha() {
        assertEquals(0x80123456, FxColor.alpha(0xFF123456, 0.5f), 0x01000000);
        assertEquals(0x00123456, FxColor.alpha(0xFF123456, -2f));
    }
}
