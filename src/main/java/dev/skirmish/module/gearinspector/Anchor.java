package dev.skirmish.module.gearinspector;

/**
 * Screen corner/edge the panel is attached to. Offsets are measured from the anchored edge towards the screen
 * center (for centered axes they shift right/down), and the result is clamped to the screen.
 */
public enum Anchor {
    TOP_LEFT(0, 0),
    TOP_CENTER(1, 0),
    TOP_RIGHT(2, 0),
    CENTER_LEFT(0, 1),
    CENTER(1, 1),
    CENTER_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2),
    BOTTOM_CENTER(1, 2),
    BOTTOM_RIGHT(2, 2);

    public record Placement(int x, int y) {
    }

    private final int horizontal;
    private final int vertical;

    Anchor(int horizontal, int vertical) {
        this.horizontal = horizontal;
        this.vertical = vertical;
    }

    public Placement place(int screenWidth, int screenHeight, int width, int height, int offsetX, int offsetY) {
        int x = clamp(axis(horizontal, screenWidth, width, offsetX), screenWidth - width);
        int y = clamp(axis(vertical, screenHeight, height, offsetY), screenHeight - height);
        return new Placement(x, y);
    }

    private static int axis(int mode, int screen, int size, int offset) {
        return switch (mode) {
            case 0 -> offset;
            case 1 -> (screen - size) / 2 + offset;
            default -> screen - size - offset;
        };
    }

    private static int clamp(int value, int max) {
        return Math.max(0, Math.min(Math.max(0, max), value));
    }
}
