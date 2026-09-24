package dev.skirmish.module.anvilcalc.calc;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Everything the solver reads: the left-slot item, the books (their index is the book id used in the plan),
 * enchantment definitions for every id that occurs, and the limits.
 *
 * @param tooExpensiveAt a step costing this much or more is refused outside creative (vanilla: 40)
 * @param exactLimit     at most this many candidate books are searched exactly; more use the greedy heuristic
 * @param profile        server rules applied on top of vanilla ({@link RulesProfile#VANILLA} for none)
 * @param baseArmor      the left item is worn armour or elytra (some servers treat armour differently)
 * @param dubious        indices of books that follow «сомнительные» rules and are never merged by the plan
 */
public record AnvilInput(Piece base, List<Piece> books, Map<String, EnchantInfo> catalog, boolean creative,
                         int tooExpensiveAt, int exactLimit, RulesProfile profile, boolean baseArmor, Set<Integer> dubious) {
    public AnvilInput {
        books = List.copyOf(books);
        catalog = Map.copyOf(catalog);
        dubious = Set.copyOf(dubious);
    }

    /** Vanilla rules. */
    public AnvilInput(Piece base, List<Piece> books, Map<String, EnchantInfo> catalog, boolean creative,
                      int tooExpensiveAt, int exactLimit) {
        this(base, books, catalog, creative, tooExpensiveAt, exactLimit, RulesProfile.VANILLA, false, Set.of());
    }

    /** Largest allowed step cost + 1, or unlimited in creative. */
    public int stepLimit() {
        return creative ? Integer.MAX_VALUE : tooExpensiveAt;
    }
}
