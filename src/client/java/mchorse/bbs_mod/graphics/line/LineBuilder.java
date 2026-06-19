package mchorse.bbs_mod.graphics.line;

import mchorse.bbs_mod.graphics.GuiQuadMesh;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
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
 * attached to the live {@code DrawContext}. So each polyline is recorded into a {@link GuiQuadMesh} and
 * submitted through {@link Batcher2D#drawQuadMesh(GuiQuadMesh)} — the same deferred, scissor-aware path
 * the keyframe shapes and {@code context.fill} use, so lines composite and clip in lock-step with the
 * batcher's solid fills.</p>
 *
 * <p>The GUI renderer composites every simple element through the hard-wired {@code QUADS} sequential
 * index buffer, so the thick-line {@code TRIANGLE_STRIP} produced by {@link Line#build(float)} is
 * expanded into independent quads here (one quad per pair of consecutive strip "rungs"). A real strip
 * would (a) stitch adjacent, unrelated polylines together with degenerate triangles and (b) mismatch the
 * QUADS index buffer.</p>
 */
public class LineBuilder <T>
{
    public float thickness;
    public List<Line<T>> lines = new ArrayList<>();

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
        /* The mesh folds the live 2D GUI pose into every vertex as it is recorded (the VertexConsumer
         * default vertex(Matrix3x2fc, ...) transforms CPU-side), so recording synchronously against the
         * live matrix is correct. */
        Matrix3x2fc matrix = batcher2D.getContext().getMatrices();
        GuiQuadMesh mesh = new GuiQuadMesh();
        List<List<LinePoint<T>>> build = this.build();

        for (List<LinePoint<T>> points : build)
        {
            /* Each pair of consecutive strip "rungs" (k, k+1) is one quad. Strip rung k = (points[2k],
             * points[2k+1]); perimeter order a -> b -> d -> c keeps the quad convex (cull is off, so the
             * winding is irrelevant). Need at least two rungs (4 vertices) to form a quad. */
            int rungs = points.size() / 2;

            for (int k = 0; k < rungs - 1; k++)
            {
                renderer.render(mesh, matrix, points.get(2 * k));
                renderer.render(mesh, matrix, points.get(2 * k + 1));
                renderer.render(mesh, matrix, points.get(2 * k + 3));
                renderer.render(mesh, matrix, points.get(2 * k + 2));
            }
        }

        batcher2D.drawQuadMesh(mesh);
    }
}
