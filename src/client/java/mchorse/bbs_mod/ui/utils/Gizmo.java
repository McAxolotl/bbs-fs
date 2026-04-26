package mchorse.bbs_mod.ui.utils;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.MathUtils;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gl.VertexBuffer;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

public class Gizmo
{
    public final static int STENCIL_X = 1;
    public final static int STENCIL_Y = 2;
    public final static int STENCIL_Z = 3;
    public final static int STENCIL_XZ = 4;
    public final static int STENCIL_XY = 5;
    public final static int STENCIL_ZY = 6;
    public final static int STENCIL_XYZ = 7;

    public final static Gizmo INSTANCE = new Gizmo();

    private Mode mode = Mode.TRANSLATE;

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

    /* VBO caching for rotation rings to save resources */
    private VertexBuffer rotateRingVbo;
    private VertexBuffer rotateStencilRingVbo;
    private VertexBuffer rotateSphereVbo;
    private VertexBuffer rotateStencilSphereVbo;
    private float lastScale = -1F;
    private float lastThickness = -1F;

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

        if (index >= STENCIL_X && index <= STENCIL_XYZ)
        {
            this.index = index;
            this.mouseX = mouseX;
            this.mouseY = mouseY;

            this.currentTransform = transform;

            if (transform != null)
            {
                if (this.index == STENCIL_X) transform.enableMode(this.mode.ordinal(), Axis.X, null, drag);
                else if (this.index == STENCIL_Y) transform.enableMode(this.mode.ordinal(), Axis.Y, null, drag);
                else if (this.index == STENCIL_Z) transform.enableMode(this.mode.ordinal(), Axis.Z, null, drag);
                else if ((this.mode == Mode.TRANSLATE || this.mode == Mode.SCALE) && this.index == STENCIL_XZ) transform.enableMode(this.mode.ordinal(), Axis.X, Axis.Z, drag);
                else if ((this.mode == Mode.TRANSLATE || this.mode == Mode.SCALE) && this.index == STENCIL_XY) transform.enableMode(this.mode.ordinal(), Axis.X, Axis.Y, drag);
                else if ((this.mode == Mode.TRANSLATE || this.mode == Mode.SCALE) && this.index == STENCIL_ZY) transform.enableMode(this.mode.ordinal(), Axis.Z, Axis.Y, drag);
                else if (this.mode == Mode.ROTATE && this.index == STENCIL_XYZ) transform.enableTrackball(drag);
            }

            return true;
        }

        return false;
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

            if (this.index < STENCIL_X || this.index > STENCIL_XYZ)
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
        this.lastRenderMatrix.set(stack.peek().getPositionMatrix());
        this.hasLastRenderMatrix = true;

