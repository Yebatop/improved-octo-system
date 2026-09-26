package dev.skirmish.module.anvilcalc;

import dev.skirmish.module.anvilcalc.AnvilReader.BookRef;
import dev.skirmish.module.anvilcalc.AnvilReader.Snapshot;
import dev.skirmish.module.anvilcalc.calc.AnvilInput;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Operand;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Reason;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Skipped;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan.Step;
import dev.skirmish.module.anvilcalc.calc.Piece;
import dev.skirmish.module.anvilcalc.calc.RulesProfile;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Panel lines (translatable) and debug.log lines (plain ids) for a plan. */
final class PlanText {
    private static final String KEY = "skirmish.anvilcalc.";

    private PlanText() {
    }

    static MutableComponent tr(String key, Object... args) {
        return Component.translatable(KEY + key, args);
    }

    /** A, B, … Z, then a, b, … (an inventory holds at most 37 books). */
    static String letter(int book) {
        return book < 26 ? String.valueOf((char) ('A' + book)) : String.valueOf((char) ('a' + book - 26));
    }

    static Component where(BookRef ref) {
        int slot = ref.inventorySlot();
        if (slot == AnvilReader.RIGHT_SLOT) {
            return tr("where.right_slot");
        }
        if (slot < 9) {
            return tr("where.hotbar", slot + 1);
        }
        return tr("where.inventory", (slot - 9) / 9 + 1, (slot - 9) % 9 + 1);
    }

    static String whereLog(BookRef ref) {
        int slot = ref.inventorySlot();
        return slot == AnvilReader.RIGHT_SLOT ? "anvil right slot" : "inventory slot " + slot;
    }

    static Component enchantNames(Snapshot snapshot, Piece piece) {
        MutableComponent out = Component.empty();
        boolean first = true;
        for (Map.Entry<String, Integer> entry : piece.enchants().entrySet()) {
            Holder<Enchantment> holder = snapshot.holders().get(entry.getKey());
            if (!first) {
                out.append(Component.literal(", ").withStyle(ChatFormatting.GRAY));
            }
            first = false;
            out.append(holder != null ? Enchantment.getFullname(holder, entry.getValue())
                    : Component.literal(entry.getKey() + " " + entry.getValue()));
        }
        return first ? Component.literal("-").withStyle(ChatFormatting.GRAY) : out;
    }

    static Component enchantName(Snapshot snapshot, String id) {
        Holder<Enchantment> holder = snapshot.holders().get(id);
        return holder != null ? holder.value().description() : Component.literal(id);
    }

