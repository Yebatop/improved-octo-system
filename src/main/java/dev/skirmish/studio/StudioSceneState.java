package dev.skirmish.studio;

import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.state.pip.PictureInPictureRenderState;
import org.jspecify.annotations.Nullable;

/** One frame of the Studio scene for {@link StudioSceneRenderer}: the stage, where it goes (GUI px) and its scale (px per block). */
record StudioSceneState(StudioStage stage, float partialTick, int x0, int y0, int x1, int y1, float scale,
                        @Nullable ScreenRectangle scissorArea, @Nullable ScreenRectangle bounds) implements PictureInPictureRenderState {
    StudioSceneState(StudioStage stage, float partialTick, int x0, int y0, int x1, int y1, float scale, @Nullable ScreenRectangle scissor) {
        this(stage, partialTick, x0, y0, x1, y1, scale, scissor, PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissor));
    }
}
