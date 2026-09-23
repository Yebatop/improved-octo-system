package dev.skirmish.ui.render;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** GUI pipeline for SDF shapes (assets/skirmish/shaders/core/ui_shape.*). */
public final class UiPipelines {
    public static final RenderPipeline SHAPE = RenderPipelines.register(RenderPipeline.builder(RenderPipelines.MATRICES_PROJECTION_SNIPPET)
            .withLocation(Identifier.fromNamespaceAndPath("skirmish", "pipeline/ui_shape"))
            .withVertexShader(Identifier.fromNamespaceAndPath("skirmish", "core/ui_shape"))
            .withFragmentShader(Identifier.fromNamespaceAndPath("skirmish", "core/ui_shape"))
            .withBlend(BlendFunction.TRANSLUCENT)
            .withVertexFormat(DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build());

    private UiPipelines() {
    }
}
