package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

public class Gizmo
{
    /* Every pickable gizmo handle owns a distinct stencil id so the combined
     * mode can show move/scale/rotate at once and a pick unambiguously names
     * both the operation and the axis. {@link Handle} ties these together;
     * single-operation modes simply render a subset of them. {@link #STENCIL_MAX}
     * stays the highest id so form parts (which begin right after it) never
     * collide with a handle. */
    public final static int STENCIL_X = 1;
    public final static int STENCIL_Y = 2;
    public final static int STENCIL_Z = 3;
    public final static int STENCIL_XZ = 4;
    public final static int STENCIL_XY = 5;
    public final static int STENCIL_ZY = 6;
    public final static int STENCIL_SCALE_X = 7;
    public final static int STENCIL_SCALE_Y = 8;
    public final static int STENCIL_SCALE_Z = 9;
    public final static int STENCIL_SCALE_XZ = 10;
    public final static int STENCIL_SCALE_XY = 11;
    public final static int STENCIL_SCALE_ZY = 12;
    public final static int STENCIL_ROTATE_X = 13;
    public final static int STENCIL_ROTATE_Y = 14;
    public final static int STENCIL_ROTATE_Z = 15;
    public final static int STENCIL_TRACKBALL = 16;
    public final static int STENCIL_VIEW = 17;
    /** Screen-space translate handle: the big centre cube that grabs in the view plane. */
    public final static int STENCIL_SCREEN = 18;

    /** Highest gizmo handle id; form-part stencil ids begin right after it. */
    public final static int STENCIL_MAX = STENCIL_SCREEN;

    /** Radius of the view-plane ring relative to the per-axis rings. */
    private final static float VIEW_RING_SCALE = 1.2F;

    /** Move/scale handles shrink inside the rotation rings in combined mode. */
    private final static float COMBINED_INNER_SCALE = 0.6F;

    /** Half-size of the scale handle's end cube, in gizmo-local units (× axes scale × thickness).
     *  Based on scale/thickness rather than the per-pass line offset, so the cube is the same
     *  size in the visual and stencil passes and its hitbox matches the drawn cube exactly. */
    private final static float SCALE_CUBE_HALF = 0.032F;

    /** Half-size of the screen-space (centre) translate cube, in gizmo-local units
     *  (× axes scale × thickness). Twice the translate bars' half-thickness (the visual
     *  bar offset is {@code 0.008}), so the cube reads as a grabbable handle. Like
     *  {@link #SCALE_CUBE_HALF} it is offset-independent so the visual and stencil
     *  passes match and the hitbox lines up with the drawn cube. */
    private final static float SCREEN_CUBE_HALF = 0.016F;

    public final static Gizmo INSTANCE = new Gizmo();

    private Mode mode = Mode.COMBINED;
    /** The mode to return to when combined mode is toggled off. */
    private Mode previousMode = Mode.TRANSLATE;

    private int index;
    private int mouseX;
    private int mouseY;

    private UIPropTransform currentTransform;

    /* Snapshot of the matrix stack at the moment the gizmo is rendered.
     * Combined with a camera (whose view matrix matches the one applied to
     * the stack during rendering) this lets us recover the gizmo's true
     * world position without having to thread it through every call site. */
    private final Matrix4f lastRenderMatrix = new Matrix4f();
    private boolean hasLastRenderMatrix;

    /* TODO(1.21.11 render): VBO caching for rotation rings disabled — net.minecraft.client.gl.VertexBuffer
     * was removed in the 1.21.5 GPU rewrite (replaced by GpuBuffer/VertexBufferManager). Re-introduce a
     * cached-geometry path once the custom RenderPipeline foundation lands. */
    private float lastScale = -1F;
    private float lastThickness = -1F;
    /** World-space radius the sphere is drawn at, expressed in
     *  the local coordinate frame {@link #lastRenderMatrix} describes
     *  (i.e. already includes axesScale and the per-frame distanceScale).
     *  Captured in {@link #render} so {@link #computeScreenRadius} can
     *  project an edge point and report the sphere's real pixel size. */
    private float lastSphereLocalRadius;

    /** Driven by {@link mchorse.bbs_mod.ui.film.controller.UIFilmController}'s
     *  per-frame hover pass. When true the sphere is repainted with
     *  {@link BBSSettings#stencilHighlightColor} so the user can see
     *  the cursor sits in its pick disc. */
    private boolean sphereHovered;

