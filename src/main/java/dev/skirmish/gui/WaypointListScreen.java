package dev.skirmish.gui;

import dev.skirmish.waypoint.Waypoint;
import dev.skirmish.waypoint.WaypointManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Simple list of the current server's waypoints: add at my position, set as arrow target, delete. */
public final class WaypointListScreen extends Screen {
    private final @Nullable Screen parent;
    private EditBox nameBox;
    private WaypointList list;

    public WaypointListScreen(@Nullable Screen parent) {
        super(Component.translatable("skirmish.waypoints.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        nameBox = new EditBox(font, width / 2 - 154, 24, 200, 20, Component.translatable("skirmish.waypoints.name"));
        nameBox.setHint(Component.translatable("skirmish.waypoints.name"));
        nameBox.setMaxLength(64);
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.translatable("skirmish.waypoints.add_here"), b -> addHere())
                .bounds(width / 2 + 50, 24, 104, 20).build());

        list = new WaypointList(minecraft, width, height - 52 - 34, 52);
        addRenderableWidget(list);
        list.refresh();

        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 75, height - 28, 150, 20).build());
    }

    private void addHere() {
        String name = nameBox.getValue().isBlank() ? "Waypoint" : nameBox.getValue();
        if (WaypointManager.get().addHere(name) != null) {
            nameBox.setValue("");
            list.refresh();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, 8, 0xFFFFFFFF);
        if (list.children().isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable("skirmish.waypoints.empty"), width / 2, 70, 0xFFAAAAAA);
        }
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }

    final class WaypointList extends ContainerObjectSelectionList<WaypointList.Row> {
        WaypointList(Minecraft minecraft, int width, int height, int y) {
            super(minecraft, width, height, y, 24);
            this.centerListVertically = false;
        }

        @Override
        public int getRowWidth() {
            return Math.min(width - 20, 420);
        }

        void refresh() {
            clearEntries();
            for (Waypoint waypoint : WaypointManager.get().currentServer()) {
                addEntry(new Row(waypoint));
            }
        }

        final class Row extends ContainerObjectSelectionList.Entry<Row> {
            private final Waypoint waypoint;
            private final Button arrow;
            private final Button delete;

            Row(Waypoint waypoint) {
                this.waypoint = waypoint;
                this.arrow = Button.builder(Component.empty(), b -> {
                    WaypointManager manager = WaypointManager.get();
                    Waypoint selected = manager.selected();
                    manager.select(selected != null && selected.id().equals(waypoint.id()) ? null : waypoint.id());
                }).size(86, 20).build();
                this.delete = Button.builder(Component.translatable("skirmish.waypoints.delete"), b -> {
                    WaypointManager.get().remove(waypoint.id());
                    refresh();
                }).size(56, 20).build();
            }

            @Override
            public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
                Font font = WaypointListScreen.this.font;
                Waypoint selected = WaypointManager.get().selected();
                boolean isSelected = selected != null && selected.id().equals(waypoint.id());
                arrow.setMessage(Component.translatable(isSelected ? "skirmish.waypoints.arrow_on" : "skirmish.waypoints.arrow_off"));

                int x = getContentX();
                int right = getContentRight();
                String dimension = waypoint.dimension().replace("minecraft:", "");
                String distance = "";
                Player player = Minecraft.getInstance().player;
                if (player != null && waypoint.dimension().equals(player.level().dimension().identifier().toString())) {
                    distance = "  " + Math.round(Math.sqrt(waypoint.distanceSq(player.getX(), player.getY(), player.getZ()))) + " " + Texts.unit("m");
                }
                int textWidth = right - x - 150;
                graphics.drawString(font, font.plainSubstrByWidth(waypoint.name(), textWidth), x, getContentY() + 1, 0xFF000000 | waypoint.color(), true);
                graphics.drawString(font, font.plainSubstrByWidth(waypoint.coordsText() + "  " + dimension + distance, textWidth),
                        x, getContentY() + 11, 0xFFAAAAAA, false);

                arrow.setPosition(right - 146, getContentY());
                delete.setPosition(right - 58, getContentY());
                arrow.render(graphics, mouseX, mouseY, partialTick);
                delete.render(graphics, mouseX, mouseY, partialTick);
            }

            @Override
            public List<? extends GuiEventListener> children() {
                return List.of(arrow, delete);
            }

            @Override
            public List<? extends NarratableEntry> narratables() {
                return List.of(arrow, delete);
            }
        }
    }
}
