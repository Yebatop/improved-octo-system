package dev.skirmish.gui;

import dev.skirmish.module.Module;
import dev.skirmish.setting.ActionSetting;
import dev.skirmish.setting.BoolSetting;
import dev.skirmish.setting.EnumSetting;
import dev.skirmish.setting.NumberSetting;
import dev.skirmish.setting.Setting;
import dev.skirmish.setting.StringSetting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Right-hand panel of the menu: one row per setting of the selected module. */
final class SettingsList extends ContainerObjectSelectionList<SettingsList.Entry> {
    private static final int ROW_HEIGHT = 24;
    private static final int WIDGET_HEIGHT = 20;

    private @Nullable Module module;
    private String visibleSignature = "";
    private boolean rebuildRequested;

    SettingsList(Minecraft minecraft, int width, int height, int y) {
        super(minecraft, width, height, y, ROW_HEIGHT);
        this.centerListVertically = false;
    }

    @Override
    public int getRowWidth() {
        return Math.min(this.width - 16, 380);
    }

    void show(Module module) {
        this.module = module;
        rebuild();
        setScrollAmount(0);
    }

    /** Called after any value change: settings with visibleWhen() may appear or disappear. */
    void settingChanged() {
        if (!signature().equals(visibleSignature)) {
            rebuildRequested = true;
        }
    }

    /** Rebuilds outside of widget callbacks (from Screen.tick). */
    void tick() {
        if (rebuildRequested) {
            rebuildRequested = false;
            double scroll = scrollAmount();
            rebuild();
            setScrollAmount(scroll);
        }
    }

    private String signature() {
        if (module == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Setting<?> setting : module.settings()) {
            sb.append(setting.isVisible() ? '1' : '0');
        }
        return sb.toString();
    }

    private void rebuild() {
        clearEntries();
        if (module == null) {
            return;
        }
        Module m = module;
        Font font = minecraft.font;
        addEntry(new TextEntry(Texts.tr(m.descriptionKey(), ""), font));

        if (m.canToggle()) {
            CycleButton<Boolean> enabled = CycleButton.onOffBuilder(m.isEnabled()).displayOnlyValue()
                    .create(0, 0, 100, WIDGET_HEIGHT, Component.translatable("skirmish.menu.enabled"), (button, value) -> {
                        m.setEnabled(value);
                        settingChanged();
                    });
            addEntry(new WidgetEntry(Component.translatable("skirmish.menu.enabled"), enabled, null, font));
        } else {
            addEntry(new TextEntry(Component.translatable("skirmish.menu.always_on"), font));
        }
        addEntry(boolEntry(m.debugLog, Component.translatable("skirmish.menu.debug_log"), font));

        for (Setting<?> setting : m.settings()) {
            if (!setting.isVisible()) {
                continue;
            }
            Entry entry = entryFor(setting, font);
            if (entry != null) {
                addEntry(entry);
            }
        }
        visibleSignature = signature();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private @Nullable Entry entryFor(Setting<?> setting, Font font) {
        Component name = Texts.settingName(setting);
        AbstractWidget widget;
        AbstractWidget extra = null;
        if (setting instanceof BoolSetting bool) {
            return boolEntry(bool, name, font);
        } else if (setting instanceof NumberSetting number) {
            widget = new NumberSlider(0, 0, 100, WIDGET_HEIGHT, number, this::settingChanged);
        } else if (setting instanceof EnumSetting enumSetting) {
            widget = enumButton(enumSetting, name);
        } else if (setting instanceof StringSetting string) {
            MaskedEditBox box = new MaskedEditBox(font, name, string.password());
            box.setMaxLength(string.maxLength());
            box.setValue(string.get());
            box.setResponder(value -> {
                string.set(value);
                settingChanged();
            });
            widget = box;
            if (string.password()) {
                extra = Button.builder(Component.translatable("skirmish.menu.show"), button -> {
                    box.masked = !box.masked;
                    button.setMessage(Component.translatable(box.masked ? "skirmish.menu.show" : "skirmish.menu.hide"));
                }).size(42, WIDGET_HEIGHT).build();
            }
        } else if (setting instanceof ActionSetting action) {
            widget = Button.builder(name, button -> action.run()).size(100, WIDGET_HEIGHT).build();
        } else {
            return null;
        }
        Component tooltip = Texts.settingTooltip(setting);
        if (tooltip != null) {
            widget.setTooltip(Tooltip.create(tooltip));
        }
        return new WidgetEntry(name, widget, extra, font);
    }

    private <E extends Enum<E>> CycleButton<E> enumButton(EnumSetting<E> setting, Component name) {
        return CycleButton.<E>builder(value -> Texts.enumValue(setting, value), setting.get())
                .withValues(setting.values())
                .displayOnlyValue()
                .create(0, 0, 100, WIDGET_HEIGHT, name, (button, value) -> {
                    setting.set(value);
                    settingChanged();
                });
    }

    private Entry boolEntry(BoolSetting setting, Component name, Font font) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(setting.get()).displayOnlyValue()
                .create(0, 0, 100, WIDGET_HEIGHT, name, (b, value) -> {
                    setting.set(value);
                    settingChanged();
                });
        Component tooltip = Texts.settingTooltip(setting);
        if (tooltip != null) {
            button.setTooltip(Tooltip.create(tooltip));
        }
        return new WidgetEntry(name, button, null, font);
    }

    abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
    }

    /** Label on the left half, widget (and an optional small button) on the right half. */
    static final class WidgetEntry extends Entry {
        private final Component label;
        private final AbstractWidget widget;
        private final @Nullable AbstractWidget extra;
        private final Font font;
        private final List<AbstractWidget> children = new ArrayList<>();

        WidgetEntry(Component label, AbstractWidget widget, @Nullable AbstractWidget extra, Font font) {
            this.label = label;
            this.widget = widget;
            this.extra = extra;
            this.font = font;
            children.add(widget);
            if (extra != null) {
                children.add(extra);
            }
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = getContentX();
            int width = getContentWidth();
            int half = width / 2;
            String text = font.plainSubstrByWidth(label.getString(), half - 6);
            graphics.drawString(font, text, x, getContentYMiddle() - font.lineHeight / 2, 0xFFFFFFFF, true);
            int widgetWidth = extra == null ? half : half - 46;
            widget.setX(x + half);
            widget.setY(getContentY());
            widget.setWidth(widgetWidth);
            widget.render(graphics, mouseX, mouseY, partialTick);
            if (extra != null) {
                extra.setX(x + width - 44);
                extra.setY(getContentY());
                extra.render(graphics, mouseX, mouseY, partialTick);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return children;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return children;
        }
    }

    /** One or two lines of grey text (module description, notes). */
    static final class TextEntry extends Entry {
        private final Component text;
        private final Font font;

        TextEntry(Component text, Font font) {
            this.text = text;
            this.font = font;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            List<FormattedCharSequence> lines = font.split(text, getContentWidth());
            int y = getContentY() + (lines.size() > 1 ? 0 : 5);
            for (int i = 0; i < Math.min(2, lines.size()); i++) {
                graphics.drawString(font, lines.get(i), getContentX(), y + i * (font.lineHeight + 1), 0xFFAAAAAA, false);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** EditBox that can render its value as asterisks and never narrates it. */
    static final class MaskedEditBox extends EditBox {
        private final boolean secret;
        boolean masked;

        MaskedEditBox(Font font, Component message, boolean secret) {
            super(font, 100, WIDGET_HEIGHT, message);
            this.secret = secret;
            this.masked = secret;
            addFormatter((text, position) -> masked ? FormattedCharSequence.forward("*".repeat(text.length()), Style.EMPTY) : null);
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return secret ? getMessage().copy() : super.createNarrationMessage();
        }
    }
}
