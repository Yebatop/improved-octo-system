package dev.skirmish.fx;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientWorldEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * The effects in the world: one {@link FxField} in world coordinates, advanced every frame and drawn after the
 * entities as quads facing the camera. Only on this client: nothing is spawned in the level and nothing is sent.
 */
public final class FxWorld {
    private static final FxField FIELD = new FxField(2600);
    private static final java.util.List<FrameEmitter> EMITTERS = new java.util.ArrayList<>();

    /** Something that lays down particles every frame (trails, auras). */
    public interface FrameEmitter {
        void emit(FxField field, float dt, float partialTick);
    }
    private static long lastNanos = -1;
    private static boolean installed;

    private FxWorld() {
    }

    public static FxField field() {
        return FIELD;
    }

    public static void addEmitter(FrameEmitter emitter) {
        EMITTERS.add(emitter);
    }

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        FxPipelines.init();
        WorldRenderEvents.AFTER_ENTITIES.register(FxWorld::render);
        ClientWorldEvents.AFTER_CLIENT_WORLD_CHANGE.register((client, level) -> FIELD.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(FIELD::clear));
    }

    private static void render(WorldRenderContext context) {
        long now = System.nanoTime();
        float dt = lastNanos < 0 ? 0f : (now - lastNanos) / 1e9f;
        lastNanos = now;
        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused()) {
            dt = 0f;
        }
        if (dt > 0f && mc.level != null) {
            float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
            for (FrameEmitter e : EMITTERS) {
                e.emit(FIELD, Math.min(dt, 0.1f), pt);
            }
        }
        FIELD.update(dt);
        if (FIELD.live().isEmpty()) {
            return;
        }
        Vec3 cam = context.worldState().cameraRenderState.pos;
        Quaternionf orientation = context.worldState().cameraRenderState.orientation;
        Vector3f right = orientation.transform(new Vector3f(1, 0, 0));
        Vector3f up = orientation.transform(new Vector3f(0, 1, 0));
        Vector3f forward = orientation.transform(new Vector3f(0, 0, -1));
        PoseStack pose = context.matrices();
        context.commandQueue().submitCustomGeometry(pose, FxPipelines.paint(),
                (p, consumer) -> FxGeometry.draw(FIELD.live(), false, p.pose(), consumer, cam.x, cam.y, cam.z, right, up, forward));
        context.commandQueue().submitCustomGeometry(pose, FxPipelines.GLOW,
                (p, consumer) -> FxGeometry.draw(FIELD.live(), true, p.pose(), consumer, cam.x, cam.y, cam.z, right, up, forward));
    }
}
