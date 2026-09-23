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
 * config/skirmish/hud.json. Everything is drawn in one Fabric HUD layer below the chat.
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

    public void setPlacement(HudBlock block, Placement placement) {
        placements.put(block.id(), placement);
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

    private void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (editing || mc.options.hideGui || mc.player == null || mc.level == null) {
            return;
        }
        Ui ui = Ui.begin(graphics);
        try {
            for (HudBlock block : blocks) {
                try {
                    boolean shown = block.enabled() && block.shown();
                    float a = block.appear.target(shown).value();
                    if (a <= 0f || !block.hasContent()) {
                        continue;
                    }
                    block.update(false);
                    float w = block.width(ui, false);
                    float h = block.height(ui, false);
                    float[] at = position(block, w, h, ui.width(), ui.height());
                    ui.pushAlpha(a);
                    block.render(ui, at[0], at[1], false);
                    ui.popAlpha();
                } catch (Throwable t) {
                    DebugLog.error("hud", "render failed: " + block.id(), t);
                }
            }
        } finally {
            ui.end();
        }
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
