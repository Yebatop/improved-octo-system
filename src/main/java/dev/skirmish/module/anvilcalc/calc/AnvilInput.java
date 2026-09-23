package dev.skirmish.module.anvilcalc.calc;

import java.util.List;
import java.util.Map;

/**
 * Everything the solver reads: the left-slot item, the books (their index is the book id used in the plan),
 * enchantment definitions for every id that occurs, and the limits.
 *
 * @param tooExpensiveAt a step costing this much or more is refused outside creative (vanilla: 40)
 * @param exactLimit     at most this many candidate books are searched exactly; more use the greedy heuristic
 */
public record AnvilInput(Piece base, List<Piece> books, Map<String, EnchantInfo> catalog, boolean creative,
                         int tooExpensiveAt, int exactLimit) {
    public AnvilInput {
        books = List.copyOf(books);
        catalog = Map.copyOf(catalog);
    }

    /** Largest allowed step cost + 1, or unlimited in creative. */
    public int stepLimit() {
        return creative ? Integer.MAX_VALUE : tooExpensiveAt;
    }
}
