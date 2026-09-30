package dev.skirmish.module.analytics.dossier;

import dev.skirmish.SkirmishClient;
import dev.skirmish.combat.CombatListener;
import dev.skirmish.combat.CombatTracker;
import dev.skirmish.combat.Combatant;
import dev.skirmish.combat.EquipmentSnapshot;
import dev.skirmish.combat.Fight;
import dev.skirmish.combat.FightEndReason;
import dev.skirmish.combat.OwnDeath;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.hud.Hud;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.analytics.AnalyticsHub;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * «Досье»: a persistent record per player (config/skirmish/dossier.json, by UUID, at most {@value #MAX_PLAYERS}
 * players, the ones seen longest ago dropped first): fights, my kills and deaths against them, damage both ways, the
 * gear they wore last time and when we last fought. A small HUD block under the target card shows «Вы против Nick:
 * 3–1 · последний бой 2 ч назад» for the player I look at (only with a record) or fight. Players invisible to me are
 * never shown or recorded from sight (HolyWorld bans invisibility indicators).
 */
public final class DossierModule extends Module {
    public static final String ID = "dossier";
    public static final int MAX_PLAYERS = 2_000;
    private static final double LOOK_RANGE = 32;
    private static final long LINGER_MS = 1_500;
    private static final long SAVE_INTERVAL_MS = 30_000;
    private static @Nullable DossierModule instance;

    final BoolSetting hud = add(new BoolSetting("hud", false));
    final BoolSetting onLook = add(new BoolSetting("on_look", true));
    final BoolSetting showGear = add(new BoolSetting("gear", true));

    private DossierStore store = new DossierStore(MAX_PLAYERS);
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Skirmish dossier.json");
        t.setDaemon(true);
        return t;
    });
    private boolean dirty;
    private long lastSave;
    private @Nullable UUID shown;
    private String shownName = "";
    private boolean shownInFight;
    private long shownUntil;

    public DossierModule() {
        super(ID, true);
        instance = this;
        onLook.under(hud).visibleWhen(hud::get);
        showGear.under(hud).visibleWhen(hud::get);
    }

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    @Override
    public String featureId() {
        return ID;
    }

    private static Path file() {
        return SkirmishClient.configDir().resolve("dossier.json");
    }

    @Override
    public void onInitialize() {
        AnalyticsHub.get().require(this::isEnabled);
        load();
        Hud.get().register(new DossierHud(this));
        CombatTracker.get().addListener(new Listener());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(this::saveIfDirty));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> saveNow());
    }

    private void load() {
        Path file = file();
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            store = DossierStore.fromJson(Files.readString(file, StandardCharsets.UTF_8), MAX_PLAYERS);
            log("dossier.json loaded: %d players", store.size());
        } catch (IOException | RuntimeException e) {
            DebugLog.error(ID, "could not read " + file, e);
        }
    }

    private void saveIfDirty() {
        if (!dirty) {
            return;
        }
        dirty = false;
        lastSave = System.currentTimeMillis();
        String json = store.toJson();
        try {
            writer.execute(() -> write(json));
        } catch (RejectedExecutionException e) {
            write(json);
        }
    }

    private void saveNow() {
        if (dirty) {
            dirty = false;
            write(store.toJson());
        }
    }

    private static void write(String json) {
        Path file = file();
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            DebugLog.error(ID, "could not write " + file, e);
        }
    }

    /** «Счёт против Nick: 3–1 · 7 боёв» for the review screen, or null without a record (or with the module off). */
    public static @Nullable String summary(UUID uuid) {
        DossierModule m = instance;
        if (m == null || !m.isEnabled()) {
            return null;
        }
        DossierRecord r = m.store.get(uuid);
        if (r == null) {
            return null;
        }
        return Ui.tr("skirmish.analytics.dossier.summary", r.name(), r.kills() + "–" + r.deaths(),
                r.fights() + " " + Ui.plural("skirmish.analytics.dossier.fights", r.fights()));
    }

    @Nullable DossierRecord record(UUID uuid) {
        return store.get(uuid);
    }

    // ---- who the HUD block is about ----

    @Nullable UUID shown() {
        return shown != null && System.currentTimeMillis() < shownUntil ? shown : null;
    }

    String shownName() {
        return shownName;
    }

    boolean shownInFight() {
        return shownInFight;
    }

    @Override
    public void tick() {
        AnalyticsHub.get().tick();
        long now = System.currentTimeMillis();
        if (dirty && now - lastSave > SAVE_INTERVAL_MS) {
            saveIfDirty();
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !hud.get()) {
            shown = null;
            return;
        }
        Player looked = onLook.get() ? lookedAt(mc) : null;
        if (looked != null && store.get(looked.getUUID()) != null) {
            show(looked.getUUID(), looked.getGameProfile().name(), false, now);
            return;
        }
        Fight latest = null;
        for (Fight f : CombatTracker.get().activeFights()) {
            if (f.opponent().player() && (latest == null || f.lastActivityMs() > latest.lastActivityMs())) {
                latest = f;
            }
        }
        if (latest != null) {
            Entity entity = AnalyticsHub.find(latest.opponent().uuid(), latest.opponent().entityId());
            if (entity != null && !AnalyticsHub.hidden(entity)) {
                show(latest.opponent().uuid(), latest.opponent().name(), true, now);
                return;
            }
        }
        // Keep the last one for the linger time, but never once that player turned invisible.
        if (shown != null) {
            Entity entity = AnalyticsHub.find(shown, -1);
            if (entity != null && AnalyticsHub.hidden(entity)) {
                shown = null;
            }
        }
    }

    private void show(UUID uuid, String name, boolean inFight, long now) {
        if (!uuid.equals(shown)) {
            log("dossier shown for %s (%s)", name, inFight ? "fight" : "look");
        }
        shown = uuid;
        shownName = name;
        shownInFight = inFight;
        shownUntil = now + LINGER_MS;
    }

    /** Player under the crosshair within {@link #LOOK_RANGE}, not through blocks, never an invisible one. */
    private static @Nullable Player lookedAt(Minecraft mc) {
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.level == null || mc.player == null) {
            return null;
        }
        Vec3 eye = camera.getEyePosition(1.0F);
        Vec3 end = eye.add(camera.getViewVector(1.0F).scale(LOOK_RANGE));
        Player best = null;
        double bestDistance = Double.MAX_VALUE;
        for (AbstractClientPlayer candidate : mc.level.players()) {
            if (candidate == mc.player || candidate == camera || !candidate.isAlive() || candidate.isSpectator()
                    || AnalyticsHub.hidden(candidate)) {
                continue;
            }
            AABB box = candidate.getBoundingBox().inflate(candidate.getPickRadius());
            Optional<Vec3> hit = box.clip(eye, end);
            if (box.contains(eye)) {
                return candidate;
            }
            if (hit.isPresent() && eye.distanceTo(hit.get()) < bestDistance) {
                best = candidate;
                bestDistance = eye.distanceTo(hit.get());
            }
        }
        if (best == null) {
            return null;
        }
        HitResult block = camera.pick(bestDistance, 1.0F, false);
        if (block.getType() != HitResult.Type.MISS && block.getLocation().distanceTo(eye) < bestDistance - 1e-3) {
            return null;
        }
        return best;
    }

    static List<String> gearIds(@Nullable EquipmentSnapshot snapshot) {
        List<String> ids = new ArrayList<>(6);
        for (EquipmentSlot slot : EquipmentSnapshot.SLOTS) {
            ItemStack stack = snapshot == null ? ItemStack.EMPTY : snapshot.get(slot);
            ids.add(stack.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }
        return ids;
    }

    private final class Listener implements CombatListener {
        @Override
        public void onFightEnd(Fight fight) {
            if (!isEnabled() || !fight.opponent().player() || !AnalyticsHub.get().seenVisible(fight)) {
                return;
            }
            FightEndReason reason = fight.endReason();
            DossierRecord r = store.recordFight(fight.opponent().uuid(), new DossierRecord.FightResult(fight.opponent().name(),
                    reason == FightEndReason.KILL, fight.isDamageKnown() ? fight.damageDealt() : 0, fight.damageTaken(),
                    reason == null ? "" : reason.name(), gearIds(CombatTracker.get().equipment(fight)), fight.endMs()));
            dirty = true;
            log("fight with %s recorded: %d fights, %d–%d", r.name(), r.fights(), r.kills(), r.deaths());
        }

        @Override
        public void onOwnDeath(OwnDeath death) {
            Combatant killer = death.killer();
            if (!isEnabled() || killer == null || !killer.player() || killer.self() || !AnalyticsHub.get().mayShow(killer, death.timeMs())) {
                return;
            }
            DossierRecord r = store.recordDeath(killer.uuid(), killer.name(), death.timeMs());
            dirty = true;
            log("death by %s recorded: %d–%d", r.name(), r.kills(), r.deaths());
        }
    }
}
