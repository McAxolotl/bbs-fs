package mchorse.bbs_mod.cubic.ik;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.graphics.Draw;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws the IK chains on top of the rendered model: the solved bone chain, the
 * target and the automatic bend direction. It is drawn straight from the same
 * model-local pivot frames the renderer/solver use (so it matches the mesh in
 * any context — form editor, film, world) and re-runs the solver to show the
 * solved chain. Connections use {@code DEBUG_LINES} (a vertex pair per segment)
 * so they always join the points exactly. Gated globally by {@link #enabled}.
 */
public final class ModelIKDebug
{
    private static final float JOINT = 0.014F;
    private static final float MARKER = 0.022F;

    private static final int MAX_ITERATIONS = 12;
    private static final float TOLERANCE = 1.0e-4f;

    public static boolean enabled;

    private ModelIKDebug()
    {
    }

    public static void render(MatrixStack stack, IModel model, MapType ikData, String selectedTip)
    {
        if (!enabled || model == null || ikData == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(model, ikData);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Set<String> wanted = new HashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames);

        RenderSystem.disableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            drawChain(stack, frames, chain, selectedTip);
        }

        RenderSystem.enableDepthTest();
    }

    private static void drawChain(MatrixStack stack, Map<String, PivotFrame> frames, ModelIKCache.CompiledChain chain, String selectedTip)
    {
        List<String> ids = chain.chainRootToEffector();
        int n = ids.size();
        List<Vector3f> pts = new ArrayList<>(n);

        for (int i = 0; i < n; i++)
        {
            Vector3f p = position(frames, ids.get(i));

            if (p == null)
            {
                return;
            }

            pts.add(p);
        }

        Vector3f target = position(frames, chain.target());

        if (target == null)
        {
            return;
        }

        float poleAngleRad = (float) Math.toRadians(chain.poleAngle());

        /* Run the same solver in this space so the debug shows the solved chain. */
        IKSolver.solve(pts, new Vector3f(target), chain.pole(), poleAngleRad, chain.softness(), MAX_ITERATIONS, TOLERANCE);

        float a = chain.tip().equals(selectedTip) ? 1F : 0.6F;
        Vector3f root = pts.get(0);
        Vector3f tip = pts.get(n - 1);
        Vector3f elbow = pts.get(Math.min(1, n - 1));

        /* Automatic bend direction = the elbow's offset from the limb axis. */
        Vector3f bendBase = axisProjection(root, tip, elbow);

        /* Lines */
        Matrix4f matrix = stack.peek().getPositionMatrix();
        BufferBuilder lines = Tessellator.getInstance().getBuffer();
        lines.begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < n - 1; i++)
        {
            addLine(lines, matrix, pts.get(i), pts.get(i + 1), 0.2F, 0.85F, 1F, a);
        }

        addLine(lines, matrix, tip, target, 0.2F, 1F, 0.3F, a);

        if (bendBase != null)
        {
            addLine(lines, matrix, bendBase, elbow, 1F, 0.6F, 0.1F, a);
        }

        BufferRenderer.drawWithGlobalProgram(lines.end());

        /* Dots */
        BufferBuilder dots = Tessellator.getInstance().getBuffer();
        dots.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < n - 1; i++)
        {
            if (i == 0)
            {
                cube(dots, stack, pts.get(i), JOINT, 1F, 0.3F, 0.3F, a);
            }
            else
            {
                cube(dots, stack, pts.get(i), JOINT, 0.95F, 0.95F, 0.95F, a);
            }
        }

        cube(dots, stack, tip, JOINT * 1.3F, 1F, 0.95F, 0.25F, a);
        cube(dots, stack, target, MARKER, 0.2F, 1F, 0.3F, a);

        BufferRenderer.drawWithGlobalProgram(dots.end());
    }

    /** Closest point on the root-tip axis to the elbow; null if the limb is degenerate. */
    private static Vector3f axisProjection(Vector3f root, Vector3f tip, Vector3f elbow)
    {
        Vector3f axis = new Vector3f(tip).sub(root);
        float lenSq = axis.lengthSquared();

        if (lenSq <= 1.0e-10f)
        {
            return null;
        }

        float t = new Vector3f(elbow).sub(root).dot(axis) / lenSq;

        return new Vector3f(root).fma(t, axis);
    }

    private static Vector3f position(Map<String, PivotFrame> frames, String bone)
    {
        PivotFrame frame = frames.get(bone);

        return frame == null ? null : new Vector3f(frame.position());
    }

    private static void addLine(BufferBuilder builder, Matrix4f matrix, Vector3f p1, Vector3f p2, float r, float g, float b, float a)
    {
        builder.vertex(matrix, p1.x, p1.y, p1.z).color(r, g, b, a).next();
        builder.vertex(matrix, p2.x, p2.y, p2.z).color(r, g, b, a).next();
    }

    private static void cube(BufferBuilder builder, MatrixStack stack, Vector3f p, float s, float r, float g, float b, float a)
    {
        Draw.fillBox(builder, stack, p.x - s, p.y - s, p.z - s, p.x + s, p.y + s, p.z + s, r, g, b, a);
    }
}
