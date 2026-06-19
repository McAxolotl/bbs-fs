package mchorse.bbs_mod.graphics.line;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix3x2fc;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Line builder 2D
 *
 * This class provides a neat way to construct 2D line
 * segments that is thicker than default OpenGL3 line renderer.
 */
public class LineBuilder <T>
{
    public float thickness;
    public List<Line<T>> lines = new ArrayList<>();

    /* TODO(1.21.11 render): POSITION_COLOR TRIANGLE_STRIP pipeline/layer mirrored from Batcher2D (its
     * pipeline + layer are private). Replaces the removed RenderSystem.setShader(GameRenderer::
     * getPositionColorProgram) + BufferRenderer.drawWithGlobalProgram path. Verify at runtime that
     * this picks up the current 2D GUI projection. */
    private static final RenderPipeline LINE_STRIP = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(net.minecraft.util.Identifier.of(BBSMod.MOD_ID, "pipeline/line_color_strip"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLE_STRIP)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static RenderLayer lineStripLayer;

    private static RenderLayer getLineStripLayer()
    {
        if (lineStripLayer == null)
        {
            lineStripLayer = RenderLayer.of(BBSMod.MOD_ID + "_line_color_strip", RenderSetup.builder(LINE_STRIP).translucent().build());
        }

        return lineStripLayer;
    }

    public LineBuilder(float thickness)
    {
        this.thickness = thickness;
    }

    public LineBuilder<T> add(float x, float y)
    {
        return this.add(x, y, null);
    }

    public LineBuilder<T> add(float x, float y, T user)
    {
        if (this.lines.isEmpty())
        {
            this.push();
        }

        Line line = this.lines.get(this.lines.size() - 1);

        line.add(x, y, user);

        return this;
    }

    public LineBuilder<T> push()
    {
        return this.push(new Line<>());
    }

    public LineBuilder<T> push(Line<T> line)
    {
        this.lines.add(line);

        return this;
    }

    public List<List<LinePoint<T>>> build()
    {
        List<List<LinePoint<T>>> output = new ArrayList<>();

        for (Line line : this.lines)
        {
            List<LinePoint<T>> compiled = line.build(this.thickness);

            if (!compiled.isEmpty())
            {
                output.add(compiled);
            }
        }

        return output;
    }

    public void render(Batcher2D batcher2D, ILineRenderer<T> renderer)
    {
        /* The GUI is 2D-transform based now: getMatrices() returns a Matrix3x2fStack. Promote the
         * current 2D affine transform into a 4x4 matrix so the (Matrix4f)-based line renderers still
         * map their vertices through the active GUI transform. */
        Matrix3x2fc matrix2D = batcher2D.getContext().getMatrices();
        Matrix4f matrix = new Matrix4f().mul(matrix2D);
        List<List<LinePoint<T>>> build = this.build();

        for (List<LinePoint<T>> points : build)
        {
            BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);

            for (LinePoint<T> point : points)
            {
                renderer.render(builder, matrix, point);
            }

            BuiltBuffer built = builder.endNullable();

            if (built != null)
            {
                getLineStripLayer().draw(built);
            }
        }
    }
}