    private static Component operand(Operand operand) {
        return switch (operand.kind()) {
            case BASE -> tr("operand.item").withStyle(ChatFormatting.AQUA);
            case BOOK -> Component.literal(letter(operand.index())).withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD);
            case STEP -> tr("operand.step", operand.index() + 1).withStyle(ChatFormatting.GREEN);
        };
    }

    static String operandLog(Operand operand) {
        return switch (operand.kind()) {
            case BASE -> "item";
            case BOOK -> "book " + letter(operand.index());
            case STEP -> "result of step " + (operand.index() + 1);
        };
    }

    private static Component bookLine(Snapshot snapshot, AnvilInput input, int book, ChatFormatting letterColor) {
        return tr("book", Component.literal(letter(book)).withStyle(letterColor, ChatFormatting.BOLD),
                enchantNames(snapshot, input.books().get(book)), where(snapshot.books().get(book)));
    }

    private static Component reason(Snapshot snapshot, Skipped skipped) {
        return switch (skipped.reason()) {
            case NOT_APPLICABLE -> tr("reason.not_applicable");
            case CONFLICT -> tr("reason.conflict", skipped.enchant() == null ? "?" : enchantName(snapshot, skipped.enchant()));
            case NOTHING_NEW -> tr("reason.nothing_new");
            case OVER_LIMIT -> tr("reason.over_limit");
            case NOT_NEEDED -> tr("reason.not_needed");
            case DUBIOUS -> tr("reason.dubious");
        };
    }

    static List<Component> lines(Snapshot snapshot, AnvilPlan plan, int playerLevel) {
        AnvilInput input = snapshot.input();
        List<Component> lines = new ArrayList<>();
        if (input == null) {
            return lines;
        }
        String ms = String.format(Locale.ROOT, "%.1f", plan.nanos() / 1e6);
        Component mode = plan.mode() == AnvilPlan.Mode.EXACT ? tr("mode.exact", ms)
                : tr("mode.greedy", plan.candidates(), input.exactLimit(), ms);
        MutableComponent subtitle = mode.copy().withStyle(ChatFormatting.DARK_GRAY);
        if (input.creative()) {
            subtitle.append(Component.literal(" · ")).append(tr("mode.creative"));
        }
        RulesProfile profile = input.profile();
        if (profile.kind() != RulesProfile.Kind.VANILLA) {
            subtitle.append(Component.literal(" · ")).append(tr("rules." + profile.kind().name().toLowerCase(Locale.ROOT)));
            if (snapshot.autoRules()) {
                subtitle.append(tr("rules.auto"));
            }
        }
        lines.add(subtitle);
        lines.addAll(warnings(snapshot, input));

        if (plan.status() == AnvilPlan.Status.NOTHING_TO_DO) {
            lines.add(tr("problem.nothing").withStyle(ChatFormatting.GOLD));
        }
        if (plan.status() == AnvilPlan.Status.LIMITED) {
            AnvilPlan.Unreachable unreachable = plan.unreachable();
            if (unreachable != null && !unreachable.overLimit().isEmpty()) {
                Map.Entry<Integer, Integer> worst = null;
                for (Map.Entry<Integer, Integer> entry : unreachable.overLimit().entrySet()) {
                    if (worst == null || entry.getValue() > worst.getValue()) {
                        worst = entry;
                    }
                }
                lines.add(tr("limited", worst.getKey() + 1, worst.getValue(), input.tooExpensiveAt()).withStyle(ChatFormatting.RED));
            }
            if (!plan.leftOut().isEmpty()) {
                MutableComponent names = Component.empty();
                for (int i = 0; i < plan.leftOut().size(); i++) {
                    int book = plan.leftOut().get(i);
                    if (i > 0) {
                        names.append(", ");
                    }
                    names.append(Component.literal(letter(book)).withStyle(ChatFormatting.BOLD))
                            .append(" (").append(enchantNames(snapshot, input.books().get(book))).append(")");
                }
                lines.add(tr("leave_out", names).withStyle(ChatFormatting.RED));
            }
            if (plan.steps().isEmpty()) {
                lines.add(tr("limited_none", input.tooExpensiveAt()).withStyle(ChatFormatting.RED));
            } else {
                lines.add(tr("plan_for_rest").withStyle(ChatFormatting.GOLD));
            }
        }

        if (!plan.usedBooks().isEmpty()) {
            lines.add(tr("books").withStyle(ChatFormatting.GOLD));
            for (int book : plan.usedBooks()) {
                lines.add(bookLine(snapshot, input, book, ChatFormatting.YELLOW));
            }
        }
        if (!plan.steps().isEmpty()) {
            lines.add(tr("steps").withStyle(ChatFormatting.GOLD));
            for (int i = 0; i < plan.steps().size(); i++) {
                Step step = plan.steps().get(i);
                lines.add(tr("step", i + 1, operand(step.left()), operand(step.right()),
                        Component.literal(Integer.toString(step.cost())).withStyle(ChatFormatting.GREEN)));
            }
            MutableComponent total = tr("total", plan.total()).withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
            if (!input.creative() && playerLevel < plan.total()) {
                total.append(tr("have", playerLevel).withStyle(ChatFormatting.RED));
            }
            lines.add(total);
            lines.add(tr("result", enchantNames(snapshot, plan.result())));
        }

        List<Skipped> shown = plan.skipped().stream().filter(s -> s.reason() != Reason.OVER_LIMIT).toList();
        if (!shown.isEmpty()) {
            lines.add(tr("skipped").withStyle(ChatFormatting.GRAY));
            for (Skipped skipped : shown) {
                lines.add(tr("skipped_line", Component.literal(letter(skipped.book())).withStyle(ChatFormatting.BOLD),
                        enchantNames(snapshot, input.books().get(skipped.book())), reason(snapshot, skipped))
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        return lines;
    }

    /** Server-rule warnings: Lite armour rules, Prime «сомнительные» books and items. */
    static List<Component> warnings(Snapshot snapshot, AnvilInput input) {
        List<Component> out = new ArrayList<>();
        RulesProfile profile = input.profile();
        if (profile.armorNotRepairable() && input.baseArmor()) {
            out.add(tr("warn.lite_armor").withStyle(ChatFormatting.GOLD));
        }
        if (!input.dubious().isEmpty()) {
            MutableComponent letters = Component.empty();
            List<Integer> sorted = input.dubious().stream().sorted().toList();
            for (int i = 0; i < sorted.size(); i++) {
                if (i > 0) {
                    letters.append(", ");
                }
                letters.append(Component.literal(letter(sorted.get(i))).withStyle(ChatFormatting.BOLD));
            }
            out.add(tr("warn.dubious_books", letters).withStyle(ChatFormatting.GOLD));
        }
        if (snapshot.baseDubious()) {
            out.add(tr("warn.dubious_item").withStyle(ChatFormatting.GOLD));
        }
        return out;
    }

    static List<String> logInput(Snapshot snapshot) {
        List<String> out = new ArrayList<>();
        AnvilInput input = snapshot.input();
        if (input == null) {
            return out;
        }
        Piece base = input.base();
        out.add(String.format(Locale.ROOT, "input: item %s x%d, enchantments [%s], repair cost %d, %s, too expensive at %d, exact up to %d books",
                snapshot.base().getItemHolder().getRegisteredName(), base.count(), base.describe(), base.repairCost(),
                input.creative() ? "creative (no limit, every enchantment applicable)" : "survival", input.tooExpensiveAt(), input.exactLimit()));
        out.add(String.format(Locale.ROOT, "rules: %s%s, left item is %sarmour, dubious books %s, dubious item %s",
                input.profile().kind(), snapshot.autoRules() ? " (auto)" : "", input.baseArmor() ? "" : "not ",
                input.dubious().stream().sorted().map(PlanText::letter).toList(), snapshot.baseDubious()));
        for (int i = 0; i < input.books().size(); i++) {
            Piece book = input.books().get(i);
            out.add(String.format(Locale.ROOT, "book %s (%s): [%s], repair cost %d%s", letter(i), whereLog(snapshot.books().get(i)),
                    book.describe(), book.repairCost(), book.count() > 1 ? ", stack of " + book.count() : ""));
        }
        return out;
    }

    static List<String> logPlan(AnvilInput input, AnvilPlan plan) {
        List<String> out = new ArrayList<>();
        out.add(String.format(Locale.ROOT, "search: %s, %d candidate books, %d states, %.2f ms, status %s",
                plan.mode() == AnvilPlan.Mode.EXACT ? "exact DP over subsets" : "greedy (candidates > " + input.exactLimit() + ")",
                plan.candidates(),
                plan.states(), plan.nanos() / 1e6, plan.status()));
        for (Skipped skipped : plan.skipped()) {
            out.add(String.format(Locale.ROOT, "not used: book %s: %s%s", letter(skipped.book()), skipped.reason(),
                    skipped.enchant() != null ? " (" + skipped.enchant() + ")" : ""));
        }
        AnvilPlan.Unreachable unreachable = plan.unreachable();
        if (unreachable != null) {
            out.add(String.format(Locale.ROOT, "impossible within %d: cheapest full order costs %d levels -> [%s]",
                    input.tooExpensiveAt(), unreachable.total(), unreachable.result().describe()));
            for (int i = 0; i < unreachable.steps().size(); i++) {
                Step step = unreachable.steps().get(i);
                out.add(String.format(Locale.ROOT, "  full order step %d: left %s, right %s -> cost %d%s", i + 1,
                        operandLog(step.left()), operandLog(step.right()), step.cost(),
                        unreachable.overLimit().containsKey(i) ? " (TOO EXPENSIVE)" : ""));
            }
            List<String> letters = plan.leftOut().stream().map(PlanText::letter).toList();
            out.add("leave out: " + (letters.isEmpty() ? "-" : String.join(", ", letters)));
        }
        for (int i = 0; i < plan.steps().size(); i++) {
            Step step = plan.steps().get(i);
            out.add(String.format(Locale.ROOT, "step %d: left %s, right %s -> cost %d, result [%s], repair cost %d", i + 1,
                    operandLog(step.left()), operandLog(step.right()), step.cost(), step.result().describe(), step.result().repairCost()));
        }
        out.add(String.format(Locale.ROOT, "total %d levels, result [%s], repair cost %d", plan.total(), plan.result().describe(),
                plan.result().repairCost()));
        return out;
    }
}
