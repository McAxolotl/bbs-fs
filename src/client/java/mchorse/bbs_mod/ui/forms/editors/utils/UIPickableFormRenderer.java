package mchorse.bbs_mod.ui.forms.editors.utils;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.graphics.Draw;
import mchorse.bbs_mod.graphics.Framebuffer;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.forms.editors.UIFormEditor;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.ui.utils.Gizmo;
import mchorse.bbs_mod.ui.utils.GizmoInteraction;
import mchorse.bbs_mod.ui.utils.GizmoViewport;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.StencilFormFramebuffer;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

import java.util.function.Supplier;

public class UIPickableFormRenderer extends UIFormRenderer implements GizmoViewport
{
    public UIFormEditor formEditor;

    private boolean update;

    private StencilFormFramebuffer stencil = new StencilFormFramebuffer();
    private StencilMap stencilMap = new StencilMap();

    private final GizmoInteraction gizmo = new GizmoInteraction(this);

    private IEntity target;
    private Supplier<Boolean> renderForm;

    public UIPickableFormRenderer(UIFormEditor formEditor)
    {
        this.formEditor = formEditor;
    }

    public void updatable()
    {
        this.update = true;
    }

    public StencilFormFramebuffer getStencil()
    {
        return this.stencil;
    }

    public void setRenderForm(Supplier<Boolean> renderForm)
    {
        this.renderForm = renderForm;
    }

    public IEntity getTargetEntity()
    {
        return this.target == null ? this.entity : this.target;
    }

    public void setTarget(IEntity target)
    {
        this.target = target;
    }

    private void ensureFramebuffer()
    {
        this.stencil.setup(Link.bbs("stencil_form"));
        this.stencil.resizeGUI(this.area.w, this.area.h);
    }

    @Override
    public void resize()
    {
        super.resize();

        this.ensureFramebuffer();
    }

