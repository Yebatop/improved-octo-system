package dev.skirmish.module.anvilcalc;

import dev.skirmish.module.anvilcalc.calc.AnvilInput;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Operand;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Reason;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Status;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Step;
import dev.skirmish.module.anvilcalc.calc.AnvilRules;
import dev.skirmish.module.anvilcalc.calc.AnvilSolver;
import dev.skirmish.module.anvilcalc.calc.EnchantInfo;
import dev.skirmish.module.anvilcalc.calc.Piece;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static dev.skirmish.module.anvilcalc.Vanilla.ench;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnvilSolverTest {
    private static final Map<String, EnchantInfo> SWORD = Vanilla.catalog(Vanilla.SWORD);
    private static final AnvilRules SURVIVAL = new AnvilRules(SWORD, false);

    private static AnvilPlan solve(Piece base, List<Piece> books, boolean creative, int exactLimit) {
        return AnvilSolver.solve(new AnvilInput(base, books, SWORD, creative, 40, exactLimit));
    }

    private static AnvilPlan solve(Piece base, List<Piece> books) {
        return solve(base, books, false, 10);
    }

    private static List<Piece> referenceBooks() {
        return List.of(
                Piece.book(0, ench("sharpness", 5)),
                Piece.book(0, ench("looting", 3)),
                Piece.book(0, ench("mending", 1)),
                Piece.book(0, ench("unbreaking", 3)),
                Piece.book(0, ench("sweeping_edge", 3)));
    }

    /** Replays the plan with the rules and checks costs, limits and the final result. */
    private static Piece replay(Piece base, List<Piece> books, AnvilPlan plan, AnvilRules rules, int limit) {
        List<Piece> results = new ArrayList<>();
        int total = 0;
        boolean[] used = new boolean[books.size()];
        for (Step step : plan.steps()) {
            Piece left = resolve(base, books, results, step.left(), used);
            Piece right = resolve(base, books, results, step.right(), used);
            AnvilRules.Outcome outcome = rules.combine(left, right);
            assertTrue(outcome.ok(), "step failed: " + step);
            assertEquals(step.cost(), outcome.cost());
            assertTrue(outcome.cost() < limit, "over limit: " + step);
            assertEquals(step.result(), outcome.result());
            results.add(outcome.result());
            total += outcome.cost();
        }
        assertEquals(plan.total(), total);
        return results.isEmpty() ? base : results.get(results.size() - 1);
    }

    private static Piece resolve(Piece base, List<Piece> books, List<Piece> results, Operand operand, boolean[] used) {
        return switch (operand.kind()) {
            case BASE -> base;
            case BOOK -> {
                assertFalse(used[operand.index()], "book used twice");
                used[operand.index()] = true;
                yield books.get(operand.index());
            }
            case STEP -> results.get(operand.index());
        };
    }

    // --- single steps (AnvilMenu.createResult) ---

    @Test
    void bookCostIsHalfTheItemMultiplierAtLeastOne() {
        Piece sword = Piece.item(0, ench());
        assertEquals(5, SURVIVAL.combine(sword, Piece.book(0, ench("sharpness", 5))).cost());
        assertEquals(6, SURVIVAL.combine(sword, Piece.book(0, ench("looting", 3))).cost());
        assertEquals(2, SURVIVAL.combine(sword, Piece.book(0, ench("mending", 1))).cost());
        assertEquals(3, SURVIVAL.combine(sword, Piece.book(0, ench("unbreaking", 3))).cost());
        assertEquals(6, SURVIVAL.combine(sword, Piece.book(0, ench("sweeping_edge", 3))).cost());
    }

    @Test
    void leftRightOrderChangesTheCost() {
        Piece sharpness = Piece.book(0, ench("sharpness", 5));
        Piece looting = Piece.book(0, ench("looting", 3));
        assertEquals(6, SURVIVAL.combine(sharpness, looting).cost());
        assertEquals(5, SURVIVAL.combine(looting, sharpness).cost());
        assertEquals(1, SURVIVAL.combine(sharpness, looting).result().repairCost());
    }

    @Test
    void priorWorkPenaltyAddsBothInputsAndDoubles() {
        Piece sword = Piece.item(3, ench("unbreaking", 3));
        Piece book = Piece.book(1, ench("mending", 1));
        AnvilRules.Outcome outcome = SURVIVAL.combine(sword, book);
        assertEquals(3 + 1 + 2, outcome.cost());
        assertEquals(7, outcome.result().repairCost());
        assertEquals(15, SURVIVAL.combine(Piece.item(1, ench()), Piece.book(7, ench("mending", 1))).result().repairCost());
    }

    @Test
    void equalLevelsCombineUpToMax() {
        AnvilRules.Outcome three = SURVIVAL.combine(Piece.book(0, ench("sharpness", 3)), Piece.book(0, ench("sharpness", 3)));
        assertEquals(4, three.result().level("sharpness"));
        assertEquals(4, three.cost());
        AnvilRules.Outcome five = SURVIVAL.combine(Piece.book(0, ench("sharpness", 5)), Piece.book(0, ench("sharpness", 5)));
        assertEquals(5, five.result().level("sharpness"));
        // A lower level on the right still charges the resulting level.
        AnvilRules.Outcome lower = SURVIVAL.combine(Piece.item(0, ench("looting", 3)), Piece.book(0, ench("looting", 1)));
        assertEquals(3, lower.result().level("looting"));
        assertEquals(6, lower.cost());
    }

    @Test
    void incompatibleEnchantmentCostsOnePerConflictAndIsDropped() {
        Piece sword = Piece.item(0, ench("sharpness", 5));
        AnvilRules.Outcome onlySmite = SURVIVAL.combine(sword, Piece.book(0, ench("smite", 5)));
        assertEquals(AnvilRules.Failure.NOTHING_APPLIES, onlySmite.failure());
        AnvilRules.Outcome mixed = SURVIVAL.combine(sword, Piece.book(0, ench("smite", 5, "unbreaking", 3)));
        assertTrue(mixed.ok());
        assertEquals(1 + 3, mixed.cost());
        assertEquals(0, mixed.result().level("smite"));
        assertEquals(3, mixed.result().level("unbreaking"));
    }

    @Test
    void unsupportedEnchantmentIsFreeOnItemsButChargedBetweenBooks() {
        Piece sword = Piece.item(0, ench());
        AnvilRules.Outcome onSword = SURVIVAL.combine(sword, Piece.book(0, ench("protection", 4, "unbreaking", 3)));
        assertEquals(3, onSword.cost());
        assertEquals(0, onSword.result().level("protection"));
        AnvilRules.Outcome onBook = SURVIVAL.combine(Piece.book(0, ench("unbreaking", 3)), Piece.book(0, ench("protection", 4)));
        assertEquals(4, onBook.cost());
        AnvilRules creative = new AnvilRules(SWORD, true);
        assertEquals(4 + 3, creative.combine(sword, Piece.book(0, ench("protection", 4, "unbreaking", 3))).cost());
    }

    @Test
    void itemCannotGoRightOfABookAndStacksCostForty() {
        assertEquals(AnvilRules.Failure.INVALID_ORDER, SURVIVAL.combine(Piece.book(0, ench("mending", 1)), Piece.item(0, ench())).failure());
        Piece stack = new Piece(false, 2, 0, ench());
        assertEquals(40, SURVIVAL.combine(stack, Piece.book(0, ench("mending", 1))).cost());
    }

    // --- plans ---

    @Test
    void referenceSwordMatchesBruteForce() {
        Piece sword = Piece.item(0, ench());
        List<Piece> books = referenceBooks();
        AnvilPlan plan = solve(sword, books);
        assertEquals(Status.OK, plan.status());
        assertEquals(AnvilPlan.Mode.EXACT, plan.mode());
        assertEquals(5, plan.usedBooks().size());
        assertEquals(5, plan.steps().size());
        Piece result = replay(sword, books, plan, SURVIVAL, 40);
        assertEquals(ench("sharpness", 5, "looting", 3, "mending", 1, "unbreaking", 3, "sweeping_edge", 3).entrySet(),
                result.enchants().entrySet().stream().collect(java.util.stream.Collectors.toSet()));

        BruteForce.Result reference = new BruteForce(Vanilla.SWORD, false, 40)
                .solve(false, 0, ench(), List.of(0, 0, 0, 0, 0), books.stream().map(Piece::enchants).toList());
        assertEquals(reference.cost(), plan.total());
        assertEquals(reference.levelSum(), result.levelSum());
        assertEquals(REFERENCE_TOTAL, plan.total());
    }

    /** Cheapest total for the reference sword found by the brute force (not checked against an external calculator). */
    private static final int REFERENCE_TOTAL = 33;

    @Test
    void mergesEqualBooksToReachHigherLevel() {
        Piece sword = Piece.item(0, ench());
        List<Piece> books = List.of(Piece.book(0, ench("sharpness", 3)), Piece.book(0, ench("sharpness", 3)),
                Piece.book(0, ench("sharpness", 4)));
        AnvilPlan plan = solve(sword, books);
        Piece result = replay(sword, books, plan, SURVIVAL, 40);
        assertEquals(5, result.level("sharpness"));
        assertEquals(3, plan.usedBooks().size());
        BruteForce.Result reference = new BruteForce(Vanilla.SWORD, false, 40)
                .solve(false, 0, ench(), List.of(0, 0, 0), books.stream().map(Piece::enchants).toList());
        assertEquals(reference.cost(), plan.total());
    }

    @Test
    void uselessAndConflictingBooksAreSkippedWithReason() {
        Piece sword = Piece.item(0, ench("sharpness", 5, "unbreaking", 2));
        List<Piece> books = List.of(
                Piece.book(0, ench("smite", 5)),
                Piece.book(0, ench("protection", 4)),
                Piece.book(0, ench("unbreaking", 3)),
                Piece.book(0, ench("sharpness", 4)),
                Piece.book(0, ench("unbreaking", 1)));
        AnvilPlan plan = solve(sword, books);
        assertEquals(Status.OK, plan.status());
        assertEquals(List.of(2), plan.usedBooks());
        assertEquals(1, plan.steps().size());
        assertEquals(3, plan.total());
        Map<Integer, AnvilPlan.Skipped> skipped = new java.util.HashMap<>();
        plan.skipped().forEach(s -> skipped.put(s.book(), s));
        assertEquals(Reason.CONFLICT, skipped.get(0).reason());
        assertEquals("sharpness", skipped.get(0).enchant());
        assertEquals(Reason.NOT_APPLICABLE, skipped.get(1).reason());
        assertEquals(Reason.NOTHING_NEW, skipped.get(3).reason());
        assertEquals(Reason.NOTHING_NEW, skipped.get(4).reason());
    }

    @Test
    void conflictingBooksKeepOneEnchantment() {
        Piece sword = Piece.item(0, ench());
        List<Piece> books = List.of(Piece.book(0, ench("smite", 5)), Piece.book(0, ench("sharpness", 5)));
        AnvilPlan plan = solve(sword, books);
        Piece result = replay(sword, books, plan, SURVIVAL, 40);
        assertEquals(5, result.levelSum());
        assertEquals(1, plan.usedBooks().size());
        // Sharpness costs 5 on a book, smite 5 too (anvil cost 2 halved): the tie keeps the lower mask, the smite book.
        assertEquals(5, plan.total());
        assertEquals(Reason.CONFLICT, plan.skipped().get(0).reason());
    }

    @Test
    void tooExpensiveNamesTheBooksToLeaveOut() {
        Piece sword = Piece.item(31, ench());
        List<Piece> books = List.of(Piece.book(0, ench("sharpness", 5)), Piece.book(0, ench("looting", 3)),
                Piece.book(0, ench("mending", 1)));
        AnvilPlan plan = solve(sword, books);
        assertEquals(Status.LIMITED, plan.status());
        assertEquals(List.of(1), plan.leftOut());
        assertEquals(List.of(0, 2), plan.usedBooks());
        Piece result = replay(sword, books, plan, SURVIVAL, 40);
        assertEquals(ench("sharpness", 5, "mending", 1).keySet(), result.enchants().keySet());
        // Sharpness left, mending right (2), then the item: 31 + 1 + 5 + 2 = 39.
        assertEquals(2 + 39, plan.total());
        AnvilPlan.Unreachable unreachable = plan.unreachable();
        assertNotNull(unreachable);
        assertFalse(unreachable.overLimit().isEmpty());
        unreachable.overLimit().values().forEach(cost -> assertTrue(cost >= 40));
        assertEquals(9, unreachable.result().levelSum());

        AnvilPlan creative = solve(sword, books, true, 10);
        assertEquals(Status.OK, creative.status());
        assertEquals(3, creative.usedBooks().size());
        assertNull(creative.unreachable());
        replay(sword, books, creative, new AnvilRules(SWORD, true), Integer.MAX_VALUE);
    }

    @Test
    void penaltyAtLimitBlocksEverything() {
        Piece sword = Piece.item(39, ench());
        AnvilPlan plan = solve(sword, List.of(Piece.book(0, ench("mending", 1))));
        assertEquals(Status.LIMITED, plan.status());
        assertTrue(plan.steps().isEmpty());
        assertEquals(List.of(0), plan.leftOut());
        assertEquals(41, plan.unreachable().overLimit().get(0));
    }

    @Test
    void cheaperFeasibleOrderIsUsedWhenTheCheapestHitsTheLimit() {
        AnvilPlan plan = checkAgainstBruteForce(Piece.item(7, ench()), referenceBooks(), false);
        assertEquals(Status.OK, plan.status());
    }

    @Test
    void greedyMergesEverythingAndIsNeverBelowExact() {
        Piece sword = Piece.item(0, ench());
        List<Piece> books = referenceBooks();
        AnvilPlan greedy = solve(sword, books, false, 0);
        assertEquals(AnvilPlan.Mode.GREEDY, greedy.mode());
        assertEquals(Status.OK, greedy.status());
        Piece result = replay(sword, books, greedy, SURVIVAL, 40);
        assertEquals(15, result.levelSum());
        assertTrue(greedy.total() >= REFERENCE_TOTAL);
    }

    @Test
    void randomCasesMatchBruteForce() {
        Random random = new Random(20260922L);
        String[] pool = {"sharpness", "smite", "looting", "unbreaking", "mending", "fire_aspect", "knockback", "protection", "sweeping_edge"};
        for (int round = 0; round < 150; round++) {
            boolean creative = round % 5 == 4;
            int count = 2 + random.nextInt(3);
            List<Piece> books = new ArrayList<>();
            List<Integer> rcs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                Map<String, Integer> e = new java.util.LinkedHashMap<>();
                int n = 1 + random.nextInt(2);
                for (int k = 0; k < n; k++) {
                    String id = pool[random.nextInt(pool.length)];
                    e.put(id, 1 + random.nextInt(SWORD.get(id).maxLevel()));
                }
                int rc = random.nextInt(4) == 0 ? 1 : 0;
                books.add(Piece.book(rc, e));
                rcs.add(rc);
            }
            int baseRc = new int[]{0, 0, 1, 3, 7, 15}[random.nextInt(6)];
            Map<String, Integer> baseEnch = random.nextBoolean() ? ench() : ench(pool[random.nextInt(pool.length)], 1);
            Piece base = Piece.item(baseRc, baseEnch);
            checkAgainstBruteForce(base, books, creative);
        }
    }

    /** Exact plans must agree with the brute force: same level sum and total, or a justified LIMITED answer. */
    private static AnvilPlan checkAgainstBruteForce(Piece base, List<Piece> books, boolean creative) {
        AnvilPlan plan = solve(base, books, creative, 10);
        Piece result = replay(base, books, plan, new AnvilRules(SWORD, creative), creative ? Integer.MAX_VALUE : 40);
        List<Integer> rcs = books.stream().map(Piece::repairCost).toList();
        List<Map<String, Integer>> enchants = books.stream().map(Piece::enchants).toList();
        BruteForce.Result limited = new BruteForce(Vanilla.SWORD, creative, 40).solve(false, base.repairCost(), base.enchants(), rcs, enchants);
        String context = "base " + base + " books " + books + " plan " + plan;
        if (plan.status() == Status.LIMITED) {
            BruteForce.Result unlimited = new BruteForce(Vanilla.SWORD, creative, Integer.MAX_VALUE)
                    .solve(false, base.repairCost(), base.enchants(), rcs, enchants);
            assertTrue(unlimited.levelSum() > limited.levelSum(), context);
            assertNotNull(plan.unreachable(), context);
            assertEquals(unlimited.cost(), plan.unreachable().total(), context);
            assertEquals(unlimited.levelSum(), plan.unreachable().result().levelSum(), context);
            assertTrue(result.levelSum() <= limited.levelSum(), context);
            assertFalse(plan.leftOut().isEmpty(), context);
        } else {
            assertEquals(limited.levelSum(), result.levelSum(), context);
            assertEquals(limited.cost(), plan.total(), context);
        }
        AnvilPlan greedy = solve(base, books, creative, 0);
        Piece greedyResult = replay(base, books, greedy, new AnvilRules(SWORD, creative), creative ? Integer.MAX_VALUE : 40);
        if (greedyResult.levelSum() == result.levelSum() && plan.status() != Status.LIMITED) {
            assertTrue(greedy.total() >= plan.total(), context);
        }
        return plan;
    }

    @Test
    void tenBooksStayFast() {
        Piece sword = Piece.item(0, ench());
        List<Piece> books = List.of(
                Piece.book(0, ench("sharpness", 4)), Piece.book(0, ench("sharpness", 4)),
                Piece.book(0, ench("looting", 2)), Piece.book(0, ench("looting", 2)),
                Piece.book(0, ench("unbreaking", 2)), Piece.book(0, ench("unbreaking", 2)),
                Piece.book(0, ench("mending", 1)), Piece.book(0, ench("sweeping_edge", 3)),
                Piece.book(0, ench("fire_aspect", 2)), Piece.book(0, ench("knockback", 2)));
        for (boolean creative : new boolean[]{false, true}) {
            AnvilPlan plan = solve(sword, books, creative, 10);
            assertEquals(AnvilPlan.Mode.EXACT, plan.mode());
            assertTrue(plan.nanos() < 5_000_000_000L, "took " + plan.nanos() / 1_000_000 + " ms");
            replay(sword, books, plan, new AnvilRules(SWORD, creative), creative ? Integer.MAX_VALUE : 40);
        }
    }
}