    private Gizmo()
    {}

    /**
     * Reconstruct the world-space origin of the gizmo from the most recent
     * render matrix and the camera that drove that render. The stack at
     * render time is {@code view * translate(-cam.pos) * gizmoChain}, so
     * undoing the view rotation and adding camera position yields the real
     * world coordinates.
     */
    public boolean computeWorldOrigin(Camera camera, Vector3d out)
    {
        if (!this.hasLastRenderMatrix)
        {
            return false;
        }

        Matrix4f undoView = new Matrix4f(camera.view).invert().mul(this.lastRenderMatrix);
        Vector3f cameraRelative = undoView.getTranslation(new Vector3f());

        out.set(
            camera.position.x + cameraRelative.x,
            camera.position.y + cameraRelative.y,
            camera.position.z + cameraRelative.z
        );

        return true;
    }

    /**
     * Recover the gizmo's world-space axes from the latest render matrix and
     * camera. Columns of {@code out} become the unit-length world directions
     * of the gizmo's X/Y/Z handles. Returns {@code false} if the gizmo hasn't
     * been rendered yet, in which case the caller should skip ray-based
     * dragging.
     */
    public boolean computeWorldAxes(Camera camera, Matrix3f out)
    {
        if (!this.hasLastRenderMatrix)
        {
            return false;
        }

        Matrix4f undoView = new Matrix4f(camera.view).invert().mul(this.lastRenderMatrix);

        out.set(undoView.get3x3(new Matrix3f()));

        Vector3f col = new Vector3f();

        for (int i = 0; i < 3; i++)
        {
            out.getColumn(i, col);

            float lenSq = col.lengthSquared();

            if (lenSq < 1.0E-12F)
            {
                return false;
            }

            col.div((float) Math.sqrt(lenSq));
            out.setColumn(i, col);
        }

        return true;
    }

    public Mode getMode()
    {
        return this.mode;
    }

    public void setSphereHovered(boolean hovered)
    {
        this.sphereHovered = hovered;
    }

    /** The trackball sphere shows in the dedicated rotate mode and in combined. */
    public boolean hasSphere()
    {
        return this.mode == Mode.ROTATE || this.mode == Mode.COMBINED;
    }

    public boolean isSphereInteractive()
    {
        if (!BBSSettings.gizmos.get() || !BBSSettings.rotate3dSphere.get())
        {
            return false;
        }

        if (!this.hasSphere())
        {
            return false;
        }

        if (this.currentTransform != null && this.currentTransform.isEditing() && !this.currentTransform.isTrackball())
        {
            return false;
        }

        return true;
    }

    public boolean isTrackballDragging()
    {
        return this.currentTransform != null && this.currentTransform.isEditing() && this.currentTransform.isTrackball();
    }

