package dev.skirmish.module.anvilcalc;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.anvilcalc.AnvilReader.BookRef;
import dev.skirmish.module.anvilcalc.AnvilReader.Snapshot;
import dev.skirmish.module.anvilcalc.calc.AnvilInput;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan;
import dev.skirmish.module.anvilcalc.calc.AnvilSolver;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import dev.skirmish.hud.HudStyle;
import dev.skirmish.ui.Ui;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

/** The «Calculate» button, the plan panel beside the anvil GUI and letters on the book slots. Draws only. */
final class AnvilOverlay {
    private static final int IMAGE_WIDTH = 176;
    private static final int IMAGE_HEIGHT = 166;

    private final AnvilCalcModule module;
    private final AnvilScreen screen;
    private final ExecutorService executor;

    private dev.skirmish.ui.widget.@Nullable Button button;
    private @Nullable Snapshot snapshot;
    private @Nullable AnvilPlan plan;
    private @Nullable CompletableFuture<AnvilPlan> pending;
    /** Shown instead of a plan: a problem or «calculating». */
    private List<Component> message = List.of();
    private List<Component> lines = List.of();
    private boolean stale;


    AnvilOverlay(AnvilCalcModule module, AnvilScreen screen, ExecutorService executor) {
        this.module = module;
        this.screen = screen;
        this.executor = executor;
    }

    AnvilScreen screen() {
        return screen;
    }

