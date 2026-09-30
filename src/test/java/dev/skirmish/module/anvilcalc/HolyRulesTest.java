package dev.skirmish.module.anvilcalc;

import dev.skirmish.module.anvilcalc.calc.AnvilInput;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Reason;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Status;
import dev.skirmish.module.anvilcalc.calc.AnvilRules;
import dev.skirmish.module.anvilcalc.calc.AnvilSolver;
import dev.skirmish.module.anvilcalc.calc.DubiousBook;
import dev.skirmish.module.anvilcalc.calc.EnchantInfo;
import dev.skirmish.module.anvilcalc.calc.Piece;
import dev.skirmish.module.anvilcalc.calc.RulesProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static dev.skirmish.module.anvilcalc.Vanilla.ench;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HolyWorld rules profiles (wiki "Зачарования", "Сомнительные зачарования"; t.me/hwlite/496). */
class HolyRulesTest {
    private static final Map<String, EnchantInfo> SWORD = Vanilla.catalog(Vanilla.SWORD);
    private static final Map<String, EnchantInfo> CHESTPLATE = Vanilla.catalog(Set.of("protection", "fire_protection",
            "blast_protection", "projectile_protection", "thorns", "unbreaking", "mending", "vanishing_curse"));
    private static final AnvilRules LITE_SWORD = new AnvilRules(SWORD, false, RulesProfile.HOLYWORLD_LITE, false);
    private static final AnvilRules LITE_ARMOR = new AnvilRules(CHESTPLATE, false, RulesProfile.HOLYWORLD_LITE, true);
    private static final AnvilRules VANILLA_SWORD = new AnvilRules(SWORD, false);

    private static AnvilPlan solve(Piece base, List<Piece> books, Map<String, EnchantInfo> catalog, RulesProfile profile,
                                   boolean armor, Set<Integer> dubious) {
        return AnvilSolver.solve(new AnvilInput(base, books, catalog, false, 40, 10, profile, armor, dubious));
    }

    /** Replays a plan step by step with the given rules and returns the final piece. */
    private static Piece replay(Piece base, List<Piece> books, AnvilPlan plan, AnvilRules rules) {
        List<Piece> results = new ArrayList<>();
        int total = 0;
        for (AnvilPlan.Step step : plan.steps()) {
            Piece left = operand(base, books, results, step.left());
            Piece right = operand(base, books, results, step.right());
            AnvilRules.Outcome outcome = rules.combine(left, right);
            assertTrue(outcome.ok(), "step failed: " + step);
            assertEquals(step.cost(), outcome.cost());
            assertTrue(outcome.cost() < 40);
            assertEquals(step.result(), outcome.result());
            results.add(outcome.result());
            total += outcome.cost();
        }
        assertEquals(plan.total(), total);
        return results.isEmpty() ? base : results.getLast();
    }

    private static Piece operand(Piece base, List<Piece> books, List<Piece> results, AnvilPlan.Operand operand) {
        return switch (operand.kind()) {
            case BASE -> base;
            case BOOK -> books.get(operand.index());
            case STEP -> results.get(operand.index());
        };
    }

    // --- profile definitions ---

    @Test
    void vanillaProfileChangesNothing() {
        assertFalse(RulesProfile.VANILLA.changesDefinitions());
        assertSame(SWORD, RulesProfile.VANILLA.adjust(SWORD, false));
        assertSame(CHESTPLATE, RulesProfile.VANILLA.adjust(CHESTPLATE, true));
        assertSame(SWORD, RulesProfile.HOLYWORLD_PRIME.adjust(SWORD, false));
    }

    @Test
    void liteMaximumLevelsFromTheWiki() {
        Map<String, EnchantInfo> lite = RulesProfile.HOLYWORLD_LITE.adjust(Vanilla.catalog(Set.of()), false);
        Map<String, Integer> expected = Map.of("efficiency", 10, "sharpness", 7, "protection", 5, "smite", 7,
                "bane_of_arthropods", 7, "unbreaking", 5, "looting", 5, "fortune", 5, "mending", 2);
        expected.forEach((id, max) -> assertEquals(max, lite.get(id).maxLevel(), id));
        // Not on the list: vanilla maximum.
        assertEquals(3, lite.get("sweeping_edge").maxLevel());
        assertEquals(4, lite.get("blast_protection").maxLevel());
        assertTrue(lite.get("sharpness").incompatible().isEmpty());
    }