    /**
     * Project the gizmo's origin onto the viewport in pixel space and
     * write the result into {@code out}. Returns {@code false} when the
     * gizmo hasn't been rendered yet or the origin sits behind the
     * camera ({@code clip.w <= 0}) — caller should skip the hover check.
     *
     * <p>{@link #lastRenderMatrix} already encodes
     * {@code view * translate(-cam) * gizmoChain}, so left-multiplying
     * by the projection matrix yields clip space directly. NDC → pixel
     * mapping then accounts for the inverted Y between OpenGL NDC
     * (Y up) and screen coordinates (Y down).
     */
    public boolean computeScreenCenter(Matrix4f projection, float areaX, float areaY, float areaW, float areaH, Vector2f out)
    {
        if (!this.hasLastRenderMatrix)
        {
            return false;
        }

        Matrix4f mvp = new Matrix4f(projection).mul(this.lastRenderMatrix);
        Vector4f clip = mvp.transform(new Vector4f(0F, 0F, 0F, 1F));

        if (clip.w <= 0F)
        {
            return false;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;

        out.x = areaX + (ndcX * 0.5F + 0.5F) * areaW;
        out.y = areaY + (1F - (ndcY * 0.5F + 0.5F)) * areaH;

        return true;
    }

    /**
     * Effective pixel radius of the rotate-mode sphere on screen, so the
     * hover/pick disc in {@link mchorse.bbs_mod.ui.film.controller.UIFilmController}
     * matches the sphere's actual visual size at the current camera
     * distance and axes scale.
     *
     * <p>Projects three local-axis edge points
     * ({@code (r,0,0)}, {@code (0,r,0)}, {@code (0,0,r)}) onto the
     * viewport and returns the largest pixel distance from the
     * projected centre — covers all camera orientations without
     * needing a true ellipse-from-sphere derivation. Returns {@code 0}
     * when the gizmo hasn't been rendered yet, the centre is behind
     * the camera, or the sphere radius hasn't been captured.
     */
    public float computeScreenRadius(Matrix4f projection, float areaX, float areaY, float areaW, float areaH)
    {
        if (!this.hasLastRenderMatrix || this.lastSphereLocalRadius <= 0F)
        {
            return 0F;
        }

        Vector2f center = new Vector2f();

        if (!this.computeScreenCenter(projection, areaX, areaY, areaW, areaH, center))
        {
            return 0F;
        }

        Matrix4f mvp = new Matrix4f(projection).mul(this.lastRenderMatrix);
        float r = this.lastSphereLocalRadius;
        float[] xs = {r, 0F, 0F};
        float[] ys = {0F, r, 0F};
        float[] zs = {0F, 0F, r};
        float maxSq = 0F;

        for (int i = 0; i < 3; i++)
        {
            Vector4f clip = mvp.transform(new Vector4f(xs[i], ys[i], zs[i], 1F));

            if (clip.w <= 0F) continue;

            float ndcX = clip.x / clip.w;
            float ndcY = clip.y / clip.w;
            float px = areaX + (ndcX * 0.5F + 0.5F) * areaW;
            float py = areaY + (1F - (ndcY * 0.5F + 0.5F)) * areaH;
            float dx = px - center.x;
            float dy = py - center.y;
            float d = dx * dx + dy * dy;

            if (d > maxSq) maxSq = d;
        }

        return (float) Math.sqrt(maxSq);
    }

    /**
     * Set the persistent gizmo mode. Returns {@code true} iff the mode
     * actually changed — callers (notably the tool-switch hotkey
     * helper) use this to distinguish a real switch from a no-op press
     * on the already-active tool.
     */
    public boolean setMode(Mode mode)
    {
        if (!BBSSettings.gizmos.get())
        {
            return false;
        }

        boolean same = this.mode == mode;

        this.mode = mode;

        return !same;
    }

    /**
     * Toggle the combined mode: entering it remembers the mode left behind so a
     * second press returns there. This is the only way out of combined, since
     * in that mode the G/S/R hotkeys run their operation without switching the
     * displayed handles.
     */
    public boolean toggleCombined()
    {
        if (this.mode == Mode.COMBINED)
        {
            return this.setMode(this.previousMode);
        }

        Mode previous = this.mode;

        if (this.setMode(Mode.COMBINED))
        {
            this.previousMode = previous;

            return true;
        }

        return false;
    }

    public boolean start(int index, int mouseX, int mouseY, UIPropTransform transform)
    {
        return this.start(index, mouseX, mouseY, transform, null);
    }

    public boolean start(int index, int mouseX, int mouseY, UIPropTransform transform, GizmoDrag drag)
    {
        if (!BBSSettings.gizmos.get())
        {
            return false;
        }

        Handle handle = Handle.byIndex(index);

        if (handle == null)
        {
            return false;
        }

        this.index = index;
        this.mouseX = mouseX;
        this.mouseY = mouseY;

        this.currentTransform = transform;

        if (transform != null)
        {
            switch (handle.op)
            {
                case MOVE:
                case SCALE:
                case ROTATE:
                    transform.enableMode(handle.op.modeOrdinal, handle.axis, handle.axis2, drag);
                    break;
                case SCREEN:
                    transform.enableScreenTranslate(drag);
                    break;
                case TRACKBALL:
                    if (BBSSettings.rotate3dSphere.get()) transform.enableTrackball(drag);
                    break;
                case VIEW:
                    transform.enableViewRotate(drag);
                    break;
            }
        }

        return true;
    }

    public void trackTransform(UIPropTransform transform)
    {
        this.currentTransform = transform;
    }

    public void clearTrackedTransform(UIPropTransform transform)
    {
        if (this.currentTransform == transform)
        {
            this.currentTransform = null;

            if (this.index < STENCIL_X || this.index > STENCIL_MAX)
            {
                this.index = -1;
            }
        }
    }

    public void stop()
    {
        this.index = -1;

        if (this.currentTransform != null)
        {
            this.currentTransform.acceptChanges();
        }

        this.currentTransform = null;
    }

    public void render(MatrixStack stack)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        stack.push();
        MatrixStackUtils.scaleBack(stack);
        this.captureRenderMatrix(stack);

        if (BBSSettings.gizmos.get())
        {
            float distanceScale = this.getAxesDistanceScale(stack);

            /* Cache the sphere's effective world radius (in
             * {@link #lastRenderMatrix}'s coordinate frame) so
             * {@link #computeScreenRadius} can report the real on-screen
             * pixel size for hover/pick distance checks. */
            this.lastSphereLocalRadius = 0.22F * BBSSettings.axesScale.get() * distanceScale;

            stack.push();
            stack.scale(distanceScale, distanceScale, distanceScale);
            this.drawAxes(stack, 0.25F, 0.008F);
            stack.pop();
        }
        else
        {
            float distanceScale = this.getAxesDistanceScale(stack);

            stack.push();
            stack.scale(distanceScale, distanceScale, distanceScale);
            Draw.coolerAxes(stack, 0.25F, 0.008F);
            stack.pop();
        }

        this.drawInfiniteLine(stack);
        stack.pop();
    }

