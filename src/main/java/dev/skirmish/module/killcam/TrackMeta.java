package dev.skirmish.module.killcam;

import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerSkin;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/** Per-track data that does not change per tick; stored as the track's meta in {@link ReplayBuffer}. */
final class TrackMeta {
    final UUID uuid;
    final String name;
    final boolean self;
    @Nullable PlayerSkin skin;
    byte modelParts;
    HumanoidArm mainArm = HumanoidArm.RIGHT;

    TrackMeta(UUID uuid, String name, boolean self) {
        this.uuid = uuid;
        this.name = name;
        this.self = self;
    }
}
