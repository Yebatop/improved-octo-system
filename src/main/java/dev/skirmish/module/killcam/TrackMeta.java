package dev.skirmish.module.killcam;

import com.google.common.collect.ImmutableMultimap;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.authlib.properties.PropertyMap;
import dev.skirmish.module.killcam.library.ReplayRecording;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

/** Per-track data that does not change per tick; stored as the track's meta in {@link ReplayBuffer}. */
final class TrackMeta {
    static final String TEXTURES = "textures";

    final UUID uuid;
    final String name;
    final boolean self;
    @Nullable PlayerSkin skin;
    /** Saved replays: skin loaded from the recorded {@code textures} property (resolves once downloaded). */
    @Nullable Supplier<PlayerSkin> skinLookup;
    byte modelParts;
    HumanoidArm mainArm = HumanoidArm.RIGHT;

    TrackMeta(UUID uuid, String name, boolean self) {
        this.uuid = uuid;
        this.name = name;
        this.self = self;
    }

    /** Meta of a track of a saved replay; the skin comes from the saved profile property when there is one. */
    static TrackMeta saved(ReplayRecording.Track track) {
        TrackMeta meta = new TrackMeta(track.uuid, track.name, track.self);
        meta.modelParts = track.modelParts;
        meta.mainArm = track.leftHanded ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
        if (!track.skinValue.isEmpty()) {
            try {
                Property property = track.skinSignature.isEmpty() ? new Property(TEXTURES, track.skinValue)
                        : new Property(TEXTURES, track.skinValue, track.skinSignature);
                GameProfile profile = new GameProfile(track.uuid, track.name, new PropertyMap(ImmutableMultimap.of(TEXTURES, property)));
                meta.skinLookup = Minecraft.getInstance().getSkinManager().createLookup(profile, false);
            } catch (RuntimeException ignored) {
                meta.skinLookup = null;
            }
        }
        return meta;
    }
}