    private float getAxesDistanceScale(MatrixStack stack)
    {
        Vector3f cameraRelative = stack.peek().getPositionMatrix().getTranslation(new Vector3f());

        /* TODO(1.21.11 render): RenderSystem.getProjectionMatrix() was removed (1.21.5; only a
         * GpuBufferSlice accessor remains), so the perspective FOV can no longer be derived from the
         * live projection. Fall back to the configured FOV until the projection is threaded through
         * the new pipeline. */
        return BBSSettings.getAxesDistanceScale(cameraRelative.length());
    }

    private void drawInfiniteLine(MatrixStack stack)
    {
        int debugIndex = this.index;

        if ((debugIndex < STENCIL_X || debugIndex > STENCIL_ZY) && this.currentTransform != null)
        {
            debugIndex = this.currentTransform.getDebugLineStencilIndex();
        }

        if (debugIndex < STENCIL_X || debugIndex > STENCIL_ZY)
        {
            return;
        }

        /* TODO(1.21.11 render): immediate-mode infinite line disabled. RenderSystem.setShader /
         * GameRenderer.getPositionColorProgram / BufferRenderer.drawWithGlobalProgram / depthFunc were
         * removed in the 1.21.5 GPU rewrite. Re-implement once the POSITION_COLOR pipeline + depth-state
         * RenderLayer foundation is in place (geometry: Draw.fillBox bars along the active axis/axes). */
    }

    private void updateVbos()
    {
        /* TODO(1.21.11 render): rotation-ring/sphere VBO caching disabled — VertexBuffer was removed in
         * the 1.21.5 GPU rewrite. When the cached-geometry path is rebuilt (GpuBuffer + custom
         * POSITION_COLOR RenderPipeline), re-upload the ring (arc3D), stencil ring, and sphere meshes
         * here, keyed on axesScale/axesThickness changes. */
        this.lastScale = BBSSettings.axesScale.get();
        this.lastThickness = BBSSettings.axesThickness.get();
    }

    private void drawCachedSphere(MatrixStack stack, float r, float g, float b, float a)
    {
        /* TODO(1.21.11 render): cached sphere VBO draw disabled (VertexBuffer.draw / setShaderColor /
         * getProjectionMatrix removed in the 1.21.5 GPU rewrite). Re-route through the new pipeline. */
    }

    /* Cached gizmo geometry is uploaded as VBOs, so unlike the immediate-mode
     * handles (which inherit the global model-view) the cached draws must fold
     * in {@link RenderSystem#getModelViewMatrix()} themselves. In the form editor
     * that matrix is identity (the camera lives in the stack), but in the film
     * editor it carries the world camera, so omitting it left the rings adrift. */
    private static Matrix4f modelView(MatrixStack stack)
    {
        return new Matrix4f(RenderSystem.getModelViewMatrix()).mul(stack.peek().getPositionMatrix());
    }

    private void drawCachedRing(MatrixStack stack, Axis axis, int color)
    {
        float alpha = Colors.getA(color);

        if (alpha <= 0F)
        {
            alpha = 1F;
        }

        this.drawCachedRing(stack, axis, Colors.getR(color), Colors.getG(color), Colors.getB(color), alpha);
    }

