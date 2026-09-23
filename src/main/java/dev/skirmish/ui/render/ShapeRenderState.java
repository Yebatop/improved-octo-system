package dev.skirmish.ui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.gui.render.state.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;
import org.jspecify.annotations.Nullable;

/**
 * One SDF quad (see ui_shape.vsh for the attribute encoding). Coordinates are in the pose's space (design px);
 * {@code l*} are the local shape coordinates at the quad corners.
 */
public record ShapeRenderState(
        Matrix3x2f pose,
        float x0, float y0, float x1, float y1,
        float lx0, float ly0, float lx1, float ly1,
        int shapeA, int shapeB, int shapeC, int shapeD,
        float mode,
        float rounding,
        int color,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    static final float FIXED = 16f;

    @Override
    public void buildVertices(VertexConsumer consumer) {
        vertex(consumer, x0, y0, lx0, ly0);
        vertex(consumer, x0, y1, lx0, ly1);
        vertex(consumer, x1, y1, lx1, ly1);
        vertex(consumer, x1, y0, lx1, ly0);
    }

    private void vertex(VertexConsumer consumer, float x, float y, float lx, float ly) {
        consumer.addVertexWith2DPose(pose, x, y)
                .setColor(color)
                .setUv(lx, ly)
                .setUv1(shapeA, shapeB)
                .setUv2(shapeC, shapeD)
                .setNormal(mode, rounding / 8f, 0f);
    }

    @Override
    public RenderPipeline pipeline() {
        return UiPipelines.SHAPE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    static @Nullable ScreenRectangle bounds(Matrix3x2f pose, float x0, float y0, float x1, float y1, @Nullable ScreenRectangle scissor) {
        Vector2f a = pose.transformPosition(x0, y0, new Vector2f());
        Vector2f b = pose.transformPosition(x1, y0, new Vector2f());
        Vector2f c = pose.transformPosition(x1, y1, new Vector2f());
        Vector2f d = pose.transformPosition(x0, y1, new Vector2f());
        int minX = (int) Math.floor(Math.min(Math.min(a.x, b.x), Math.min(c.x, d.x)));
        int minY = (int) Math.floor(Math.min(Math.min(a.y, b.y), Math.min(c.y, d.y)));
        int maxX = (int) Math.ceil(Math.max(Math.max(a.x, b.x), Math.max(c.x, d.x)));
        int maxY = (int) Math.ceil(Math.max(Math.max(a.y, b.y), Math.max(c.y, d.y)));
        ScreenRectangle rect = new ScreenRectangle(minX, minY, Math.max(1, maxX - minX), Math.max(1, maxY - minY));
        return scissor != null ? scissor.intersection(rect) : rect;
    }
}
