package dev.skirmish.module.market.parse;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Local price history of config/skirmish/prices.json: per item key, recent per-unit prices with timestamps. Capped
 * per item and in the number of items (least recently updated items go first). Pure Java (unit tested); the module
 * owns file IO. Not thread safe: use from the client thread, hand {@link #toJson} to a writer thread.
 * <pre>{"version":1,"items":{"minecraft:diamond":[{"t":1790000000000,"p":1250.0,"s":"ah"}, ...]}}</pre>
 */
public final class PriceHistory {
    public static final int VERSION = 1;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** One observation: time (epoch ms), price per item, source ("ah" seen on a page, "buy" from a purchase line). */
    public record Sample(long time, double unitPrice, String source) {
    }

    private final int maxPerItem;
    private final int maxItems;
    /** Access order = eviction order. */
    private final LinkedHashMap<String, List<Sample>> items = new LinkedHashMap<>(64, 0.75f, true);
    /** Lots already recorded (item, seller, price, count) with the time: one page is re-read many times. */
    private final Map<String, Long> seenLots = new HashMap<>();

    public PriceHistory(int maxPerItem, int maxItems) {
        this.maxPerItem = Math.max(1, maxPerItem);
        this.maxItems = Math.max(1, maxItems);
    }

    public int size() {
        return items.size();
    }

    public List<Sample> samples(String itemKey) {
        List<Sample> list = items.get(itemKey);
        return list == null ? List.of() : Collections.unmodifiableList(list);
    }

    /**
     * Records a lot seen on an auction page unless the same lot was recorded within {@code dedupMs}.
     * @return whether a sample was added
     */
    public boolean recordLot(String itemKey, String seller, long price, int count, long now, long dedupMs) {
        String lot = itemKey + '\u0000' + seller + '\u0000' + price + '\u0000' + count;
        Long last = seenLots.get(lot);
        if (last != null && now - last < dedupMs) {
            return false;
        }
        seenLots.put(lot, now);
        if (seenLots.size() > maxItems * 8) {
            seenLots.values().removeIf(t -> now - t >= dedupMs);
        }
        add(itemKey, new Sample(now, price / (double) Math.max(1, count), "ah"));
        return true;
    }

    public void add(String itemKey, Sample sample) {
        if (itemKey == null || itemKey.isBlank() || !(sample.unitPrice() > 0) || Double.isInfinite(sample.unitPrice())) {
            return;
        }
        List<Sample> list = items.computeIfAbsent(itemKey, k -> new ArrayList<>());
        list.add(sample);
        list.sort((a, b) -> Long.compare(a.time(), b.time()));
        while (list.size() > maxPerItem) {
            list.removeFirst();
        }
        while (items.size() > maxItems) {
            Iterator<String> eldest = items.keySet().iterator();
            eldest.next();
            eldest.remove();
        }
    }

    /**
     * Median per-unit price of samples newer than {@code maxAgeMs} (all when {@code maxAgeMs <= 0}); empty with
     * fewer than {@code minSamples}.
     */
    public OptionalDouble median(String itemKey, long now, long maxAgeMs, int minSamples) {
        List<Sample> list = items.get(itemKey);
        if (list == null) {
            return OptionalDouble.empty();
        }
        List<Double> prices = new ArrayList<>();
        for (Sample s : list) {
            if (maxAgeMs <= 0 || now - s.time() <= maxAgeMs) {
                prices.add(s.unitPrice());
            }
        }
        if (prices.size() < Math.max(1, minSamples)) {
            return OptionalDouble.empty();
        }
        Collections.sort(prices);
        int n = prices.size();
        return OptionalDouble.of(n % 2 == 1 ? prices.get(n / 2) : (prices.get(n / 2 - 1) + prices.get(n / 2)) / 2.0);
    }

    /** How a price compares with the usual one. */
    public enum Verdict {
        CHEAP, NORMAL, DEAR
    }

    /** {@code CHEAP} at or below {@code median × (1 − tolerance)}, {@code DEAR} at or above {@code median × (1 + tolerance)}. */
    public static Verdict verdict(double unitPrice, double median, double tolerance) {
        if (median <= 0) {
            return Verdict.NORMAL;
        }
        if (unitPrice <= median * (1 - tolerance)) {
            return Verdict.CHEAP;
        }
        if (unitPrice >= median * (1 + tolerance)) {
            return Verdict.DEAR;
        }
        return Verdict.NORMAL;
    }

    public String toJson() {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonObject out = new JsonObject();
        // Oldest first, so eviction order survives a reload.
        for (Map.Entry<String, List<Sample>> e : new ArrayList<>(items.entrySet())) {
            JsonArray array = new JsonArray();
            for (Sample s : e.getValue()) {
                JsonObject o = new JsonObject();
                o.addProperty("t", s.time());
                o.addProperty("p", s.unitPrice());
                o.addProperty("s", s.source());
                array.add(o);
            }
            out.add(e.getKey(), array);
        }
        root.add("items", out);
        return GSON.toJson(root);
    }

    /** Replaces the contents with {@code json}; malformed entries are skipped. */
    public void loadJson(String json) {
        items.clear();
        seenLots.clear();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject in = root.getAsJsonObject("items");
        if (in == null) {
            return;
        }
        for (Map.Entry<String, JsonElement> e : in.entrySet()) {
            if (!e.getValue().isJsonArray()) {
                continue;
            }
            for (JsonElement el : e.getValue().getAsJsonArray()) {
                try {
                    JsonObject o = el.getAsJsonObject();
                    add(e.getKey(), new Sample(o.get("t").getAsLong(), o.get("p").getAsDouble(),
                            o.has("s") ? o.get("s").getAsString() : "ah"));
                } catch (RuntimeException ignored) {
                    // Skip the malformed sample, keep the rest.
                }
            }
        }
    }
}
