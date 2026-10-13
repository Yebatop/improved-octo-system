package dev.skirmish.hud;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Where a HUD element sits: the point at fraction {@code (ax, ay)} of the screen, minus fraction {@code (px, py)}
 * of the element's size, plus an offset in design px. Anchoring to the nearest edge keeps elements in place when
 * the window or GUI scale changes. {@code scale} enlarges or shrinks the element around its top-left corner; the
 * size used by {@link #x}/{@link #y} is the scaled one.
 */
public record Placement(float ax, float ay, float px, float py, float dx, float dy, float scale) {
    /** Smallest and largest element scale the HUD editor allows (and what a hand-edited hud.json is clamped to). */
    public static final float MIN_SCALE = 0.6f;
    public static final float MAX_SCALE = 1.8f;

    public Placement {
        scale = clampScale(scale);
    }

    /** Unscaled placement (scale 1). */
    public Placement(float ax, float ay, float px, float py, float dx, float dy) {
        this(ax, ay, px, py, dx, dy, 1f);
    }

    public float x(float screenWidth, float width) {
        return screenWidth * ax - width * px + dx;
    }

    public float y(float screenHeight, float height) {
        return screenHeight * ay - height * py + dy;
    }

    public Placement withScale(float newScale) {
        return new Placement(ax, ay, px, py, dx, dy, newScale);
    }

    /** {@code value} within {@link #MIN_SCALE}..{@link #MAX_SCALE}; 1 for NaN/infinite values. */
    public static float clampScale(float value) {
        if (!Float.isFinite(value)) {
            return 1f;
        }
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, value));
    }

    /**
     * Scale for a corner handle dragged to {@code (cornerX, cornerY)} relative to the element's top-left: the
     * pointer projected onto the diagonal of the unscaled {@code baseW × baseH} box, so moving along either axis
     * resizes smoothly. Clamped to {@link #MIN_SCALE}..{@code maxScale} (itself at most {@link #MAX_SCALE}).
     */
    public static float scaleForCorner(float cornerX, float cornerY, float baseW, float baseH, float maxScale) {
        float diag = baseW * baseW + baseH * baseH;
        if (!(diag > 0f)) {
            return 1f;
        }
        float s = (cornerX * baseW + cornerY * baseH) / diag;
        return Math.max(MIN_SCALE, Math.min(Math.max(MIN_SCALE, Math.min(MAX_SCALE, maxScale)), clampScale(s)));
    }

    /** Placement for an element dropped at {@code (x, y)}: anchored to the screen third its center falls into. */
    public static Placement at(float x, float y, float w, float h, float screenWidth, float screenHeight) {
        return at(x, y, w, h, screenWidth, screenHeight, 1f);
    }

    /** As {@link #at(float, float, float, float, float, float)}; {@code w}/{@code h} are the scaled size. */
    public static Placement at(float x, float y, float w, float h, float screenWidth, float screenHeight, float scale) {
        float fx = third((x + w / 2f) / screenWidth);
        float fy = third((y + h / 2f) / screenHeight);
        return new Placement(fx, fy, fx, fy, x - (screenWidth * fx - w * fx), y - (screenHeight * fy - h * fy), scale);
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
        if (scale != 1f) {
            o.addProperty("scale", scale);
        }
        return o;
    }

    /** Reads {@link #toJson()}; files written before element scaling have no {@code scale} (= 1). */
    public static Placement fromJson(JsonObject o) {
        JsonElement s = o.get("scale");
        float scale = s != null && s.isJsonPrimitive() && s.getAsJsonPrimitive().isNumber() ? s.getAsFloat() : 1f;
        return new Placement(o.get("ax").getAsFloat(), o.get("ay").getAsFloat(), o.get("px").getAsFloat(),
                o.get("py").getAsFloat(), o.get("dx").getAsFloat(), o.get("dy").getAsFloat(), scale);
    }
}
