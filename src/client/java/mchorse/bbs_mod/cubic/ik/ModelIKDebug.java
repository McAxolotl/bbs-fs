package mchorse.bbs_mod.cubic.ik;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.model.bobj.BOBJModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderSetup;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Minimal IK overlay: each chain is a clean run of thin wires through round
 * joint dots, the effector picked out in a warm accent, and the target shown as
 * a small dot inside a caged sphere. It reads the model-local pivot frames the
 * renderer already produced — IK is applied to the rig before this runs, so the
 * frames are the solved chain (re-solving would double-apply the pole angle).
 * Gated globally by {@link #enabled}.
 *
 * <p>{@link #renderStencil} mirrors the goal markers into the picking pass so a
 * click on a green target selects its bone, exactly as if its (often mesh-less)
 * bone had been clicked directly.
 */
public final class ModelIKDebug
{
    private static final float[] WIRE = {0.90F, 0.92F, 0.95F};
    private static final float[] EFFECTOR = {0.30F, 0.64F, 1.00F};
    private static final float[] GOAL = {0.22F, 0.84F, 0.55F};

    public static boolean enabled;

    /* Ported to 1.21.11: 1.21.5 removed RenderSystem.setShader/state toggles and
     * BufferRenderer.drawWithGlobalProgram. Immediate-mode geometry is now built into a
     * BufferBuilder, finished into a BuiltBuffer, and submitted through a RenderLayer carrying a
     * RenderPipeline (the pipeline encodes the old depth-test/blend/cull state). This overlay always
     * drew with depth-test disabled (so the gizmos sit on top), no cull and translucent blend, so we
     * register two POSITION_COLOR no-depth pipelines: one for the joint/goal triangles and one for the
     * chain lines. Mirrors mchorse.bbs_mod.graphics.Draw. */
    private static final RenderPipeline POSITION_COLOR_TRIS_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/ik_debug_position_color_tris"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static final RenderPipeline POSITION_COLOR_LINES_NO_DEPTH = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/ik_debug_position_color_lines"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.DEBUG_LINES)
            .withBlend(BlendFunction.TRANSLUCENT)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    /* The picking pass encodes the object index as the exact vertex colour, so blend MUST be off
     * (a blended pixel would corrupt the id read back from the framebuffer). Depth-test stays
     * disabled to match the visual overlay. */
    private static final RenderPipeline POSITION_COLOR_STENCIL = RenderPipelines.register(
        RenderPipeline.builder(RenderPipelines.POSITION_COLOR_SNIPPET)
            .withLocation(Identifier.of(BBSMod.MOD_ID, "pipeline/ik_debug_position_color_stencil"))
            .withVertexFormat(VertexFormats.POSITION_COLOR, VertexFormat.DrawMode.TRIANGLES)
            .withoutBlend()
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .withCull(false)
            .build()
    );

    private static RenderLayer trisLayer;
    private static RenderLayer linesLayer;
    private static RenderLayer stencilLayer;

    private ModelIKDebug()
    {
    }

    private static RenderLayer getTrisLayer()
    {
        if (trisLayer == null)
        {
            trisLayer = RenderLayer.of(BBSMod.MOD_ID + "_ik_debug_tris",
                RenderSetup.builder(POSITION_COLOR_TRIS_NO_DEPTH).translucent().build());
        }

        return trisLayer;
    }

    private static RenderLayer getLinesLayer()
    {
        if (linesLayer == null)
        {
            linesLayer = RenderLayer.of(BBSMod.MOD_ID + "_ik_debug_lines",
                RenderSetup.builder(POSITION_COLOR_LINES_NO_DEPTH).translucent().build());
        }

        return linesLayer;
    }

    private static RenderLayer getStencilLayer()
    {
        if (stencilLayer == null)
        {
            stencilLayer = RenderLayer.of(BBSMod.MOD_ID + "_ik_debug_stencil",
                RenderSetup.builder(POSITION_COLOR_STENCIL).build());
        }

        return stencilLayer;
    }

    /** Finish a buffer and submit it through the given layer (no-op on an empty buffer). */
    private static void flush(BufferBuilder builder, RenderLayer layer)
    {
        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            /* TODO(1.21.11 render): verify at runtime. RenderLayer.draw uploads + draws with the
             * layer pipeline; previously this was BufferRenderer.drawWithGlobalProgram. */
            layer.draw(built);
        }
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

        Map<String, PivotFrame> frames = collectFrames(model, compiled);

        /* Depth-test/cull/blend state now lives in the layer pipelines (no-depth, no-cull,
         * translucent), so the old RenderSystem toggles are gone. */
        stack.push();

        if (model instanceof BOBJModel)
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        }

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            drawChain(stack, frames, chain, selectedTip);
        }

        stack.pop();
    }

    private static Map<String, PivotFrame> collectFrames(IModel model, ModelIKCache.Compiled compiled)
    {
        Set<String> wanted = new HashSet<>();

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames);

        return frames;
    }

    /**
     * Mirrors the goals into the picking pass: a pickable cube at each goal,
     * registered under the chain's target bone. Must run after the model's bones
     * are registered so the goal ids fall right after them — the cube encodes
     * {@code stencilMap.objectIndex} as its colour and {@code addPicking} then
     * claims that same id. The matrix matches the visual overlay's.
     */
    public static void renderStencil(MatrixStack stack, IModel model, MapType ikData, StencilMap stencilMap, Form form)
    {
        if (!enabled || model == null || ikData == null || stencilMap == null)
        {
            return;
        }

        ModelIKCache.Compiled compiled = ModelIKCache.getFromData(model, ikData);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = collectFrames(model, compiled);

        /* Depth-test disabled (and blend off) is encoded in the stencil layer's pipeline. */
        stack.push();

        if (model instanceof BOBJModel)
        {
            stack.multiply(RotationAxis.POSITIVE_Y.rotation(MathUtils.PI));
        }

        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (ModelIKCache.CompiledChain chain : compiled.chains())
        {
            Vector3f goal = position(frames, chain.target());

            if (goal == null)
            {
                continue;
            }

            int id = stencilMap.objectIndex;
            float s = goalRadius(frames, chain.chainRootToEffector());

            Draw.fillBox(builder, stack, goal.x - s, goal.y - s, goal.z - s, goal.x + s, goal.y + s, goal.z + s, (id & 0xFF) / 255F, (id >> 8 & 0xFF) / 255F, (id >> 16 & 0xFF) / 255F, 1F);

            stencilMap.addPicking(form, chain.target());
        }

        flush(builder, getStencilLayer());

        stack.pop();
    }

    /** Clickable goal half-size, scaled to the bone span so it fits any rig. */
    private static float goalRadius(Map<String, PivotFrame> frames, List<String> ids)
    {
        Vector3f root = ids.isEmpty() ? null : position(frames, ids.get(0));
        Vector3f tip = ids.isEmpty() ? null : position(frames, ids.get(ids.size() - 1));
        float span = root != null && tip != null ? root.distance(tip) : 0.5F;

        return span / Math.max(1, ids.size() - 1) * 0.2F;
    }

    private static void drawChain(MatrixStack stack, Map<String, PivotFrame> frames, ModelIKCache.CompiledChain chain, String selectedTip)
    {
        List<String> ids = chain.chainRootToEffector();
        int n = ids.size();

        if (n < 2)
        {
            return;
        }

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

        Vector3f tip = pts.get(n - 1);

        float total = 0F;

        for (int i = 0; i < n - 1; i++)
        {
            total += pts.get(i).distance(pts.get(i + 1));
        }

        float unit = total / (n - 1);
        boolean sel = selectedTip == null || selectedTip.isEmpty() || chain.tip().equals(selectedTip);
        float a = sel ? 1F : 0.4F;

        Matrix4f matrix = stack.peek().getPositionMatrix();

        /* Lines: the bone chain plus a faint bridge to the goal. */
        BufferBuilder lines = Tessellator.getInstance().begin(VertexFormat.DrawMode.DEBUG_LINES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < n - 1; i++)
        {
            addLine(lines, matrix, pts.get(i), pts.get(i + 1), WIRE, 0.9F * a);
        }

        addLine(lines, matrix, tip, target, GOAL, 0.4F * a);

        flush(lines, getLinesLayer());

        /* Solid spheres: joints, the accented effector, and the goal. */
        BufferBuilder dots = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        for (int i = 0; i < n - 1; i++)
        {
            orb(dots, stack, pts.get(i), unit * 0.07F, WIRE, a);
        }

        orb(dots, stack, tip, unit * 0.1F, EFFECTOR, a);
        orb(dots, stack, target, unit * 0.12F, GOAL, a);

        flush(dots, getTrisLayer());
    }

    private static void orb(BufferBuilder builder, MatrixStack stack, Vector3f p, float radius, float[] col, float a)
    {
        stack.push();
        stack.translate(p.x, p.y, p.z);
        Draw.sphere(builder, stack, radius, 9, 9, col[0], col[1], col[2], a);
        stack.pop();
    }

    private static Vector3f position(Map<String, PivotFrame> frames, String bone)
    {
        PivotFrame frame = frames.get(bone);

        return frame == null ? null : new Vector3f(frame.position());
    }

    private static void addLine(BufferBuilder builder, Matrix4f matrix, Vector3f p1, Vector3f p2, float[] col, float a)
    {
        builder.vertex(matrix, p1.x, p1.y, p1.z).color(col[0], col[1], col[2], a);
        builder.vertex(matrix, p2.x, p2.y, p2.z).color(col[0], col[1], col[2], a);
    }
}