    @Test
    void namespacedRegistryIdsGetTheSameCaps() {
        Map<String, EnchantInfo> namespaced = new HashMap<>();
        SWORD.forEach((id, info) -> namespaced.put("minecraft:" + id, new EnchantInfo("minecraft:" + id, info.anvilCost(),
                info.maxLevel(), info.supported(), Set.of())));
        Map<String, EnchantInfo> lite = RulesProfile.HOLYWORLD_LITE.adjust(namespaced, false);
        assertEquals(7, lite.get("minecraft:sharpness").maxLevel());
        assertEquals(2, lite.get("minecraft:mending").maxLevel());
    }

    // --- single anvil steps ---

    @Test
    void equalBooksGoPastTheVanillaMaximum() {
        Piece five = Piece.book(0, ench("sharpness", 5));
        assertEquals(5, VANILLA_SWORD.combine(five, five).result().level("sharpness"));
        AnvilRules.Outcome six = LITE_SWORD.combine(five, five);
        assertEquals(6, six.result().level("sharpness"));
        assertEquals(6, six.cost());
        Piece seven = Piece.book(0, ench("sharpness", 7));
        assertEquals(7, LITE_SWORD.combine(seven, seven).result().level("sharpness"));
        Piece protection = Piece.book(0, ench("protection", 4));
        assertEquals(5, LITE_ARMOR.combine(protection, protection).result().level("protection"));
        Piece mending = Piece.book(0, ench("mending", 1));
        assertEquals(2, LITE_SWORD.combine(Piece.item(0, ench("mending", 1)), mending).result().level("mending"));
    }

    @Test
    void incompatibleEnchantmentsCombine() {
        Piece sword = Piece.item(0, ench("sharpness", 5));
        Piece smite = Piece.book(0, ench("smite", 5));
        assertEquals(AnvilRules.Failure.NOTHING_APPLIES, VANILLA_SWORD.combine(sword, smite).failure());
        AnvilRules.Outcome lite = LITE_SWORD.combine(sword, smite);
        assertTrue(lite.ok());
        assertEquals(ench("sharpness", 5, "smite", 5), lite.result().enchants());
        // Smite anvil cost 2, halved for a book: 1 × 5, and no conflict charge.
        assertEquals(5, lite.cost());
        AnvilRules.Outcome protections = LITE_ARMOR.combine(Piece.item(0, ench("protection", 4)),
                Piece.book(0, ench("blast_protection", 4)));
        assertEquals(ench("protection", 4, "blast_protection", 4), protections.result().enchants());
    }

    @Test
    void armourNeverTakesMendingButBooksStillMerge() {
        Piece chestplate = Piece.item(0, ench());
        Piece mending = Piece.book(0, ench("mending", 1));
        assertEquals(AnvilRules.Failure.NOTHING_APPLIES, LITE_ARMOR.combine(chestplate, mending).failure());
        AnvilRules.Outcome mixed = LITE_ARMOR.combine(chestplate, Piece.book(0, ench("mending", 1, "unbreaking", 3)));
        assertEquals(ench("unbreaking", 3), mixed.result().enchants());
        assertEquals(ench("unbreaking", 3, "mending", 1),
                LITE_ARMOR.combine(Piece.book(0, ench("unbreaking", 3)), mending).result().enchants());
        // Vanilla armour takes Mending.
        assertTrue(new AnvilRules(CHESTPLATE, false).combine(chestplate, mending).ok());
    }

    // --- plans ---

    @Test
    void liteSwordPlanReachesSharpnessSixAndKeepsSmite() {
        Piece sword = Piece.item(0, ench());
        List<Piece> books = List.of(Piece.book(0, ench("sharpness", 5)), Piece.book(0, ench("sharpness", 5)),
                Piece.book(0, ench("smite", 5)), Piece.book(0, ench("unbreaking", 3)));
        AnvilPlan lite = solve(sword, books, SWORD, RulesProfile.HOLYWORLD_LITE, false, Set.of());
        assertEquals(Status.OK, lite.status());
        Piece result = replay(sword, books, lite, LITE_SWORD);
        assertEquals(6, result.level("sharpness"));
        assertEquals(5, result.level("smite"));
        assertEquals(3, result.level("unbreaking"));
        assertEquals(4, lite.usedBooks().size());

        AnvilPlan vanilla = AnvilSolver.solve(new AnvilInput(sword, books, SWORD, false, 40, 10));
        Piece vanillaResult = replay(sword, books, vanilla, VANILLA_SWORD);
        assertEquals(8, vanillaResult.levelSum());
        assertEquals(0, vanillaResult.level("smite") * vanillaResult.level("sharpness"));
    }

