package dev.skirmish.fx;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * The mod's own sounds ({@code assets/skirmish/sounds/fx}, made for it): hit, crit, kill and totem sounds. They are
 * played as UI sounds on this client only; the events are not registered anywhere, just looked up by id.
 */
public final class FxSounds {
    private FxSounds() {
    }

    public static SoundEvent of(String name) {
        return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("skirmish", "fx." + name));
    }

    /** Plays {@code event} to me at {@code pitch} and {@code volume} (0..1). */
    public static void play(SoundEvent event, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(event, pitch, Math.max(0f, Math.min(1f, volume))));
    }
}
