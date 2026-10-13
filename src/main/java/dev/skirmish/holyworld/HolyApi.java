package dev.skirmish.holyworld;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.skirmish.debug.DebugLog;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BooleanSupplier;

/**
 * Read-only client for HolyWorld's public REST API ({@code https://api.holyworld.me}, OpenAPI at /openapi/public):
 * servers, live Lite events, votings, coin trades, Prime event timetable. GET only, no auth, no cookies.
 *
 * <p>Use {@link #poll}: it returns the last good response at once (never blocks the render thread) and refreshes
 * in the background when the cached copy is older than the given TTL. Requests are made only while connected to
 * HolyWorld and while the "HolyWorld data" setting is on; failures back off (1, 2, 5, 10 min). The API sends no
 * caching headers, so the TTLs here are the only thing keeping traffic low: keep them at 30 s or more.
 */
public final class HolyApi {
    public static final String BASE = "https://api.holyworld.me";
    private static final String TAG = "holyworld";
    private static final long[] BACKOFF_MS = {60_000, 120_000, 300_000, 600_000};

    private record Entry(@Nullable JsonElement value, long fetchedAt, long failedAt, int failures, boolean inFlight) {
    }

    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static volatile BooleanSupplier allowed = () -> true;
    private static @Nullable HttpClient client;

    private HolyApi() {
    }

    /** {@code setting}: the user's "HolyWorld data" switch. */
    public static void install(BooleanSupplier setting) {
        allowed = setting;
    }

    /**
     * Last good JSON for {@code path} (e.g. {@code /v1/events}), or null if none yet. Starts a background refresh
     * when the copy is older than {@code ttl}, unless one is running or the path is backing off after errors.
     */
    public static @Nullable JsonElement poll(String path, Duration ttl) {
        Entry entry = CACHE.get(path);
        long now = System.currentTimeMillis();
        boolean stale = entry == null || entry.value() == null || now - entry.fetchedAt() > ttl.toMillis();
        boolean backingOff = entry != null && entry.failures() > 0
                && now - entry.failedAt() < BACKOFF_MS[Math.min(entry.failures(), BACKOFF_MS.length) - 1];
        if (stale && !backingOff && (entry == null || !entry.inFlight()) && canRequest()) {
            fetch(path, entry);
        }
        return entry == null ? null : entry.value();
    }

    /** Milliseconds since {@code path} was last fetched successfully, or -1. */
    public static long age(String path) {
        Entry entry = CACHE.get(path);
        return entry == null || entry.value() == null ? -1 : System.currentTimeMillis() - entry.fetchedAt();
    }

    public static boolean canRequest() {
        return allowed.getAsBoolean() && HolyWorld.isConnected();
    }

    private static synchronized HttpClient http() {
        if (client == null) {
            client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return client;
    }

    private static String userAgent() {
        String version = FabricLoader.getInstance().getModContainer("skirmish")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("dev");
        return "Skirmish/" + version + " (Fabric client mod; https://github.com/Yebatop/improved-octo-system)";
    }

    private static void fetch(String path, @Nullable Entry previous) {
        CACHE.put(path, new Entry(previous == null ? null : previous.value(), previous == null ? 0 : previous.fetchedAt(),
                previous == null ? 0 : previous.failedAt(), previous == null ? 0 : previous.failures(), true));
        HttpRequest request = HttpRequest.newBuilder(URI.create(BASE + path))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", userAgent())
                .header("Accept", "application/json")
                .GET()
                .build();
        http().sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            Entry before = CACHE.get(path);
            long now = System.currentTimeMillis();
            String problem = null;
            JsonElement json = null;
            if (error != null) {
                problem = error.getClass().getSimpleName() + ": " + error.getMessage();
            } else if (response.statusCode() != 200) {
                problem = "HTTP " + response.statusCode();
            } else {
                try {
                    json = JsonParser.parseString(response.body());
                } catch (RuntimeException e) {
                    problem = "not JSON (anti-bot page?)";
                }
            }
            if (problem == null) {
                CACHE.put(path, new Entry(json, now, 0, 0, false));
            } else {
                int failures = (before == null ? 0 : before.failures()) + 1;
                CACHE.put(path, new Entry(before == null ? null : before.value(), before == null ? 0 : before.fetchedAt(), now, failures, false));
                DebugLog.log(TAG, "GET " + path + " failed (" + problem + "), retry in "
                        + BACKOFF_MS[Math.min(failures, BACKOFF_MS.length) - 1] / 1000 + " s");
            }
        });
    }
}