    private void drawCachedRing(MatrixStack stack, Axis axis, float r, float g, float b, float a)
    {
        /* TODO(1.21.11 render): cached ring VBO draw disabled (VertexBuffer.draw / setShaderColor /
         * getProjectionMatrix removed in the 1.21.5 GPU rewrite). The axis orientation math is kept for
         * reference; re-route the draw through the new pipeline. */
    }

    private void drawCachedRingBillboard(MatrixStack stack, float r, float g, float b, float a)
    {
        /* TODO(1.21.11 render): cached billboard ring VBO draw disabled (VertexBuffer.draw /
         * setShaderColor / getProjectionMatrix removed in the 1.21.5 GPU rewrite). Re-route through the
         * new pipeline. */
    }

    private void drawRotatePie(MatrixStack stack, Axis axis)
    {
        if (this.currentTransform == null || this.currentTransform.getDrag() == null) return;

        /* TODO(1.21.11 render): rotate-pie immediate-mode fill+outline disabled. It used
         * Tessellator/BufferBuilder + RenderSystem.setShader / enableBlend / defaultBlendFunc /
         * depthFunc / disableCull / enableCull / disableBlend + BufferRenderer.drawWithGlobalProgram +
         * GameRenderer.getPositionColorProgram, all removed in the 1.21.5 GPU rewrite. Re-implement the
         * pie sweep (segments from startDeg over the accumulated rotation, plus start/end outline lines)
         * once the POSITION_COLOR pipeline lands. */
    }

    /**
     * Factor the move/scale handles shrink by so they nest inside the rotation
     * rings in combined mode. With "hide rotation rings" on there is nothing to
     * nest inside, so they keep their full (larger) size.
     */
    private float combinedInnerScale()
    {
        return this.mode == Mode.COMBINED && !BBSSettings.rotateHideRings.get() ? COMBINED_INNER_SCALE : 1F;
    }

