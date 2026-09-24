package dev.skirmish.hud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.skirmish.debug.DebugLog;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registry and renderer of the mod's HUD elements. Positions (set in {@link HudEditScreen}) live in
 * config/skirmish/hud.json together with each element's scale. Everything is drawn in one Fabric HUD layer below the
 * chat; all position maths (clamping, stacking, sidebar avoidance) uses the scaled size.
 */
public final class Hud {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Hud instance;

    private final Path file;
    private final List<HudBlock> blocks = new ArrayList<>();
    private final Map<String, Placement> placements = new HashMap<>();
    private boolean editing;

    private Hud(Path file) {
        this.file = file;
        load();
    }

    public static void install(Path file) {
        instance = new Hud(file);
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, Identifier.fromNamespaceAndPath("skirmish", "hud"),
                instance::render);
    }

    public static Hud get() {
        return instance;
    }

    public void register(HudBlock block) {
        blocks.add(block);
    }

    public List<HudBlock> blocks() {
        return Collections.unmodifiableList(blocks);
    }

    public Placement placement(HudBlock block) {
        return placements.getOrDefault(block.id(), block.defaultPlacement());
    }

    /** The user has moved or scaled the element (it no longer follows its default place). */
    public boolean hasPlacement(HudBlock block) {
        return placements.containsKey(block.id());
    }

    public void setPlacement(HudBlock block, Placement placement) {
        placements.put(block.id(), placement);
    }

    /** Element scale set in the HUD editor (1 without a saved placement). */
    public float scale(HudBlock block) {
        return placement(block).scale();
    }

    public void resetPlacement(HudBlock block) {
        placements.remove(block.id());
    }

    public void resetAll() {
        placements.clear();
        save();
    }

    void setEditing(boolean editing) {
        this.editing = editing;
    }

    /** Top-left corner of a block of the given size, kept on screen. */
    public float[] position(HudBlock block, float w, float h, float screenW, float screenH) {
        Placement p = placement(block);
        float x = Math.max(0f, Math.min(screenW - w, p.x(screenW, w)));
        float y = Math.max(0f, Math.min(screenH - h, p.y(screenH, h)));
        return new float[]{Math.round(x), Math.round(y)};
    }

    /**
     * Draws {@code block} with its top-left at {@code (x, y)} and scaled by {@code scale}: the block renders at local
     * (0, 0) inside a translated and scaled pose, so its own layout stays in design px.
     */
    static void draw(Ui ui, HudBlock block, float x, float y, float scale, boolean preview) {
        var pose = ui.graphics().pose();
        pose.pushMatrix();
        try {
            pose.translate(x, y);
            if (scale != 1f) {
                pose.scale(scale, scale);
            }
            block.render(ui, 0f, 0f, preview);
        } finally {
            pose.popMatrix();
        }
    }

    private void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (editing || mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        Ui ui = Ui.begin(graphics);
        try {
            float[] sidebar = SidebarBounds.gui(mc, graphics.guiWidth(), graphics.guiHeight());
            if (sidebar != null) {
                for (int i = 0; i < 4; i++) {
                    sidebar[i] = (float) Ui.toDesign(sidebar[i]);
                }
            }
            Map<String, float[]> drawn = new HashMap<>();
            for (HudBlock block : blocks) {
                try {
                    boolean shown = block.enabled() && block.shown();
                    float a = block.appear.target(shown).value();
                    if (a <= 0f || !block.hasContent()) {
                        continue;
                    }
                    block.update(false);
                    float scale = scale(block);
                    float w = block.width(ui, false) * scale;
                    float h = block.height(ui, false) * scale;
                    float[] at = position(block, w, h, ui.width(), ui.height());
                    if (!placements.containsKey(block.id())) {
                        at = autoPlace(ui, block, at, w, h, drawn, sidebar);
                    }
                    drawn.put(block.id(), new float[]{at[0], at[1], w, h});
                    ui.pushAlpha(a);
                    draw(ui, block, at[0], at[1], scale, false);
                    ui.popAlpha();
                } catch (Throwable t) {
                    DebugLog.error("hud", "render failed: " + block.id(), t);
                }
            }
        } finally {
            ui.end();
        }
    }

    /**
     * Default places only (the user's own placements are kept as set): stack under another block, then step out of
     * the scoreboard sidebar to its left.
     */
    private float[] autoPlace(Ui ui, HudBlock block, float[] at, float w, float h, Map<String, float[]> drawn, float @org.jspecify.annotations.Nullable [] sidebar) {
        float gap = ui.num("layout.hud.stack_gap");
        float x = at[0];
        float y = at[1];
        String under = block.stackUnder();
        if (under != null) {
            float[] ref = drawn.get(under);
            if (ref != null) {
                x = ref[0];
                y = ref[1] + ref[3] + gap;
            } else {
                for (HudBlock other : blocks) {
                    if (other.id().equals(under)) {
                        float[] p = position(other, w, h, ui.width(), ui.height());
                        x = p[0];
                        y = p[1];
                    }
                }
            }
        }
        // w/h are the scaled size, so a scaled element steps out of the sidebar by its real width.
        if (sidebar != null && x < sidebar[0] + sidebar[2] && x + w > sidebar[0] && y < sidebar[1] + sidebar[3] && y + h > sidebar[1]) {
            x = sidebar[0] - gap - w;
        }
        x = Math.max(0f, Math.min(ui.width() - w, x));
        y = Math.max(0f, Math.min(ui.height() - h, y));
        return new float[]{Math.round(x), Math.round(y)};
    }

    private void load() {
        if (!Files.isRegularFile(file)) {
            return;
        }
        try {
            JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            JsonObject elements = root.getAsJsonObject("elements");
            if (elements != null) {
                for (Map.Entry<String, JsonElement> e : elements.entrySet()) {
                    placements.put(e.getKey(), Placement.fromJson(e.getValue().getAsJsonObject()));
                }
            }
        } catch (Exception e) {
            DebugLog.error("hud", "could not read " + file, e);
        }
    }

    public void save() {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        JsonObject elements = new JsonObject();
        placements.forEach((id, p) -> elements.add(id, p.toJson()));
        root.add("elements", elements);
        try {
            Files.createDirectories(file.getParent());
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
            Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            DebugLog.error("hud", "could not write " + file, e);
        }
    }
}