    @Test
    void liteArmourPlanSkipsMendingAsNotApplicable() {
        Piece chestplate = Piece.item(0, ench());
        List<Piece> books = List.of(Piece.book(0, ench("protection", 4)), Piece.book(0, ench("protection", 4)),
                Piece.book(0, ench("blast_protection", 4)), Piece.book(0, ench("mending", 1)));
        AnvilPlan plan = solve(chestplate, books, CHESTPLATE, RulesProfile.HOLYWORLD_LITE, true, Set.of());
        Piece result = replay(chestplate, books, plan, LITE_ARMOR);
        assertEquals(ench("protection", 5, "blast_protection", 4).keySet(), result.enchants().keySet());
        assertEquals(5, result.level("protection"));
        assertEquals(List.of(new AnvilPlan.Skipped(3, Reason.NOT_APPLICABLE, null)), plan.skipped());
    }

    @Test
    void primeDubiousBooksAreLeftOut() {
        Piece pickaxe = Piece.item(0, ench());
        Map<String, EnchantInfo> catalog = Vanilla.catalog(Vanilla.PICKAXE);
        List<Piece> books = List.of(Piece.book(0, ench("efficiency", 5)), Piece.book(0, ench("fortune", 3)),
                Piece.book(0, ench("unbreaking", 3)));
        AnvilPlan plan = solve(pickaxe, books, catalog, RulesProfile.HOLYWORLD_PRIME, false, Set.of(1));
        assertEquals(List.of(0, 2).size(), plan.usedBooks().size());
        assertFalse(plan.usedBooks().contains(1));
        assertEquals(new AnvilPlan.Skipped(1, Reason.DUBIOUS, null), plan.skipped().getFirst());
        Piece result = replay(pickaxe, books, plan, new AnvilRules(catalog, false));
        assertEquals(ench("efficiency", 5, "unbreaking", 3).keySet(), result.enchants().keySet());
    }

    @Test
    void dubiousBooksAreRecognisedByNameOrLore() {
        assertTrue(DubiousBook.matches("Сомнительная добыча III", List.of()));
        assertTrue(DubiousBook.matches("§5Сомнительный разящий клинок", List.of()));
        assertTrue(DubiousBook.matches("Незеритовая кирка", List.of("Сомнительная эффективность VI")));
        assertFalse(DubiousBook.matches("Зачарованная книга", List.of("Добыча III")));
        assertFalse(DubiousBook.matches("", List.of()));
    }

    /** The vanilla profile passed explicitly gives exactly the plans of the original vanilla-only input. */
    @Test
    void explicitVanillaProfileIsBitIdentical() {
        Random random = new Random(20260924L);
        String[] pool = {"sharpness", "smite", "looting", "unbreaking", "mending", "fire_aspect", "knockback", "protection", "sweeping_edge"};
        for (int round = 0; round < 100; round++) {
            List<Piece> books = new ArrayList<>();
            int count = 2 + random.nextInt(4);
            for (int i = 0; i < count; i++) {
                Map<String, Integer> e = new LinkedHashMap<>();
                int n = 1 + random.nextInt(2);
                for (int k = 0; k < n; k++) {
                    String id = pool[random.nextInt(pool.length)];
                    e.put(id, 1 + random.nextInt(SWORD.get(id).maxLevel()));
                }
                books.add(Piece.book(random.nextInt(4) == 0 ? 1 : 0, e));
            }
            Piece base = Piece.item(new int[]{0, 1, 3, 7, 15, 31}[random.nextInt(6)], ench());
            boolean creative = round % 7 == 6;
            int exact = round % 3 == 2 ? 0 : 10;
            AnvilPlan old = AnvilSolver.solve(new AnvilInput(base, books, SWORD, creative, 40, exact));
            AnvilPlan explicit = AnvilSolver.solve(new AnvilInput(base, books, SWORD, creative, 40, exact,
                    RulesProfile.VANILLA, false, Set.of()));
            assertEquals(old.status(), explicit.status());
            assertEquals(old.steps(), explicit.steps());
            assertEquals(old.total(), explicit.total());
            assertEquals(old.result(), explicit.result());
            assertEquals(old.usedBooks(), explicit.usedBooks());
            assertEquals(old.skipped(), explicit.skipped());
            assertEquals(old.leftOut(), explicit.leftOut());
            assertEquals(old.unreachable(), explicit.unreachable());
        }
    }
}
