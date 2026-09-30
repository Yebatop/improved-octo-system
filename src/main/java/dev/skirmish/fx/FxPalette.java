package dev.skirmish.fx;

import dev.skirmish.ui.Theme;
import net.minecraft.util.RandomSource;

/** Colour sets for effects. Each gives a main colour and a lighter one; Rainbow walks the hue wheel. */
public enum FxPalette {
    ACCENT(0, 0),
    RAINBOW(0, 0),
    GOLD(0xFFFFB627, 0xFFFFF1B8),
    ICE(0xFF5AC8FA, 0xFFE3F8FF),
    BLOOD(0xFFD7263D, 0xFFFF6B6B),
    TOXIC(0xFF5BFF3C, 0xFFD4FFB0),
    SOUL(0xFF2FD8FF, 0xFFB5F4FF),
    FIRE(0xFFFF6A00, 0xFFFFD166),
    ROSE(0xFFFF5FA2, 0xFFFFD0E4),
    EMERALD(0xFF16D983, 0xFFB6FFD9),
    VOID(0xFF7B2CFF, 0xFFE0C8FF),
    WHITE(0xFFE8ECFF, 0xFFFFFFFF);

    private final int main;
    private final int light;

    FxPalette(int main, int light) {
        this.main = main;
        this.light = light;
    }

    /** The main colour (for Rainbow: the hue at {@code phase}). */
    public int main(float phase) {
        return switch (this) {
            case ACCENT -> Theme.get().color("accent") | 0xFF000000;
            case RAINBOW -> FxColor.hsv(phase, 0.75f, 1f);
            default -> main;
        };
    }

    public int light(float phase) {
        return switch (this) {
            case ACCENT -> FxColor.lighten(main(phase), 0.55f);
            case RAINBOW -> FxColor.hsv(phase, 0.35f, 1f);
            default -> light;
        };
    }

    /** A colour somewhere between the main and the light one (for Rainbow: anywhere on the wheel near {@code phase}). */
    public int pick(RandomSource random, float phase) {
        if (this == RAINBOW) {
            return FxColor.hsv(phase + random.nextFloat() * 0.25f, 0.7f, 1f);
        }
        return FxColor.lerp(main(phase), light(phase), random.nextFloat() * 0.6f);
    }
}
