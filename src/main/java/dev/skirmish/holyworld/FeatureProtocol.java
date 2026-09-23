package dev.skirmish.holyworld;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * HolyWorld LiteAPI envelope for {@code checkFeatures} (wiki.holyworld.me/lite/api/protocol and /feature). Pure Java
 * so it is unit tested.
 */
public final class FeatureProtocol {
    /** Stable mod id: the server keeps personal blocklists under it, so it must never change. */
    public static final String CLIENT_ID = "skirmish";

    /** A parsed server message. {@code blocklist} is null unless {@code ok}. */
    public record Response(@Nullable String id, boolean ok, @Nullable List<String> blocklist, @Nullable String error,
                           @Nullable String message, @Nullable String event) {
        public boolean isPush() {
            return event != null;
        }
    }

    private FeatureProtocol() {
    }

    public static String newRequestId() {
        return UUID.randomUUID().toString();
    }

    public static byte[] checkFeaturesRequest(String id, Collection<String> features) {
        JsonArray list = new JsonArray();
        features.forEach(list::add);
        JsonObject payload = new JsonObject();
        payload.addProperty("client", CLIENT_ID);
        payload.add("features", list);
        JsonObject envelope = new JsonObject();
        envelope.addProperty("id", id);
        envelope.addProperty("method", "checkFeatures");
        envelope.add("payload", payload);
        return envelope.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Parses a message body. The docs specify bare UTF-8 JSON; a VarInt length prefix (as written by
     * {@code PacketCodecs.STRING}) is tolerated too.
     */
    public static Response parse(byte[] body) {
        String text = decode(body);
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        if (root.has("event")) {
            return new Response(null, true, null, null, null, string(root, "event"));
        }
        String id = string(root, "id");
        JsonElement okElement = root.get("ok");
        boolean ok = okElement != null && okElement.isJsonPrimitive() && okElement.getAsBoolean();
        if (!ok) {
            return new Response(id, false, null, string(root, "error"), string(root, "message"), null);
        }
        List<String> blocked = new ArrayList<>();
        JsonElement payload = root.get("payload");
        if (payload != null && payload.isJsonObject()) {
            JsonElement list = payload.getAsJsonObject().get("blocklist");
            if (list != null && list.isJsonArray()) {
                for (JsonElement e : list.getAsJsonArray()) {
                    if (e.isJsonPrimitive()) {
                        blocked.add(e.getAsString());
                    }
                }
            }
        }
        return new Response(id, true, List.copyOf(blocked), null, null, null);
    }

    static String decode(byte[] body) {
        int start = 0;
        if (body.length > 0 && body[0] != '{') {
            // Skip a VarInt length prefix (1-5 bytes, high bit = continuation).
            while (start < body.length && start < 5 && (body[start] & 0x80) != 0) {
                start++;
            }
            start++;
        }
        return new String(body, Math.min(start, body.length), body.length - Math.min(start, body.length), StandardCharsets.UTF_8);
    }

    private static @Nullable String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }
}
