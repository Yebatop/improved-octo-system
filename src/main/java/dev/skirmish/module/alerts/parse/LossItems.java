package dev.skirmish.module.alerts.parse;

import dev.skirmish.module.market.parse.HwText;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Items HolyWorld drops when you log out. Pure Java (unit tested); the module feeds it the inventory.
 * <ul>
 *     <li>Режим шалкера (Prime, Alpha; wiki): two or more shulker boxes in the inventory → all of them drop.</li>
 *     <li>Lite (wiki «Рюкзак»): backpacks are shulker boxes named «Рюкзак …» (I–IV, Infinity); two or more
 *     backpacks turn on shulker mode, and then shulker boxes and backpacks drop.</li>
 *     <li>Элементы (Prime): even one Element turns on element mode; they drop on logout. The item's base type is not
 *     published, so it is recognized by name ({@code Элемент}/{@code Элементы}, not «Элементная бочка»,
 *     «Элементный сундук» or «Крупица»).</li>
 * </ul>
 */
public final class LossItems {
    /** What one inventory stack is. */
    public enum Kind {
        NONE, SHULKER, BACKPACK, ELEMENT
    }

    /** Item counts: shulker boxes, backpacks and Elements (stack sizes summed). */
    public record Tally(int shulkers, int backpacks, int elements) {
        public static final Tally EMPTY = new Tally(0, 0, 0);

        /** Shulker mode is on: two or more boxes (backpacks are boxes too). */
        public boolean shulkerMode() {
            return shulkers + backpacks >= 2;
        }

        /** Something drops on logout. */
        public boolean atRisk() {
            return shulkerMode() || elements > 0;
        }

        public int containers() {
            return shulkers + backpacks;
        }
    }

    private static final Pattern BACKPACK = Pattern.compile("рюкзак|backpack");
    /** «Элемент», «Элементы», «x16 Элемент»; not «Элементная бочка»/«Элементный сундук». */
    private static final Pattern ELEMENT = Pattern.compile("(^|[^\\p{L}])элемент(ы|а|ов)?([^\\p{L}]|$)|(^|[^\\p{L}])element(s)?([^\\p{L}]|$)");
    private static final Pattern NOT_ELEMENT = Pattern.compile("бочк|сундук|крупиц|barrel|chest|shard");

    private LossItems() {
    }

    /**
     * @param shulkerBox the item is in the {@code #minecraft:shulker_boxes} tag
     * @param name       displayed name (custom or default)
     * @param lore       lore lines (only used for backpacks, whose level may be in the lore)
     */
    public static Kind classify(boolean shulkerBox, String name, List<String> lore) {
        String key = HwText.normalize(name);
        if (shulkerBox) {
            if (BACKPACK.matcher(key).find()) {
                return Kind.BACKPACK;
            }
            for (String line : lore) {
                String l = HwText.normalize(line);
                if (l.startsWith("рюкзак") || l.contains("это рюкзак")) {
                    return Kind.BACKPACK;
                }
            }
            return Kind.SHULKER;
        }
        if (ELEMENT.matcher(key).find() && !NOT_ELEMENT.matcher(key).find()) {
            return Kind.ELEMENT;
        }
        return Kind.NONE;
    }

    /** Adds one stack to a running tally. */
    public static Tally add(Tally tally, Kind kind, int count) {
        return switch (kind) {
            case SHULKER -> new Tally(tally.shulkers() + Math.max(1, count), tally.backpacks(), tally.elements());
            case BACKPACK -> new Tally(tally.shulkers(), tally.backpacks() + Math.max(1, count), tally.elements());
            case ELEMENT -> new Tally(tally.shulkers(), tally.backpacks(), tally.elements() + Math.max(1, count));
            case NONE -> tally;
        };
    }
}
