package dev.skirmish.module.hwitems;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.module.gearinspector.holy.Talisman;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * HolyWorld item knowledge from the wiki (wiki.holyworld.me, "Динамит", "Кастомные предметы", "Сферы и Талисманы",
 * "Рюкзак", "Яйца призыва", "Опыт", "Зачарования", FAQ), loaded from {@value #RESOURCE}. Each entry names the items it
 * applies to (as {@link HwItemKey} keys), the translation keys of its tooltip lines, an optional slot badge and, for
 * the unique spheres, their fixed effects. The texts live in the lang files. Pure Java, covered by tests.
 * <p>
 * Lookup: an exact key match first; otherwise the entry whose name occurs in the item name as whole words, the
 * longest such name winning ({@code Надёжный стиллер} beats {@code стиллер}). Entries marked {@code exact} (single
 * common words such as {@code Охотник}) only match the whole name.
 */
public final class HwItemTable {
    public static final String RESOURCE = "/assets/skirmish_hw_tooltips/hw_items.json";
    public static final String FACT_PREFIX = "skirmish.hw_items.fact.";
    public static final String BADGE_PREFIX = "skirmish.hw_items.badge.";
    /** Entries applied by code rather than by name: any armour piece, and top donor armour. */
    public static final String ARMOR = "@armor";
    public static final String ARMOR_TOP = "@armor_top";

    /**
     * @param id    entry id (lang key part)
     * @param group what kind of item it is ({@code tnt}, {@code sphere}, …); badges are enabled per group
     * @param names item-name keys that select this entry
     * @param exact only a whole-name match counts
     * @param lines full translation keys of the tooltip lines, in order
     * @param badge whether the entry has a slot badge ({@link #badgeKey()})
     * @param stats fixed effects (unique spheres), empty otherwise
     */
    public record Entry(String id, String group, List<String> names, boolean exact, List<String> lines, boolean badge,
                        Map<Talisman.StatType, Integer> stats) {
        public Entry {
            names = List.copyOf(names);
            lines = List.copyOf(lines);
            stats = stats.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(stats));
        }

        public String badgeKey() {
            return BADGE_PREFIX + id;
        }
    }

    private record Name(String key, Entry entry) {
    }

    private final Map<String, Entry> byId;
    private final Map<String, Entry> byName;
    /** Every name, most words first, then longest. */
    private final List<Name> phrases;

    private HwItemTable(List<Entry> entries) {
        Map<String, Entry> ids = new LinkedHashMap<>();
        Map<String, Entry> names = new HashMap<>();
        List<Name> list = new ArrayList<>();
        for (Entry e : entries) {
            if (ids.put(e.id(), e) != null) {
                throw new IllegalArgumentException("Duplicate hw_items entry " + e.id());
            }
            for (String name : e.names()) {
                if (names.put(name, e) != null) {
                    throw new IllegalArgumentException("Duplicate hw_items name \"" + name + "\" (" + e.id() + ")");
                }
                if (!e.exact()) {
                    list.add(new Name(name, e));
                }
            }
        }
        list.sort(Comparator.comparingInt((Name n) -> n.key().split(" ").length).reversed()
                .thenComparing(Comparator.comparingInt((Name n) -> n.key().length()).reversed()));
        this.byId = Collections.unmodifiableMap(ids);
        this.byName = names;
        this.phrases = List.copyOf(list);
    }

    private static volatile @Nullable HwItemTable instance;

    /** The table from the mod's resources (loaded once). */
    public static HwItemTable get() {
        HwItemTable table = instance;
        if (table == null) {
            synchronized (HwItemTable.class) {
                if (instance == null) {
                    instance = load();
                }
                table = instance;
            }
        }
        return table;
    }

    static HwItemTable load() {
        try (InputStream in = HwItemTable.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException(RESOURCE + " is missing");
            }
            return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Parses the table JSON; names are normalised with {@link HwItemKey#of}. */
    public static HwItemTable parse(Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        List<Entry> entries = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray("entries")) {
            JsonObject o = element.getAsJsonObject();
            String id = o.get("id").getAsString();
            List<String> names = new ArrayList<>();
            if (o.has("names")) {
                for (JsonElement n : o.getAsJsonArray("names")) {
                    String key = HwItemKey.of(n.getAsString());
                    if (!key.isEmpty() && !names.contains(key)) {
                        names.add(key);
                    }
                }
            }
            List<String> lines = new ArrayList<>();
            if (o.has("lines")) {
                for (JsonElement l : o.getAsJsonArray("lines")) {
                    lines.add(lineKey(id, l.getAsString()));
                }
            }
            Map<Talisman.StatType, Integer> stats = new EnumMap<>(Talisman.StatType.class);
            if (o.has("stats")) {
                for (Map.Entry<String, JsonElement> s : o.getAsJsonObject("stats").entrySet()) {
                    stats.put(Talisman.StatType.valueOf(s.getKey().toUpperCase(Locale.ROOT)), s.getValue().getAsInt());
                }
            }
            entries.add(new Entry(id, o.has("group") ? o.get("group").getAsString() : "misc", names,
                    o.has("exact") && o.get("exact").getAsBoolean(), lines,
                    o.has("badge") && o.get("badge").getAsBoolean(), stats));
        }
        return new HwItemTable(entries);
    }

    /** {@code radius} of entry {@code tnt_a} → {@code …fact.tnt_a.radius}; {@code tnt.privates} (with a dot) is shared. */
    static String lineKey(String entryId, String line) {
        return FACT_PREFIX + (line.contains(".") ? line : entryId + "." + line);
    }

    public List<Entry> entries() {
        return List.copyOf(byId.values());
    }

    public @Nullable Entry byId(String id) {
        return byId.get(id);
    }

    /** The entry for an item's display name (raw, with codes and decorations), or null. */
    public @Nullable Entry find(String rawName) {
        return findKey(HwItemKey.of(rawName));
    }

    /** As {@link #find} for an already normalised key. */
    public @Nullable Entry findKey(String key) {
        if (key.isEmpty()) {
            return null;
        }
        Entry exact = byName.get(key);
        if (exact != null) {
            return exact;
        }
        for (Name name : phrases) {
            if (HwItemKey.containsPhrase(key, name.key())) {
                return name.entry();
            }
        }
        return null;
    }
}