    private void drawRotateHandles(MatrixStack stack, boolean editing, int activeOp)
    {
        this.updateVbos();

        boolean rotating = editing && activeOp == Op.ROTATE.modeOrdinal;
        Axis activeAxis = rotating ? this.currentTransform.getAxis() : null;
        boolean trackball = rotating && this.currentTransform.isTrackball();
        boolean viewActive = rotating && this.currentTransform.isViewRotate();

        /* The sphere is translucent and drawn before the bars/cubes, so in
         * combined it sits as a faint tint behind the move/scale handles. */
        if (this.hasSphere() && BBSSettings.rotate3dSphere.get() && (!rotating || trackball))
        {
            int color = this.sphereHovered
                ? BBSSettings.stencilHighlightColor.get()
                : BBSSettings.rotate3dSphereColor.get();

            /* TODO(1.21.11 render): RenderSystem.enableBlend/defaultBlendFunc/disableBlend removed
             * (1.21.5); blend state belongs to the RenderPipeline now. */
            this.drawCachedSphere(stack, Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
        }

        /* TODO(1.21.11 render): RenderSystem.depthFunc removed (1.21.5); depth state belongs to the
         * RenderPipeline now. */
        if (!BBSSettings.rotateHideRings.get()) {
            if (!rotating || activeAxis == Axis.Z) this.drawCachedRing(stack, Axis.Z, Colors.BLUE);
            if (!rotating || activeAxis == Axis.X) this.drawCachedRing(stack, Axis.X, Colors.RED);
            if (!rotating || activeAxis == Axis.Y) this.drawCachedRing(stack, Axis.Y, Colors.GREEN);
        }

        /* The screen-space (billboard) view-rotation ring is intentionally excluded from the
         * "Hide rotation rings" option, so it is always drawn regardless of that setting. */
        if (!rotating || viewActive)
        {
            int color = Colors.LIGHTEST_GRAY;

            this.drawCachedRingBillboard(stack, Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
        }

        if (rotating && activeAxis != null)
        {
            this.drawRotatePie(stack, activeAxis);
        }
    }

    private void drawAxes(MatrixStack stack, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        boolean editing = this.currentTransform != null && this.currentTransform.isEditing();
        int activeOp = editing ? this.currentTransform.getMode() : -1;

        boolean showMove = this.mode.shows(Op.MOVE) && (!editing || activeOp == Op.MOVE.modeOrdinal);
        boolean showScale = this.mode.shows(Op.SCALE) && (!editing || activeOp == Op.SCALE.modeOrdinal);
        boolean showRotate = this.mode.shows(Op.ROTATE) && (!editing || activeOp == Op.ROTATE.modeOrdinal);

        axisSize *= scale * this.combinedInnerScale();
        axisOffset *= scale * thickness;

        if (showRotate)
        {
            this.drawRotateHandles(stack, editing, activeOp);
        }

        /* TODO(1.21.11 render): move/scale handle immediate-mode geometry disabled. It built a
         * POSITION_COLOR BufferBuilder (axis bars, screen cube, plane quads, scale end cubes, centre
         * cube) and drew it via RenderSystem.setShader + GameRenderer.getPositionColorProgram +
         * RenderSystem.depthFunc + BufferRenderer.drawWithGlobalProgram — all removed in the 1.21.5 GPU
         * rewrite. Re-emit these Draw.fillBox shapes through the new POSITION_COLOR pipeline once the
         * foundation lands. */
    }

    public void renderStencil(MatrixStack stack, StencilMap map)
    {
        if (BBSRendering.isIrisShadowPass())
        {
            return;
        }

        if (!BBSSettings.gizmos.get())
        {
            return;
        }

        stack.push();
        MatrixStackUtils.scaleBack(stack);
        this.captureRenderMatrix(stack);

        float distanceScale = this.getAxesDistanceScale(stack);

        stack.push();
        stack.scale(distanceScale, distanceScale, distanceScale);
        this.drawAxes(stack, map, 0.25F, 0.025F);
        stack.pop();
        stack.pop();
    }

    private void captureRenderMatrix(MatrixStack stack)
    {
        /* The drag/pick math (computeWorldOrigin, computeWorldAxes, computeScreenCenter)
         * needs the FULL modelview — view * translate(-cam) * gizmoChain. Since 1.21.1 the
         * world render keeps the camera view in RenderSystem's global model-view and hands
         * the stack an identity base, so stack.peek() alone no longer carries the camera
         * rotation. modelView() folds the global model-view back in (and is a no-op in the
         * form editor, where the camera already lives in the stack), giving the same
         * coordinate frame the gizmo is actually drawn in. */
        this.lastRenderMatrix.set(modelView(stack));
        this.hasLastRenderMatrix = true;
    }

    private void drawAxes(MatrixStack stack, StencilMap map, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        boolean editing = this.currentTransform != null && this.currentTransform.isEditing();
        int activeOp = editing ? this.currentTransform.getMode() : -1;

        boolean showMove = this.mode.shows(Op.MOVE) && (!editing || activeOp == Op.MOVE.modeOrdinal);
        boolean showScale = this.mode.shows(Op.SCALE) && (!editing || activeOp == Op.SCALE.modeOrdinal);
        boolean showRotate = this.mode.shows(Op.ROTATE) && (!editing || activeOp == Op.ROTATE.modeOrdinal);

        axisSize *= scale * this.combinedInnerScale();
        axisOffset *= scale * thickness;

        /* TODO(1.21.11 render): RenderSystem.disableDepthTest/enableDepthTest removed (1.21.5); depth
         * state belongs to the RenderPipeline now. */

        if (showRotate)
        {
            this.updateVbos();

            boolean rotating = editing && activeOp == Op.ROTATE.modeOrdinal;
            Axis activeAxis = rotating ? this.currentTransform.getAxis() : null;
            boolean viewActive = rotating && this.currentTransform.isViewRotate();

            if (!BBSSettings.rotateHideRings.get()) {
                if (!rotating || activeAxis == Axis.Z) this.drawCachedRing(stack, Axis.Z, STENCIL_ROTATE_Z / 255F, 0F, 0F, 1F);
                if (!rotating || activeAxis == Axis.X) this.drawCachedRing(stack, Axis.X, STENCIL_ROTATE_X / 255F, 0F, 0F, 1F);
                if (!rotating || activeAxis == Axis.Y) this.drawCachedRing(stack, Axis.Y, STENCIL_ROTATE_Y / 255F, 0F, 0F, 1F);
            }

            /* View ring stays pickable even when the rings are hidden (see drawAxes visual pass). */
            if (!rotating || viewActive) this.drawCachedRingBillboard(stack, STENCIL_VIEW / 255F, 0F, 0F, 1F);
        }

        if (showMove || showScale)
        {
            /* TODO(1.21.11 render): move/scale stencil-id geometry disabled. It built a POSITION_COLOR
             * BufferBuilder encoding each handle's stencil id in the red channel (bars, screen cube,
             * plane quads, scale end cubes) and drew it via RenderSystem.setShader +
             * GameRenderer.getPositionColorProgram + BufferRenderer.drawWithGlobalProgram — all removed
             * in the 1.21.5 GPU rewrite. The stencil-pick path (StencilMap) depends on this, so picking
             * gizmo handles will not work until this is re-emitted through the new pipeline. */
        }
    }

    public static enum Mode
    {
        TRANSLATE, SCALE, ROTATE, COMBINED;

        public boolean shows(Op op)
        {
            switch (this)
            {
                case TRANSLATE:
                    return op == Op.MOVE || op == Op.SCREEN;
                case SCALE:
                    return op == Op.SCALE;
                case ROTATE:
                    return op == Op.ROTATE || op == Op.VIEW || op == Op.TRACKBALL;
                case COMBINED:
                    return op == Op.MOVE || op == Op.SCALE || op == Op.ROTATE || op == Op.VIEW || op == Op.SCREEN;
                default:
                    return false;
            }
        }
    }

    /**
     * Kind of transform a handle drives. {@link #modeOrdinal} matches the
     * {@code mode} argument {@link UIPropTransform#enableMode(int, Axis, Axis, GizmoDrag)}
     * expects (0 translate, 1 scale, 2 rotate); VIEW and TRACKBALL are rotate
     * variants routed through their own enable* calls.
     */
    public static enum Op
    {
        MOVE(0), SCALE(1), ROTATE(2), VIEW(2), TRACKBALL(2), SCREEN(0);

        public final int modeOrdinal;

        Op(int modeOrdinal)
        {
            this.modeOrdinal = modeOrdinal;
        }
    }

    /**
     * A single pickable handle: its stencil id plus the operation and axes it
     * stands for. {@link #start} resolves a picked stencil id straight to one
     * of these and dispatches the matching transform — no dependence on the
     * active display {@link Mode}.
     */
    public static enum Handle
    {
        MOVE_X(STENCIL_X, Op.MOVE, Axis.X, null),
        MOVE_Y(STENCIL_Y, Op.MOVE, Axis.Y, null),
        MOVE_Z(STENCIL_Z, Op.MOVE, Axis.Z, null),
        MOVE_XZ(STENCIL_XZ, Op.MOVE, Axis.X, Axis.Z),
        MOVE_XY(STENCIL_XY, Op.MOVE, Axis.X, Axis.Y),
        MOVE_ZY(STENCIL_ZY, Op.MOVE, Axis.Z, Axis.Y),
        SCALE_X(STENCIL_SCALE_X, Op.SCALE, Axis.X, null),
        SCALE_Y(STENCIL_SCALE_Y, Op.SCALE, Axis.Y, null),
        SCALE_Z(STENCIL_SCALE_Z, Op.SCALE, Axis.Z, null),
        SCALE_XZ(STENCIL_SCALE_XZ, Op.SCALE, Axis.X, Axis.Z),
        SCALE_XY(STENCIL_SCALE_XY, Op.SCALE, Axis.X, Axis.Y),
        SCALE_ZY(STENCIL_SCALE_ZY, Op.SCALE, Axis.Z, Axis.Y),
        ROTATE_X(STENCIL_ROTATE_X, Op.ROTATE, Axis.X, null),
        ROTATE_Y(STENCIL_ROTATE_Y, Op.ROTATE, Axis.Y, null),
        ROTATE_Z(STENCIL_ROTATE_Z, Op.ROTATE, Axis.Z, null),
        TRACKBALL(STENCIL_TRACKBALL, Op.TRACKBALL, null, null),
        VIEW(STENCIL_VIEW, Op.VIEW, null, null),
        SCREEN(STENCIL_SCREEN, Op.SCREEN, null, null);

        public final int index;
        public final Op op;
        public final Axis axis;
        public final Axis axis2;

        Handle(int index, Op op, Axis axis, Axis axis2)
        {
            this.index = index;
            this.op = op;
            this.axis = axis;
            this.axis2 = axis2;
        }

        public static Handle byIndex(int index)
        {
            for (Handle handle : values())
            {
                if (handle.index == index)
                {
                    return handle;
                }
            }

            return null;
        }
    }
}