        if (BBSSettings.gizmos.get())
        {
            this.drawAxes(stack, 0.25F, 0.008F);
            this.drawInfiniteLine(stack);
        }
        else
        {
            Draw.coolerAxes(stack, 0.25F, 0.008F);
            this.drawInfiniteLine(stack);
        }
    }

    private void drawInfiniteLine(MatrixStack stack)
    {
        int debugIndex = this.index;

        if ((debugIndex < STENCIL_X || debugIndex > STENCIL_ZY) && this.currentTransform != null)
        {
            debugIndex = this.currentTransform.getDebugLineStencilIndex();
        }

        if (debugIndex < STENCIL_X || debugIndex > STENCIL_ZY || debugIndex == STENCIL_XYZ)
        {
            return;
        }

        BufferBuilder builder = Tessellator.getInstance().getBuffer();
        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);

        float size = 10000F;
        float t = 0.005F;

        if (debugIndex == STENCIL_X || debugIndex == STENCIL_XZ || debugIndex == STENCIL_XY)
        {
            Draw.fillBox(builder, stack, -size, -t, -t, size, t, t, Colors.RED);
        }
        
        if (debugIndex == STENCIL_Y || debugIndex == STENCIL_XY || debugIndex == STENCIL_ZY)
        {
            Draw.fillBox(builder, stack, -t, -size, -t, t, size, t, Colors.GREEN);
        }
        
        if (debugIndex == STENCIL_Z || debugIndex == STENCIL_XZ || debugIndex == STENCIL_ZY)
        {
            Draw.fillBox(builder, stack, -t, -t, -size, t, t, size, Colors.BLUE);
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);
        BufferRenderer.drawWithGlobalProgram(builder.end());
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    private void updateVbos()
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        if (this.rotateRingVbo == null || scale != this.lastScale || thickness != this.lastThickness)
        {
            if (this.rotateRingVbo != null)
            {
                this.rotateRingVbo.close();
                this.rotateStencilRingVbo.close();
                this.rotateSphereVbo.close();
                this.rotateStencilSphereVbo.close();
            }

            this.rotateRingVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            this.rotateStencilRingVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            this.rotateSphereVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            this.rotateStencilSphereVbo = new VertexBuffer(VertexBuffer.Usage.STATIC);

            BufferBuilder builder = Tessellator.getInstance().getBuffer();

            float radius = 0.22F * scale;
            float thicknessRing = 0.015F * scale * thickness;
            float outlinePad = 0.015F * scale * thickness;
            float thicknessStencil = 0.025F * scale * thickness + outlinePad;

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.arc3D(builder, new MatrixStack(), Axis.Y, radius, thicknessRing, 1F, 1F, 1F, 0F, 360F);
            this.rotateRingVbo.bind();
            this.rotateRingVbo.upload(builder.end());

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.arc3D(builder, new MatrixStack(), Axis.Y, radius, thicknessStencil, 1F, 1F, 1F, 0F, 360F);
            this.rotateStencilRingVbo.bind();
            this.rotateStencilRingVbo.upload(builder.end());

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.sphere(builder, new MatrixStack(), radius, 24, 24, 1F, 1F, 1F, 1F);
            this.rotateSphereVbo.bind();
            this.rotateSphereVbo.upload(builder.end());

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.sphere(builder, new MatrixStack(), radius, 24, 24, 1F, 1F, 1F, 1F);
            this.rotateStencilSphereVbo.bind();
            this.rotateStencilSphereVbo.upload(builder.end());

            VertexBuffer.unbind();

            this.lastScale = scale;
            this.lastThickness = thickness;
        }
    }

    private void drawCachedSphere(MatrixStack stack, VertexBuffer vbo, float r, float g, float b, float a)
    {
        RenderSystem.setShaderColor(r, g, b, a);
        vbo.bind();
        vbo.draw(stack.peek().getPositionMatrix(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    private void drawCachedRing(MatrixStack stack, VertexBuffer vbo, Axis axis, int color)
    {
        this.drawCachedRing(stack, vbo, axis, Colors.getR(color), Colors.getG(color), Colors.getB(color), Colors.getA(color));
    }

    private void drawCachedRing(MatrixStack stack, VertexBuffer vbo, Axis axis, float r, float g, float b, float a)
    {
        stack.push();
        
        if (axis == Axis.X) stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotation(MathUtils.PI / 2F));
        if (axis == Axis.Z) stack.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_X.rotation(MathUtils.PI / 2F));

        RenderSystem.setShaderColor(r, g, b, a);
        vbo.bind();
        vbo.draw(stack.peek().getPositionMatrix(), RenderSystem.getProjectionMatrix(), GameRenderer.getPositionColorProgram());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        
        stack.pop();
    }

    private void drawAxes(MatrixStack stack, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        axisSize *= scale;
        axisOffset *= scale * thickness;

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        if (this.mode == Mode.ROTATE)
        {
            this.updateVbos();

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            this.drawCachedSphere(stack, this.rotateSphereVbo, 1F, 1F, 1F, 0.15F);
            RenderSystem.disableBlend();

            RenderSystem.depthFunc(GL11.GL_ALWAYS);
            this.drawCachedRing(stack, this.rotateRingVbo, Axis.Z, Colors.BLUE);
            this.drawCachedRing(stack, this.rotateRingVbo, Axis.X, Colors.RED);
            this.drawCachedRing(stack, this.rotateRingVbo, Axis.Y, Colors.GREEN);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);

            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, Colors.WHITE);
        }
        else
        {
            builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
            Draw.fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, Colors.RED);
            Draw.fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, Colors.GREEN);
            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, Colors.BLUE);
            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, Colors.WHITE);

            if (this.mode == Mode.TRANSLATE || this.mode == Mode.SCALE)
            {
                float planeStart = axisSize * 0.2F;
                float planeEnd = axisSize * 0.6F;
                float planeThickness = axisOffset * 0.5F;

                Draw.fillBox(builder, stack, planeStart, -planeThickness, planeStart, planeEnd, planeThickness, planeEnd, Colors.PLANE_XZ);
                Draw.fillBox(builder, stack, planeStart, planeStart, -planeThickness, planeEnd, planeEnd, planeThickness, Colors.PLANE_XY);
                Draw.fillBox(builder, stack, -planeThickness, planeStart, planeStart, planeThickness, planeEnd, planeEnd, Colors.PLANE_ZY);
            }

            if (this.mode == Mode.SCALE)
            {
                float scaleEnd = axisSize + axisOffset * 2F;

                Draw.fillBox(builder, stack, axisSize, -axisOffset * 2F, -axisOffset * 2F, scaleEnd, axisOffset * 2F, axisOffset * 2F, Colors.RED);
                Draw.fillBox(builder, stack, -axisOffset * 2F, axisSize, -axisOffset * 2F, axisOffset * 2F, scaleEnd, axisOffset * 2F, Colors.GREEN);
                Draw.fillBox(builder, stack, -axisOffset * 2F, -axisOffset * 2F, axisSize, axisOffset * 2F, axisOffset * 2F, scaleEnd, Colors.BLUE);
            }
        }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.depthFunc(GL11.GL_ALWAYS);

        BufferRenderer.drawWithGlobalProgram(builder.end());

        RenderSystem.depthFunc(GL11.GL_LEQUAL);
    }

    public void renderStencil(MatrixStack stack, StencilMap map)
    {
        if (BBSSettings.gizmos.get())
        {
            this.drawAxes(stack, map, 0.25F, 0.015F);
        }
    }

    private void drawAxes(MatrixStack stack, StencilMap map, float axisSize, float axisOffset)
    {
        float scale = BBSSettings.axesScale.get();
        float thickness = BBSSettings.axesThickness.get();

        axisSize *= scale;
        axisOffset *= scale * thickness;

        BufferBuilder builder = Tessellator.getInstance().getBuffer();

        RenderSystem.disableDepthTest();

        if (this.mode == Mode.ROTATE)
        {
            this.updateVbos();

            this.drawCachedSphere(stack, this.rotateStencilSphereVbo, STENCIL_XYZ / 255F, 0F, 0F, 1F);

            this.drawCachedRing(stack, this.rotateStencilRingVbo, Axis.Z, STENCIL_Z / 255F, 0F, 0F, 1F);
            this.drawCachedRing(stack, this.rotateStencilRingVbo, Axis.X, STENCIL_X / 255F, 0F, 0F, 1F);
            this.drawCachedRing(stack, this.rotateStencilRingVbo, Axis.Y, STENCIL_Y / 255F, 0F, 0F, 1F);
            
            RenderSystem.enableDepthTest();
            return;
        }

        builder.begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR);
        
        Draw.fillBox(builder, stack, 0, -axisOffset, -axisOffset, axisSize, axisOffset, axisOffset, STENCIL_X / 255F, 0F, 0F);
            Draw.fillBox(builder, stack, -axisOffset, 0, -axisOffset, axisOffset, axisSize, axisOffset, STENCIL_Y / 255F, 0F, 0F);
            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, 0, axisOffset, axisOffset, axisSize, STENCIL_Z / 255F, 0F, 0F);
            Draw.fillBox(builder, stack, -axisOffset, -axisOffset, -axisOffset, axisOffset, axisOffset, axisOffset, 0F, 0F, 0F);

            if (this.mode == Mode.TRANSLATE || this.mode == Mode.SCALE)
            {
                float planeStart = axisSize * 0.2F;
                float planeEnd = axisSize * 0.6F;
                float planeThickness = axisOffset * 0.5F;

                Draw.fillBox(builder, stack, planeStart, -planeThickness, planeStart, planeEnd, planeThickness, planeEnd, STENCIL_XZ / 255F, 0F, 0F);
                Draw.fillBox(builder, stack, planeStart, planeStart, -planeThickness, planeEnd, planeEnd, planeThickness, STENCIL_XY / 255F, 0F, 0F);
                Draw.fillBox(builder, stack, -planeThickness, planeStart, planeStart, planeThickness, planeEnd, planeEnd, STENCIL_ZY / 255F, 0F, 0F);
            }

            if (this.mode == Mode.SCALE)
            {
                float scaleEnd = axisSize + axisOffset;

                Draw.fillBox(builder, stack, axisSize, -axisOffset * 2F, -axisOffset * 2F, scaleEnd, axisOffset * 2F, axisOffset * 2F, STENCIL_X / 255F, 0F, 0F);
                Draw.fillBox(builder, stack, -axisOffset * 2F, axisSize, -axisOffset * 2F, axisOffset * 2F, scaleEnd, axisOffset * 2F, STENCIL_Y / 255F, 0F, 0F);
                Draw.fillBox(builder, stack, -axisOffset * 2F, -axisOffset * 2F, axisSize, axisOffset * 2F, axisOffset * 2F, scaleEnd, STENCIL_Z / 255F, 0F, 0F);
            }

        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        BufferRenderer.drawWithGlobalProgram(builder.end());
        
        RenderSystem.enableDepthTest();
    }

    public static enum Mode
    {
        TRANSLATE, SCALE, ROTATE;
    }
}
