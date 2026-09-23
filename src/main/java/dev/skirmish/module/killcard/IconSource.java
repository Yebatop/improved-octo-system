package dev.skirmish.module.killcard;

import java.util.List;
import java.util.Locale;

/** Texture pixels of one item, copied from the sprite atlas on the client thread; composed by {@link IconComposer}. */
public sealed interface IconSource {
    /**
     * One texture layer.
     *
     * @param argb   {@code width * height} pixels, row-major, ARGB; never mutated after construction
     * @param tint   ARGB multiplier, -1 (white) for untinted layers
     * @param name   texture id, for the debug log
     */
    record Layer(String name, int width, int height, int[] argb, int tint) {
        public Layer {
            if (width <= 0 || height <= 0 || argb.length < width * height) {
                throw new IllegalArgumentException("Bad layer " + name + " " + width + "x" + height + " (" + argb.length + " px)");
            }
        }

        @Override
        public String toString() {
            return name + " " + width + "x" + height + (tint == -1 ? "" : " tint=" + Integer.toHexString(tint).toUpperCase(Locale.ROOT));
        }
    }

    /** Flat item (item/generated and friends): layers drawn on top of each other. */
    record Flat(List<Layer> layers) implements IconSource {
        public Flat {
            layers = List.copyOf(layers);
            if (layers.isEmpty()) {
                throw new IllegalArgumentException("No layers");
            }
        }
    }

    /** Block-like model: drawn as an isometric cube from three faces. */
    record Cube(Layer top, Layer left, Layer right) implements IconSource {
    }
}
