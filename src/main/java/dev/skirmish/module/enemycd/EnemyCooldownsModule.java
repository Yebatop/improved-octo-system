package dev.skirmish.module.enemycd;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import dev.skirmish.SkirmishClient;
import dev.skirmish.module.Category;
import dev.skirmish.module.Module;
import dev.skirmish.module.nametag.NametagHpModule;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.ui.Theme;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * «Enemy Cooldowns»: when you see another player throw a pearl, eat a golden apple or get their shield knocked out
 * by an axe, their cooldown for it counts down above their head (under the HP plate row, or on its own). The length
 * is what this server gives you for the same item (learned from your own cooldowns and remembered in
 * cooldowns.json); until you have used it once, the time since their use is shown instead. Only players in plain
 * sight get a row. Read-only; Feature Control id {@code enemy_cooldowns}.
 */
public final class EnemyCooldownsModule extends Module {
    public static final String ID = "enemy_cooldowns";
    static final String L = "layout.enemycd.";
    private static final String FILE = "cooldowns.json";
    private static volatile @Nullable EnemyCooldownsModule instance;

    final BoolSetting pearl = add(new BoolSetting("pearl", true));
    final BoolSetting gapple = add(new BoolSetting("gapple", true));
    final BoolSetting shield = add(new BoolSetting("shield", true));

    final CooldownBook book = new CooldownBook();
    private final Map<Integer, Long> eatingSince = new HashMap<>();
    private final Map<Integer, CooldownBook.Kind> eatingKind = new HashMap<>();
    /** Players with marks that are in plain sight this tick (entity ids). */
    private Set<Integer> visible = Set.of();

    public EnemyCooldownsModule() {
        super(ID, true);
    }

    public static @Nullable EnemyCooldownsModule instance() {
        return instance;
    }

    @Override
    public Category category() {
        return Category.COMBAT;
    }

    @Override
    public String featureId() {
        return ID;
    }

    @Override
    public void onInitialize() {
        instance = this;
        load();
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("skirmish", ID), new CooldownLabels(this));
        ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
            if (isEnabled() && pearl.get() && entity instanceof ThrownEnderpearl thrown && thrown.getOwner() instanceof Player owner
                    && owner != Minecraft.getInstance().player) {
                mark(owner, CooldownBook.Kind.PEARL);
            }
        });
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            book.clearMarks();
            eatingSince.clear();
            eatingKind.clear();
            visible = Set.of();
        });
    }

    @Override
    protected void onDisable() {
        book.clearMarks();
        visible = Set.of();
    }

    private void mark(Player player, CooldownBook.Kind kind) {
        long ms = book.durationMs(kind, Theme.get().integer(L + "default_" + kind.name().toLowerCase(java.util.Locale.ROOT)));
        book.mark(player.getUUID(), kind, Util.getMillis(), ms);
        log("%s used by %s (cooldown %s)", kind, player.getGameProfile().name(), ms < 0 ? "unknown yet" : ms / 1000.0 + "s");
    }

    /** The server set your own cooldown for {@code group} (from the packet handler). */
    public void onOwnCooldown(String group, int ticks) {
        if (book.learn(group, ticks)) {
            log("learned: %s lasts %d ticks here", group, ticks);
            save();
        }
    }

    /** A sound the server played at (x, y, z): a shield knocked out by an axe marks the player standing there. */
    public void onSound(Holder<SoundEvent> sound, double x, double y, double z) {
        if (!isEnabled() || !shield.get() || !sound.value().location().equals(SoundEvents.SHIELD_BREAK.value().location())) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Player best = null;
        double bestD = 2.25;
        for (AbstractClientPlayer p : mc.level.players()) {
            double d = p.distanceToSqr(x, y, z);
            if (p != mc.player && d < bestD) {
                best = p;
                bestD = d;
            }
        }
        if (best != null) {
            mark(best, CooldownBook.Kind.SHIELD);
        }
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        long now = Util.getMillis();
        if (gapple.get()) {
            for (AbstractClientPlayer p : mc.level.players()) {
                if (p == mc.player) {
                    continue;
                }
                CooldownBook.Kind kind = eatingKind(p);
                Long since = eatingSince.get(p.getId());
                if (kind != null && since == null) {
                    eatingSince.put(p.getId(), now);
                    eatingKind.put(p.getId(), kind);
                } else if (kind == null && since != null) {
                    eatingSince.remove(p.getId());
                    CooldownBook.Kind ate = eatingKind.remove(p.getId());
                    if (ate != null && now - since >= Theme.get().num(L + "eat_ms")) {
                        mark(p, ate);
                    }
                }
            }
        }
        Set<Integer> seen = new HashSet<>();
        if (book.hasMarks()) {
            Vec3 eye = mc.gameRenderer.getMainCamera().position();
            for (UUID id : book.players()) {
                Player p = mc.level.getPlayerByUUID(id);
                if (p != null && p != mc.player && !book.active(id, now, keepUnknownMs()).isEmpty()
                        && NametagHpModule.inPlainSight(mc, eye, p)) {
                    seen.add(p.getId());
                }
            }
        }
        visible = seen;
    }

    private static CooldownBook.@Nullable Kind eatingKind(Player p) {
        if (!p.isUsingItem()) {
            return null;
        }
        ItemStack use = p.getUseItem();
        return use.is(Items.ENCHANTED_GOLDEN_APPLE) ? CooldownBook.Kind.EGAPPLE
                : use.is(Items.GOLDEN_APPLE) ? CooldownBook.Kind.GAPPLE : null;
    }

    long keepUnknownMs() {
        return Math.round(Theme.get().num(L + "unknown_keep_ms"));
    }

    boolean visible(int entityId) {
        return visible.contains(entityId);
    }

    Set<Integer> visibleIds() {
        return visible;
    }

    /** Marks to draw for a player now (empty unless they are in plain sight). */
    public List<CooldownBook.Mark> marks(Player player) {
        if (!isEnabled() || !visible.contains(player.getId())) {
            return List.of();
        }
        return book.active(player.getUUID(), Util.getMillis(), keepUnknownMs());
    }

    /** Draws this player's row centred at {@code cx} with its bottom at {@code bottom} (used on top of HP plates). */
    public void drawRow(dev.skirmish.ui.Ui ui, Player player, float cx, float bottom) {
        List<CooldownBook.Mark> marks = marks(player);
        if (!marks.isEmpty()) {
            CooldownRow.draw(ui, marks, cx, bottom - ui.num(L + "plate_gap"), Util.getMillis());
        }
    }

    // ---- learned durations ----

    private Path file() {
        return SkirmishClient.configDir().resolve(FILE);
    }

    private void load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                JsonObject json = new Gson().fromJson(Files.readString(f, StandardCharsets.UTF_8), JsonObject.class);
                if (json != null) {
                    for (String key : json.keySet()) {
                        book.learn(key, json.get(key).getAsInt());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            error("cooldowns.json unreadable", e);
        }
    }

    private void save() {
        try {
            JsonObject json = new JsonObject();
            book.learned().forEach(json::addProperty);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), new Gson().toJson(json), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            error("cooldowns.json not saved", e);
        }
    }
}
