package dev.skirmish.module.enemycd;

import dev.skirmish.module.nametag.NametagHpModule;
import dev.skirmish.ui.Ui;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.List;

/**
 * Cooldown rows over the heads of players who have no Skirmish HP plate this frame (those get the row on top of
 * their plate instead). Projected like the plates; only players in plain sight.
 */
final class CooldownLabels implements HudElement {
    private static final String L = EnemyCooldownsModule.L;
    private final EnemyCooldownsModule module;

    CooldownLabels(EnemyCooldownsModule module) {
        this.module = module;
    }

    @Override
    public void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        if (!module.isEnabled() || module.visibleIds().isEmpty() || mc.options.hideGui || mc.level == null) {
            return;
        }
        Camera camera = mc.gameRenderer.getMainCamera();
        if (!camera.isInitialized()) {
            return;
        }
        Vec3 cam = camera.position();
        Vector3fc forward = camera.forwardVector();
        float partial = delta.getGameTimeDeltaPartialTick(true);
        long now = Util.getMillis();
        Ui ui = Ui.begin(graphics);
        try {
            for (int id : module.visibleIds()) {
                if (NametagHpModule.replacesNameTag(id) || !(mc.level.getEntity(id) instanceof Player player)) {
                    continue;
                }
                List<CooldownBook.Mark> marks = module.marks(player);
                if (marks.isEmpty()) {
                    continue;
                }
                Vec3 head = ((Entity) player).getPosition(partial).add(0, player.getBbHeight() + ui.num(L + "lift"), 0);
                Vec3 rel = head.subtract(cam);
                if (rel.x * forward.x() + rel.y * forward.y() + rel.z * forward.z() < 0.1) {
                    continue;
                }
                Vec3 ndc = mc.gameRenderer.projectPointToScreen(head);
                if (!Double.isFinite(ndc.x) || !Double.isFinite(ndc.y) || Math.abs(ndc.x) > 1.2 || Math.abs(ndc.y) > 1.2) {
                    continue;
                }
                CooldownRow.draw(ui, marks, (float) ((ndc.x + 1.0) * 0.5 * ui.width()), (float) ((1.0 - ndc.y) * 0.5 * ui.height()), now);
            }
        } finally {
            ui.end();
        }
    }
}
