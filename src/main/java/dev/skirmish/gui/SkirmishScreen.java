package dev.skirmish.gui;

import dev.skirmish.module.Module;
import dev.skirmish.module.ModuleManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Main menu: module list on the left (click = select), settings of the selected module on the right. */
public final class SkirmishScreen extends Screen {
    private static final int LEFT_WIDTH = 130;
    private static final int TOP = 28;
    private static final int BOTTOM = 32;
    private static String lastSelected = "";

    private final @Nullable Screen parent;
    private final List<Button> moduleButtons = new ArrayList<>();
    private SettingsList settings;
    private Module selected;

    public SkirmishScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.menu.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        List<Module> modules = ModuleManager.get().all();
        if (selected == null) {
            selected = modules.stream().filter(m -> m.id().equals(lastSelected)).findFirst().orElse(modules.getFirst());
        }

        moduleButtons.clear();
        int y = TOP;
        for (Module module : modules) {
            Button button = Button.builder(moduleLabel(module), b -> select(module))
                    .bounds(8, y, LEFT_WIDTH - 8, 20).build();
            moduleButtons.add(addRenderableWidget(button));
            y += 22;
        }

        settings = new SettingsList(minecraft, width - LEFT_WIDTH - 8, height - TOP - BOTTOM, TOP);
        settings.setX(LEFT_WIDTH + 4);
        addRenderableWidget(settings);
        settings.show(selected);

        addRenderableWidget(Button.builder(Component.translatable("skirmish.menu.waypoints"),
                b -> minecraft.setScreen(new WaypointListScreen(this))).bounds(8, height - 26, LEFT_WIDTH - 8, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(width - 158, height - 26, 150, 20).build());
    }

    private void select(Module module) {
        selected = module;
        lastSelected = module.id();
        settings.show(module);
        refreshLabels();
    }

    private MutableComponent moduleLabel(Module module) {
        MutableComponent name = Texts.tr(module.nameKey(), Texts.humanize(module.id())).copy();
        ChatFormatting color = !module.canToggle() ? ChatFormatting.AQUA : module.isEnabled() ? ChatFormatting.GREEN : ChatFormatting.RED;
        name.withStyle(color);
        if (module == selected) {
            name = Component.literal("> ").append(name);
        }
        return name;
    }

    private void refreshLabels() {
        List<Module> modules = ModuleManager.get().all();
        for (int i = 0; i < moduleButtons.size() && i < modules.size(); i++) {
            moduleButtons.get(i).setMessage(moduleLabel(modules.get(i)));
        }
    }

    @Override
    public void tick() {
        settings.tick();
        refreshLabels();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        ModuleManager.get().markDirty();
        minecraft.setScreen(parent);
    }
}
