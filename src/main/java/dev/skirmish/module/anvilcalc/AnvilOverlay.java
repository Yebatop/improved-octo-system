package dev.skirmish.module.anvilcalc;

import dev.skirmish.SkirmishKeys;
import dev.skirmish.module.anvilcalc.AnvilReader.BookRef;
import dev.skirmish.module.anvilcalc.AnvilReader.Snapshot;
import dev.skirmish.module.anvilcalc.calc.AnvilInput;
import dev.skirmish.module.anvilcalc.calc.AnvilPlan;
import dev.skirmish.module.anvilcalc.calc.AnvilSolver;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
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
    private static final int GAP = 4;
    private static final int PADDING = 3;
    private static final int LINE = 10;
    private static final int BUTTON_HEIGHT = 20;

    private final AnvilCalcModule module;
    private final AnvilScreen screen;
    private final ExecutorService executor;

    private @Nullable Button button;
    private @Nullable Snapshot snapshot;
    private @Nullable AnvilPlan plan;
    private @Nullable CompletableFuture<AnvilPlan> pending;
    /** Shown instead of a plan: a problem or «calculating». */
    private List<Component> message = List.of();
    private List<Component> lines = List.of();
    private boolean stale;

    private int panelX;
    private int panelY;
    private int panelWidth;
    private boolean rightSide;

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
        int left = (screen.width - IMAGE_WIDTH) / 2;
        int top = (screen.height - IMAGE_HEIGHT) / 2;
        int roomRight = screen.width - (left + IMAGE_WIDTH) - 2 * GAP;
        int roomLeft = left - 2 * GAP;
        rightSide = roomRight >= 150 || roomRight >= roomLeft;
        panelWidth = Math.max(60, Math.min(260, rightSide ? roomRight : roomLeft));
        panelX = rightSide ? left + IMAGE_WIDTH + GAP : left - GAP - panelWidth;
        panelY = top + BUTTON_HEIGHT + GAP;

        int buttonWidth = Math.min(100, panelWidth);
        int buttonX = rightSide ? panelX : panelX + panelWidth - buttonWidth;
        button = Button.builder(PlanText.tr("button"), b -> calculate())
                .bounds(buttonX, top, buttonWidth, BUTTON_HEIGHT)
                .build();
        button.visible = isShown() && module.showButton.get();
        Screens.getButtons(screen).add(button);

        ScreenEvents.afterBackground(screen).register((s, graphics, mouseX, mouseY, delta) -> renderPanel(graphics));
        ScreenEvents.afterRender(screen).register((s, graphics, mouseX, mouseY, delta) -> renderSlotLabels(graphics));
        ScreenEvents.afterTick(screen).register(s -> tick());
        ScreenKeyboardEvents.afterKeyPress(screen).register((s, event) -> {
            if (module.isEnabled() && SkirmishKeys.ANVILCALC_CALCULATE.matches(event)) {
                calculate();
            }
        });
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
        Snapshot read = AnvilReader.read(menu, player, module.tooExpensiveAt.getInt(), module.exactLimit.getInt());
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
        Button b = button;
        if (b != null) {
            b.visible = isShown() && module.showButton.get();
        }
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

    private void renderPanel(GuiGraphics graphics) {
        if (!isShown() || lines.isEmpty()) {
            return;
        }
        Font font = screen.getFont();
        float scale = module.panelScale.getFloat();
        List<FormattedCharSequence> wrapped = wrap(font, (int) (panelWidth / scale) - 2 * PADDING);
        int availableHeight = screen.height - panelY - GAP;
        int contentHeight = wrapped.size() * LINE + 2 * PADDING;
        if (contentHeight * scale > availableHeight && contentHeight > 0) {
            scale = Math.max(0.5F, (float) availableHeight / contentHeight);
            wrapped = wrap(font, (int) (panelWidth / scale) - 2 * PADDING);
            contentHeight = wrapped.size() * LINE + 2 * PADDING;
        }
        int textWidth = 0;
        for (FormattedCharSequence line : wrapped) {
            textWidth = Math.max(textWidth, font.width(line));
        }
        int boxWidth = textWidth + 2 * PADDING;
        float x = rightSide ? panelX : panelX + panelWidth - boxWidth * scale;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, panelY);
        graphics.pose().scale(scale, scale);
        graphics.fill(0, 0, boxWidth, contentHeight, 0xD0101010);
        graphics.renderOutline(0, 0, boxWidth, contentHeight, 0xFF505050);
        int y = PADDING + 1;
        for (FormattedCharSequence line : wrapped) {
            graphics.drawString(font, line, PADDING, y, 0xFFFFFFFF, true);
            y += LINE;
        }
        graphics.pose().popMatrix();
    }

    private List<FormattedCharSequence> wrap(Font font, int width) {
        List<FormattedCharSequence> out = new ArrayList<>();
        int max = Math.max(40, width);
        for (Component line : lines) {
            out.addAll(font.split(line, max));
        }
        return out;
    }

    private void renderSlotLabels(GuiGraphics graphics) {
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
            colors.put(skipped.book(), 0xFF808080);
        }
        for (int book : result.leftOut()) {
            colors.put(book, 0xFFFF5555);
        }
        for (int book : result.usedBooks()) {
            colors.put(book, 0xFFFFFF55);
        }
        int left = (screen.width - IMAGE_WIDTH) / 2;
        int top = (screen.height - IMAGE_HEIGHT) / 2;
        Font font = screen.getFont();
        for (Map.Entry<Integer, Integer> entry : colors.entrySet()) {
            BookRef ref = current.books().get(entry.getKey());
            Slot slot = findSlot(screen.getMenu(), player, ref);
            if (slot == null) {
                continue;
            }
            graphics.drawString(font, PlanText.letter(entry.getKey()), left + slot.x + 1, top + slot.y + 1, entry.getValue(), true);
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
