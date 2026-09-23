package dev.skirmish.module.killcam;

import com.mojang.authlib.GameProfile;
import dev.skirmish.combat.EquipmentSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

/**
 * Client-only stand-in for a recorded player. It lives in the ClientLevel under its own random UUID and a negative
 * entity id (server ids are positive), never ticks and is driven every frame by {@link ReplaySession}.
 * The skin is resolved through the original player's PlayerInfo because the fake's own UUID has none.
 */
final class ReplayPlayer extends RemotePlayer {
    final int track;
    final UUID originalUuid;
    private final PlayerSkin fallbackSkin;
    private final @Nullable Object[] shownEquipment = new Object[ReplayBuffer.EQUIPMENT_SLOTS];
    boolean present;
    String skinSource = "default";

    ReplayPlayer(ClientLevel level, int entityId, int track, TrackMeta meta) {
        super(level, new GameProfile(UUID.randomUUID(), meta.name));
        setId(entityId);
        this.track = track;
        this.originalUuid = meta.uuid;
        this.fallbackSkin = meta.skin != null ? meta.skin : DefaultPlayerSkin.get(meta.uuid);
        setSilent(true);
        setMainArm(meta.mainArm);
        getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, meta.modelParts);
        PlayerInfo info = originalInfo();
        skinSource = info != null ? "PlayerInfo of " + meta.uuid : meta.skin != null ? "recorded skin" : "default skin";
    }

    static byte sharedFlags(Entity entity) {
        return entity.getEntityData().get(DATA_SHARED_FLAGS_ID);
    }

    static byte livingFlags(LivingEntity entity) {
        return entity.getEntityData().get(DATA_LIVING_ENTITY_FLAGS);
    }

    static byte modelParts(Player player) {
        return player.getEntityData().get(DATA_PLAYER_MODE_CUSTOMISATION);
    }

    private @Nullable PlayerInfo originalInfo() {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? null : connection.getPlayerInfo(originalUuid);
    }

    @Override
    public PlayerSkin getSkin() {
        PlayerInfo info = originalInfo();
        return info != null ? info.getSkin() : fallbackSkin;
    }

    @Override
    public @Nullable GameType gameMode() {
        return GameType.SURVIVAL;
    }

    @Override
    public void tick() {
        avatarState().tick(position(), Vec3.ZERO);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * Puts the entity into the recorded state. Old and new values are set equal so the renderer's own
     * partial-tick interpolation is a no-op; the walk animation is offset by {@code partialTick} for the same reason.
     */
    void apply(TrackSample s, float partialTick) {
        setPos(s.x, s.y, s.z);
        xo = xOld = s.x;
        yo = yOld = s.y;
        zo = zOld = s.z;
        setYRot(s.headYaw);
        yRotO = getYRot();
        setXRot(s.pitch);
        xRotO = getXRot();
        yHeadRot = yHeadRotO = s.headYaw;
        yBodyRot = yBodyRotO = s.bodyYaw;
        getEntityData().set(DATA_SHARED_FLAGS_ID, s.sharedFlags);
        setPose(Pose.BY_ID.apply(s.pose));
        setOnGround((s.anim & TrackSample.ANIM_ON_GROUND) != 0);

        // position(f) = (position - speed * (1 - f)) * scale; only public methods, see WalkAnimationState.
        float target = s.walkPos + s.walkSpeed * (1.0F - partialTick);
        walkAnimation.stop();
        walkAnimation.update(target - s.walkSpeed, 1.0F, 1.0F);
        walkAnimation.setSpeed(s.walkSpeed);
        walkAnimation.update(s.walkSpeed, 0.0F, 1.0F);

        attackAnim = oAttackAnim = s.attackAnim;
        swinging = s.swinging();
        swingingArm = s.swingOffHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        hurtDuration = 10;
        hurtTime = s.hurtTime;
        deathTime = s.deathTime;

        getEntityData().set(DATA_LIVING_ENTITY_FLAGS, s.livingFlags);
        if (isUsingItem() && !useItem.isEmpty()) {
            useItemRemaining = Math.max(0, useItem.getUseDuration(this) - s.useTicks);
        }
    }

    /** Shows the recorded stack of one slot (index into {@link EquipmentSnapshot#SLOTS}); copies only on change. */
    boolean applyEquipment(int slot, @Nullable Object stack) {
        if (shownEquipment[slot] == stack) {
            return false;
        }
        shownEquipment[slot] = stack;
        setItemSlot(EquipmentSnapshot.SLOTS.get(slot), stack instanceof ItemStack item ? item.copy() : ItemStack.EMPTY);
        return true;
    }
}
