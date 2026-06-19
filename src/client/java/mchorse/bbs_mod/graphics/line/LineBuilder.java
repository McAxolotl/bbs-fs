package mchorse.bbs_mod.graphics.line;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.ScreenRect;
import net.minecraft.client.gui.render.state.SimpleGuiElementRenderState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureSetup;
import net.minecraft.util.Identifier;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Line builder 2D
 *
 * This class provides a neat way to construct 2D line
 * segments that is thicker than default OpenGL3 line renderer.
 *
 * <p>Ported to 1.21.11: the original immediate-mode path
 * ({@code Tessellator.begin(TRIANGLE_STRIP) -> RenderLayer.draw}) is dropped by the two-phase GUI
 * (1.21.6+), which only composites the {@link net.minecraft.client.gui.render.state.GuiRenderState}
 * attached to the live {@link DrawContext}. So each polyline is recorded as a custom
 * {@link SimpleGuiElementRenderState} added through {@code context.state.addSimpleElement(...)} — the
 * exact path {@code DrawContext.fill} uses (snapshotted 2D pose + current scissor), so it composites and
 * clips in lock-step with the batcher's solid fills.</p>
 *
 * <p>The GUI renderer batches every simple element that shares a {@link RenderPipeline} into ONE buffer
 * and draws it indexed as {@code QUADS}. A {@code TRIANGLE_STRIP} would therefore (a) stitch adjacent,
 * unrelated polylines together with degenerate triangles and (b) mismatch the QUADS sequential index
 * buffer. So the thick-line {@code TRIANGLE_STRIP} produced by {@link Line#build(float)} is expanded
 * into independent quads here (one quad per pair of consecutive strip "rungs"), which batch safely.</p>
 */
public class LineBuilder <T>
{
    public float thickness;
    public List<Line<T>> lines = new ArrayList<>();

    /* POSITION_COLOR QUADS pipeline for thick-line geometry, composited through the two-phase GUI. No
     * depth test (GUI overlay), no cull (winding is irrelevant for flat quads), translucent blend (lines
     * carry alpha, e.g. the faint interpolation grid). */
    private static final RenderPipeline LINE_QUADS = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/line_color_quads"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.QUADS)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

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
        DrawContext context = batcher2D.getContext();

        /* Snapshot the live 2D GUI transform (the Matrix3x2fStack mutates after this call) and the
         * current scissor, mirroring DrawContext.fill. */
        Matrix3x2fc pose = new Matrix3x2f(context.getMatrices());
        ScreenRect scissor = context.scissorStack.peekLast();
        List<List<LinePoint<T>>> build = this.build();

        for (List<LinePoint<T>> points : build)
        {
            /* Need at least two strip "rungs" (4 vertices) to form a single quad. */
            if (points.size() < 4)
            {
                continue;
            }

            context.state.addSimpleElement(new LineQuads<>(pose, scissor, computeBounds(points, pose, scissor), points, renderer));
        }
    }

    /** Local-space AABB of the strip vertices, transformed by the pose and clipped to the scissor. */
    private static <T> ScreenRect computeBounds(List<LinePoint<T>> points, Matrix3x2fc pose, ScreenRect scissor)
    {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;

        for (LinePoint<T> point : points)
        {
            minX = Math.min(minX, point.x);
            minY = Math.min(minY, point.y);
            maxX = Math.max(maxX, point.x);
            maxY = Math.max(maxY, point.y);
        }

        int x = (int) Math.floor(minX);
        int y = (int) Math.floor(minY);
        ScreenRect bounds = new ScreenRect(x, y, (int) Math.ceil(maxX) - x + 1, (int) Math.ceil(maxY) - y + 1).transformEachVertex(pose);

        return scissor == null ? bounds : bounds.intersection(scissor);
    }

    /**
     * One polyline recorded into the deferred {@link net.minecraft.client.gui.render.state.GuiRenderState}.
     * The thick-line strip is expanded into independent quads in {@link #setupVertices(VertexConsumer)}.
     */
    private record LineQuads<T>(Matrix3x2fc pose, ScreenRect scissor, ScreenRect bounds, List<LinePoint<T>> points, ILineRenderer<T> renderer) implements SimpleGuiElementRenderState
    {
        @Override
        public void setupVertices(VertexConsumer consumer)
        {
            int rungs = this.points.size() / 2;

            /* Each pair of consecutive rungs (k, k+1) is one quad. Strip rung k = (points[2k], points[2k+1]);
             * perimeter order a -> b -> d -> c keeps the quad convex. Cull is off, so winding is irrelevant. */
            for (int k = 0; k < rungs - 1; k++)
            {
                this.renderer.render(consumer, this.pose, this.points.get(2 * k));
                this.renderer.render(consumer, this.pose, this.points.get(2 * k + 1));
                this.renderer.render(consumer, this.pose, this.points.get(2 * k + 3));
                this.renderer.render(consumer, this.pose, this.points.get(2 * k + 2));
            }
        }

        @Override
        public RenderPipeline pipeline()
        {
            return LINE_QUADS;
        }

        @Override
        public TextureSetup textureSetup()
        {
            return TextureSetup.empty();
        }

        @Override
        public ScreenRect scissorArea()
        {
            return this.scissor;
        }

        @Override
        public ScreenRect bounds()
        {
            return this.bounds;
        }
    }
}