    /** Called after every init/resize of the screen (Fabric recreates the per-screen events then). */
    void attach() {
        button = new dev.skirmish.ui.widget.Button(() -> PlanText.tr("button").getString(), true, this::calculate)
                .layout("layout.anvil.");
        dev.skirmish.ui.widget.ScreenWidgets.attach(screen, this::draw);
        ScreenEvents.afterTick(screen).register(s -> tick());
        ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
            if (module.isEnabled() && SkirmishKeys.ANVILCALC_CALCULATE.matches(event)) {
                calculate();
            }
        });
    }

    /** Button, plan panel and slot letters, beside the anvil GUI (right side when there is room). */
    private void draw(Ui ui, dev.skirmish.ui.widget.ScreenWidgets widgets, double mx, double my) {
        if (!isShown()) {
            return;
        }
        String l = "layout.anvil.";
        float left = (float) Ui.toDesign((screen.width - IMAGE_WIDTH) / 2);
        float top = (float) Ui.toDesign((screen.height - IMAGE_HEIGHT) / 2);
        float right = left + (float) Ui.toDesign(IMAGE_WIDTH);
        float gap = ui.num(l + "gap");
        float width = ui.num(l + "width");
        boolean rightSide = ui.width() - right - gap >= width || ui.width() - right >= left;
        width = Math.min(width, Math.max(ui.num(l + "min_width"), rightSide ? ui.width() - right - gap * 2 : left - gap * 2));
        float x = rightSide ? right + gap : left - gap - width;
        float y = top;
        dev.skirmish.ui.widget.Button b = button;
        if (b != null && module.showButton.get()) {
            float bw = Math.min(width, b.preferredWidth(ui));
            float bh = b.preferredHeight(ui);
            b.bounds(rightSide ? x : x + width - bw, y, bw, bh);
            widgets.widget(ui, b, mx, my);
            y += bh + gap;
        }
        renderPanel(ui, x, y, width);
        renderSlotLabels(ui, left, top);
    }

    private boolean isShown() {
        return module.isEnabled();
    }

    void calculate() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        AnvilMenu menu = screen.getMenu();
        Snapshot read = AnvilReader.read(menu, player, module.tooExpensiveAt.getInt(), module.exactLimit.getInt(),
                module.rulesProfile(), module.rules.get() == AnvilProfile.AUTO);
        snapshot = read;
        plan = null;
        pending = null;
        stale = false;
        if (read.problem() != null) {
            module.log("calculate: " + (read.problem() == AnvilReader.Problem.EMPTY ? "left slot empty"
                    : "left item " + read.base().getItemHolder().getRegisteredName() + " cannot hold enchantments"));
            show(PlanText.tr(read.problem() == AnvilReader.Problem.EMPTY ? "problem.empty" : "problem.not_enchantable")
                    .withStyle(ChatFormatting.GOLD));
            return;
        }
        AnvilInput input = read.input();
        if (input == null) {
            return;
        }
        PlanText.logInput(read).forEach(module::log);
        if (input.books().isEmpty()) {
            module.log("calculate: no enchanted books in the inventory");
            show(PlanText.tr("problem.no_books").withStyle(ChatFormatting.GOLD));
            return;
        }
        show(PlanText.tr("calculating").withStyle(ChatFormatting.GRAY));
        pending = CompletableFuture.supplyAsync(() -> AnvilSolver.solve(input), executor);
    }

    private void tick() {
        CompletableFuture<AnvilPlan> future = pending;
        Snapshot current = snapshot;
        if (future != null && future.isDone() && current != null && current.input() != null) {
            pending = null;
            try {
                AnvilPlan result = future.join();
                plan = result;
                PlanText.logPlan(current.input(), result).forEach(module::log);
                rebuild();
            } catch (RuntimeException e) {
                module.error("AnvilCalc search failed", e);
                show(PlanText.tr("problem.error").withStyle(ChatFormatting.RED));
            }
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (current != null && !stale && player != null
                && !AnvilReader.signature(screen.getMenu(), player).equals(current.signature())) {
            stale = true;
            module.log("inputs changed since the last calculation (anvil or inventory); plan marked stale");
            rebuild();
        }
    }

    private void show(Component line) {
        message = List.of(line);
        rebuild();
    }

    private void rebuild() {
        Snapshot current = snapshot;
        AnvilPlan result = plan;
        List<Component> out = new ArrayList<>();
        if (stale) {
            out.add(PlanText.tr("stale").withStyle(ChatFormatting.YELLOW));
        }
        if (current != null && result != null) {
            LocalPlayer player = Minecraft.getInstance().player;
            out.addAll(PlanText.lines(current, result, player == null ? 0 : player.experienceLevel));
        } else {
            out.addAll(message);
        }
        lines = out;
    }

    private void renderPanel(Ui ui, float x, float y, float width) {
        if (lines.isEmpty()) {
            return;
        }
        float inset = HudStyle.insetX(ui);
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : lines) {
            wrapped.addAll(ui.wrapRich("anvil_line", line, width - inset * 2));
        }
        float lh = ui.lineHeight("anvil_line") + ui.num("layout.anvil.line_gap");
        float height = HudStyle.insetY(ui) * 2 + wrapped.size() * lh - ui.num("layout.anvil.line_gap");
        HudStyle.panel(ui, x, y, width, height);
        float ly = y + HudStyle.insetY(ui);
        for (FormattedCharSequence line : wrapped) {
            ui.richLine("anvil_line", line, x + inset, ly);
            ly += lh;
        }
    }

    private void renderSlotLabels(Ui ui, float left, float top) {
        Snapshot current = snapshot;
        AnvilPlan result = plan;
        if (!isShown() || !module.slotLabels.get() || current == null || result == null || stale) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Map<Integer, Integer> colors = new HashMap<>();
        for (AnvilPlan.Skipped skipped : result.skipped()) {
            colors.put(skipped.book(), ui.color("text_3"));
        }
        for (int book : result.leftOut()) {
            colors.put(book, ui.color("bad"));
        }
        for (int book : result.usedBooks()) {
            colors.put(book, ui.color("warn"));
        }
        for (Map.Entry<Integer, Integer> entry : colors.entrySet()) {
            BookRef ref = current.books().get(entry.getKey());
            Slot slot = findSlot(screen.getMenu(), player, ref);
            if (slot == null) {
                continue;
            }
            float sx = left + (float) Ui.toDesign(slot.x);
            float sy = top + (float) Ui.toDesign(slot.y);
            float size = ui.num("layout.anvil.letter_size");
            ui.rect(sx, sy, size, size, ui.theme().radius("chip"), ui.color("card"));
            String letter = PlanText.letter(entry.getKey());
            ui.textCentered("anvil_letter", letter, sx + (size - ui.textWidth("anvil_letter", letter)) / 2f, sy, size, entry.getValue());
        }
    }

    private static @Nullable Slot findSlot(AnvilMenu menu, LocalPlayer player, BookRef ref) {
        if (ref.inventorySlot() == AnvilReader.RIGHT_SLOT) {
            return menu.getSlot(AnvilMenu.ADDITIONAL_SLOT);
        }
        for (Slot slot : menu.slots) {
            if (slot.container == player.getInventory() && slot.getContainerSlot() == ref.inventorySlot()) {
                return slot;
            }
        }
        return null;
    }
}
