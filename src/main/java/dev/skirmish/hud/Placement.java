package dev.skirmish.hud;

import com.google.gson.JsonObject;

/**
 * Where a HUD element sits: the point at fraction {@code (ax, ay)} of the screen, minus fraction {@code (px, py)}
 * of the element's size, plus an offset in design px. Anchoring to the nearest edge keeps elements in place when
 * the window or GUI scale changes.
 */
public record Placement(float ax, float ay, float px, float py, float dx, float dy) {

    public float x(float screenWidth, float width) {
        return screenWidth * ax - width * px + dx;
    }

    public float y(float screenHeight, float height) {
        return screenHeight * ay - height * py + dy;
    }

    /** Placement for an element dropped at {@code (x, y)}: anchored to the screen third its center falls into. */
    public static Placement at(float x, float y, float w, float h, float screenWidth, float screenHeight) {
        float fx = third((x + w / 2f) / screenWidth);
        float fy = third((y + h / 2f) / screenHeight);
        return new Placement(fx, fy, fx, fy, x - (screenWidth * fx - w * fx), y - (screenHeight * fy - h * fy));
    }

    private static float third(float f) {
        return f < 1f / 3f ? 0f : f > 2f / 3f ? 1f : 0.5f;
    }

    public JsonObject toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("ax", ax);
        o.addProperty("ay", ay);
        o.addProperty("px", px);
        o.addProperty("py", py);
        o.addProperty("dx", dx);
        o.addProperty("dy", dy);
        return o;
    }

    public static Placement fromJson(JsonObject o) {
        return new Placement(o.get("ax").getAsFloat(), o.get("ay").getAsFloat(), o.get("px").getAsFloat(),
                o.get("py").getAsFloat(), o.get("dx").getAsFloat(), o.get("dy").getAsFloat());
    }
}
