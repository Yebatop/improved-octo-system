package dev.skirmish.holyworld;

import dev.skirmish.debug.DebugLog;
import dev.skirmish.setting.FeatureGate;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * HolyWorld Feature Control ({@code liteapi:feature-control}): on joining a HolyWorld server the mod sends
 * {@code checkFeatures} with every feature id it has, and hides whatever comes back in {@code blocklist}
 * (FeatureGate). This is the one plugin message the mod sends, only to HolyWorld, at most once per 10 s. Safe mode
 * adds a local list of features that HolyWorld's rules make risky (free camera, info through walls).
 */
public final class FeatureControl {
    private static final String TAG = "holyworld";
    /** Server limit: 1 request per 10 s per player. */
    private static final long MIN_INTERVAL_MS = 10_000;
    private static final long TIMEOUT_MS = 5_000;
    private static final int JOIN_DELAY_TICKS = 20;
    /** Features HolyWorld's rule 2.4 makes risky; hidden there while safe mode is on. */
    public static final Set<String> SAFE_MODE_FEATURES = Set.of("freecam", "through_walls");

    private static Set<String> serverBlocked = Set.of();
    private static @Nullable String pendingId;
    private static long sentAt = -1;
    private static long lastSent = -MIN_INTERVAL_MS;
    private static int joinCountdown = -1;
    private static boolean retryAfterLimit;
    private static boolean wasHolyWorld;
    private static BooleanSupplier safeMode = () -> true;

    private FeatureControl() {
    }

    public static void install(BooleanSupplier safeModeSetting) {
        safeMode = safeModeSetting;
        PayloadTypeRegistry.playC2S().register(FeatureControlPayload.TYPE, FeatureControlPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(FeatureControlPayload.TYPE, FeatureControlPayload.CODEC);
        ClientPlayNetworking.registerGlobalReceiver(FeatureControlPayload.TYPE, (payload, context) -> onMessage(payload.body()));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> joinCountdown = JOIN_DELAY_TICKS));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            serverBlocked = Set.of();
            pendingId = null;
            joinCountdown = -1;
            apply();
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        boolean holy = HolyWorld.isConnected();
        if (holy != wasHolyWorld) {
            wasHolyWorld = holy;
            apply();
        }
        if (joinCountdown > 0 && --joinCountdown == 0) {
            request("join");
        }
        long now = Util.getMillis();
        if (pendingId != null && now - sentAt > TIMEOUT_MS) {
            DebugLog.log(TAG, "checkFeatures: no answer in " + TIMEOUT_MS + " ms (server without LiteAPI?)");
            pendingId = null;
        }
        if (retryAfterLimit && now - lastSent >= MIN_INTERVAL_MS) {
            retryAfterLimit = false;
            request("retry after RATE_LIMITED");
        }
    }

    /** Asks the server which features are blocked. Only on HolyWorld and never more than once per 10 s. */
    public static void request(String reason) {
        Minecraft mc = Minecraft.getInstance();
        if (!HolyWorld.isConnected() || mc.getConnection() == null) {
            return;
        }
        long now = Util.getMillis();
        if (now - lastSent < MIN_INTERVAL_MS) {
            retryAfterLimit = true;
            return;
        }
        List<String> features = FeatureGate.declared();
        String id = FeatureProtocol.newRequestId();
        try {
            ClientPlayNetworking.send(new FeatureControlPayload(FeatureProtocol.checkFeaturesRequest(id, features)));
            pendingId = id;
            sentAt = now;
            lastSent = now;
            DebugLog.log(TAG, "checkFeatures sent (" + reason + "): " + features.size() + " features " + features);
        } catch (RuntimeException e) {
            DebugLog.error(TAG, "checkFeatures could not be sent", e);
        }
    }

    private static void onMessage(byte[] body) {
        FeatureProtocol.Response response;
        try {
            response = FeatureProtocol.parse(body);
        } catch (RuntimeException e) {
            DebugLog.error(TAG, "unreadable feature-control message (" + body.length + " bytes)", e);
            return;
        }
        if (response.isPush()) {
            DebugLog.log(TAG, "feature-control push event '" + response.event() + "' (ignored)");
            return;
        }
        if (pendingId != null && response.id() != null && !pendingId.equals(response.id())) {
            DebugLog.log(TAG, "feature-control answer for an old request " + response.id() + " (ignored)");
            return;
        }
        pendingId = null;
        if (!response.ok()) {
            DebugLog.log(TAG, "checkFeatures failed: " + response.error() + (response.message() == null ? "" : " (" + response.message() + ")"));
            if ("RATE_LIMITED".equals(response.error())) {
                retryAfterLimit = true;
            }
            return;
        }
        setServerBlocklist(response.blocklist() == null ? Set.of() : new HashSet<>(response.blocklist()));
    }

    /** Applies a server blocklist (also used by {@code /skirmish features simulate} for testing). */
    public static void setServerBlocklist(Set<String> blocked) {
        serverBlocked = Set.copyOf(blocked);
        DebugLog.log(TAG, "server blocklist: " + (blocked.isEmpty() ? "empty" : blocked));
        apply();
    }

    public static Set<String> serverBlocklist() {
        return serverBlocked;
    }

    /** Recomputes FeatureGate: server blocklist plus safe-mode features while on HolyWorld. */
    public static void apply() {
        Set<String> all = new HashSet<>(serverBlocked);
        if (HolyWorld.isConnected() && safeMode.getAsBoolean()) {
            all.addAll(SAFE_MODE_FEATURES);
        }
        FeatureGate.setBlocked(all);
    }
}