    @Override
    public boolean subMouseClicked(UIContext context)
    {
        if (this.formEditor.clickViewport(context, this.stencil))
        {
            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public boolean subMouseReleased(UIContext context)
    {
        if (this.gizmo.mouseReleased(context))
        {
            return true;
        }

        return super.subMouseReleased(context);
    }

    public GizmoInteraction getGizmoInteraction()
    {
        return this.gizmo;
    }

    @Override
    public StencilFormFramebuffer getGizmoStencil()
    {
        return this.stencil;
    }

    @Override
    public Matrix4f getGizmoProjection()
    {
        return this.camera.projection;
    }

    @Override
    public Area getGizmoArea()
    {
        return this.area;
    }

    @Override
    public boolean startGizmo(UIContext context, int stencilIndex)
    {
        return this.formEditor.startGizmo(context, stencilIndex);
    }

    @Override
    public void pickGizmoForm(UIContext context, Form form, String bone)
    {
        this.formEditor.pickFormFromRenderer(new Pair<>(form, bone));
    }

    @Override
    protected void renderUserModel(UIContext context)
    {
        if (this.form == null)
        {
            return;
        }

        this.formEditor.preFormRender(context, this.form);

        /* TODO(1.21.11 render): getMatrices() is now a 2D Matrix3x2fStack; the 3D form
         * model-view comes from this.camera/this.world inside the context, so feed a fresh
         * identity MatrixStack here (matches the render-foundation bridge). */
        FormRenderingContext formContext = new FormRenderingContext()
            .set(FormRenderType.PREVIEW, this.target == null ? this.entity : this.target, new MatrixStack(), LightmapTextureManager.pack(15, 15), OverlayTexture.DEFAULT_UV, context.getTransition())
            .camera(this.camera)
            .modelRenderer();

        if (this.renderForm == null || this.renderForm.get())
        {
            FormUtilsClient.render(this.form, formContext);

            if (this.form.hitbox.get())
            {
                this.renderFormHitbox(context);
            }
        }

        this.renderAxes(context);

        if (this.area.isInside(context))
        {
            /* TODO(1.21.11 render): GlStateManager._disable/_enableScissorTest removed;
             * scissor now goes through RenderSystem.enableScissorForRenderTypeDraws. */
            this.stencilMap.setup();
            this.stencil.apply();

            FormUtilsClient.render(this.form, formContext.stencilMap(this.stencilMap));

            Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
            MatrixStack stack = new MatrixStack();

            stack.push();

            if (matrix != null)
            {
                MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
            }

            Gizmo.INSTANCE.renderStencil(stack, this.stencilMap);

            stack.pop();

            this.stencil.pickGUI(context, this.area);
            this.stencil.unbind(this.stencilMap);

            /* TODO(1.21.11 render): Framebuffer.beginWrite(boolean) removed; rebinding the
             * main framebuffer for writing now goes through the GpuTexture/command-queue API. */
        }
        else
        {
            this.stencil.clearPicking();
        }

        this.gizmo.update(context);
    }

    private void renderAxes(UIContext context)
    {
        Matrix4f matrix = this.formEditor.getOrigin(context.getTransition());
        /* TODO(1.21.11 render): getMatrices() is now 2D; axes/gizmo render in 3D, so build
         * a MatrixStack from the origin instead (the depth state is encoded by the layer). */
        MatrixStack stack = new MatrixStack();

        stack.push();

        if (matrix != null)
        {
            MatrixStackUtils.multiply(stack, MatrixStackUtils.stripScale(matrix));
        }

        /* Draw axes */
        if (UIBaseMenu.renderAxes)
        {
            /* TODO(1.21.11 render): RenderSystem.disable/enableDepthTest removed; depth state
             * is now part of the RenderPipeline used by the gizmo render layer. */
            Gizmo.INSTANCE.render(stack);
        }

        stack.pop();
    }

    private void renderFormHitbox(UIContext context)
    {
        float hitboxW = this.form.hitboxWidth.get();
        float hitboxH = this.form.hitboxHeight.get();
        float eyeHeight = hitboxH * this.form.hitboxEyeHeight.get();

        /* TODO(1.21.11 render): getMatrices() is now a 2D Matrix3x2fStack; hitbox boxes render
         * in 3D world space, so draw against a fresh MatrixStack at the model origin. */
        MatrixStack stack = new MatrixStack();

        /* Draw look vector */
        final float thickness = 0.01F;
        Draw.renderBox(stack, -thickness, -thickness + eyeHeight, -thickness, thickness, thickness, 2F, 1F, 0F, 0F);

        /* Draw hitbox */
        Draw.renderBox(stack, -hitboxW / 2, 0, -hitboxW / 2, hitboxW, hitboxH, hitboxW);
    }

    @Override
    protected void update()
    {
        super.update();

        if (this.update && this.form != null)
        {
            this.form.update(this.entity);
        }
    }

    @Override
    public void render(UIContext context)
    {
        super.render(context);

        /* PROBE(1.21.11 render, problem A): clear the panel FBO to solid magenta via explicit
         * glClearBufferfv (does NOT touch global glClearColor -> no GlStateManager desync) and blit
         * it through the proven AdoptedTexture path (same as icons). This isolates the two-phase-GUI
         * compositing mechanism from the still-dead 3D render: if a magenta square fills the preview
         * panel, problem A is solved and only the real 3D draw (problem B) remains. Remove once real. */
        Framebuffer probeFb = this.stencil.getFramebuffer();

        if (context.getTick() % 30L == 0L)
        {
            Texture mt = probeFb == null ? null : probeFb.getMainTexture();
            System.out.println("[BBS probe] pickable.render area=" + this.area.x + "," + this.area.y + " " + this.area.w + "x" + this.area.h
                + " fb=" + (probeFb == null ? "null" : ("fbId" + probeFb.id + " tex" + (mt == null ? "null" : (mt.id + " " + mt.width + "x" + mt.height))))
                + " picked=" + this.stencil.hasPicked());
        }

        if (probeFb != null)
        {
            probeFb.bind();

            /* The ambient GUI color-write mask has ALPHA DISABLED (GUI does not write framebuffer alpha),
             * so the clear's alpha (and a future model render's alpha) is dropped -> FBO alpha stays 0 ->
             * the GUI_TEXTURED blit multiplies by texel alpha 0 and draws fully transparent (invisible).
             * Force all 4 channels writable for the clear, then restore the previous mask. Raw GL save/
             * restore is safe: at exit GL == previous == GlStateManager cache, so no tracker desync. */
            byte[] prevMask = new byte[4];

            try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush())
            {
                java.nio.ByteBuffer mask = stack.malloc(4);
                org.lwjgl.opengl.GL11.glGetBooleanv(org.lwjgl.opengl.GL11.GL_COLOR_WRITEMASK, mask);
                prevMask[0] = mask.get(0);
                prevMask[1] = mask.get(1);
                prevMask[2] = mask.get(2);
                prevMask[3] = mask.get(3);
            }

            org.lwjgl.opengl.GL11.glColorMask(true, true, true, true);
            org.lwjgl.opengl.GL30.glClearBufferfv(org.lwjgl.opengl.GL30.GL_COLOR, 0, new float[]{1F, 0F, 1F, 1F});

            /* DIAGNOSTIC: read back 1px after the clear to confirm alpha now writes (expect 1,0,1,1). */
            if (context.getTick() % 30L == 0L)
            {
                try (org.lwjgl.system.MemoryStack stack = org.lwjgl.system.MemoryStack.stackPush())
                {
                    java.nio.FloatBuffer px = stack.mallocFloat(4);
                    org.lwjgl.opengl.GL11.glReadPixels(10, 10, 1, 1, org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_FLOAT, px);
                    System.out.println("[BBS probe] FBO readback after clear @(10,10)=" + px.get(0) + "," + px.get(1) + "," + px.get(2) + "," + px.get(3)
                        + " prevMask=" + prevMask[0] + prevMask[1] + prevMask[2] + prevMask[3]);
                }
            }

            org.lwjgl.opengl.GL11.glColorMask(prevMask[0] != 0, prevMask[1] != 0, prevMask[2] != 0, prevMask[3] != 0);

            probeFb.unbind();

            Texture probe = probeFb.getMainTexture();
            int pw = probe.width;
            int ph = probe.height;

            context.batcher.texturedBox(probe, Colors.WHITE, this.area.x + 8, this.area.y + 8, this.area.w - 16, this.area.h - 16, 0, ph, pw, 0, pw, ph);
        }

        if (!this.stencil.hasPicked())
        {
            return;
        }

        int index = this.stencil.getIndex();
        Texture texture = this.stencil.getFramebuffer().getMainTexture();
        Pair<Form, String> pair = this.stencil.getPicked();
        int w = texture.width;
        int h = texture.height;

        /* TODO(1.21.11 render): the picker-preview highlight overlay depends on the new
         * uniform-upload path. RenderPipeline.getUniform("Target"/"HighlightColor")/GlUniform.set
         * are gone (uniforms are UBO/DynamicUniform entries now), and Batcher2D.texturedBox is a
         * no-op stub. Re-enable once the picker-preview pipeline + uniform upload are ported.
         * Original intent: set Target=index, HighlightColor=stencilHighlightColor, then draw the
         * stencil texture through the picker-preview program over the viewport. */
        int color = BBSSettings.stencilHighlightColor.get();

        context.batcher.texturedBox(BBSShaders::getPickerPreviewProgram, texture.id, Colors.WHITE, this.area.x, this.area.y, this.area.w, this.area.h, 0, h, w, 0, w, h);

        if (pair != null && pair.a != null)
        {
            String label = pair.a.getFormIdOrName();

            if (!pair.b.isEmpty())
            {
                label += " - " + pair.b;
            }

            context.batcher.textCard(label, context.mouseX + 12, context.mouseY + 8);
        }
    }

    @Override
    protected void renderGrid(UIContext context)
    {
        if (this.renderForm == null || this.renderForm.get())
        {
            super.renderGrid(context);
        }
    }
}
