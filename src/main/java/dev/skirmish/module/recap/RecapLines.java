package dev.skirmish.module.recap;

import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/**
 * Texts of the recap, shared by the window and the PNG card: eight stat tiles and the best-fight line. Pure Java
 * (translations come in as a function), covered by tests.
 */
public final class RecapLines {
    public static final String P = "skirmish.session_recap.";

    /** @param color theme colour token of the value */
    public record Tile(String value, String label, String color) {
    }

    private RecapLines() {
    }

    public static List<Tile> tiles(SessionRecap r, Function<String, String> tr, char decimal) {
        List<Tile> tiles = new ArrayList<>(8);
        tiles.add(new Tile(Integer.toString(r.kills()), tr.apply(P + "tile.kills"), "good"));
        tiles.add(new Tile(Integer.toString(r.deaths()), tr.apply(P + "tile.deaths"), "bad"));
        tiles.add(new Tile(RecapFormat.kd(r.kd(), decimal), tr.apply(P + "tile.kd"), "text"));
        tiles.add(new Tile(RecapFormat.playtime(r.playtimeMs()), tr.apply(P + "tile.playtime"), "text"));
        tiles.add(new Tile(r.wins() + " / " + r.fights(), tr.apply(P + "tile.fights"), "accent"));
        tiles.add(new Tile(r.totemsPopped() + " : " + r.myTotems(), tr.apply(P + "tile.totems"), "warn"));
        tiles.add(r.coins() == null
                ? new Tile("—", tr.apply(P + "tile.coins_unknown"), "text_3")
                : new Tile(RecapFormat.signed(r.coins()), tr.apply(P + "tile.coins"), r.coins() < 0 ? "bad" : "good"));
        tiles.add(r.levels() == null
                ? new Tile("—", tr.apply(P + "tile.levels"), "text_3")
                : new Tile(RecapFormat.signed(r.levels()), tr.apply(P + "tile.levels"), "text"));
        return tiles;
    }

    /** {@code 18,5 урона · 7 ударов · 2 тотема · победа · 0:42}; damage is left out when the server hid health. */
    public static String bestDetails(RecapFight f, Function<String, String> tr, char decimal) {
        List<String> parts = new ArrayList<>(5);
        if (f.damageKnown()) {
            parts.add(format(tr.apply(P + "best.damage"), RecapFormat.hp(f.damage(), decimal)));
        }
        parts.add(format(tr.apply(P + "best.hits." + RecapFormat.plural(f.hitsDealt())), f.hitsDealt()));
        if (f.opponentTotems() > 0) {
            parts.add(format(tr.apply(P + "best.totems." + RecapFormat.plural(f.opponentTotems())), f.opponentTotems()));
        }
        parts.add(tr.apply(P + "best.result." + f.result().name().toLowerCase(Locale.ROOT)));
        parts.add(RecapFormat.playtime(f.durationMs()));
        return String.join(" · ", parts);
    }

    /** The best fight's title ({@code против Nick}), or the empty text. */
    public static String bestTitle(@Nullable RecapFight f, Function<String, String> tr) {
        return f == null ? tr.apply(P + "best.none") : format(tr.apply(P + "best.vs"), f.opponent());
    }

    private static String format(String pattern, Object arg) {
        try {
            return String.format(Locale.ROOT, pattern, arg);
        } catch (java.util.IllegalFormatException e) {
            return pattern;
        }
    }
}
