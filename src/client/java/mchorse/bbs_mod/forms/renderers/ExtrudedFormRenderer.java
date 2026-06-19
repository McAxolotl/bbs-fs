package mchorse.bbs_mod.forms.renderers;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.BBSShaders;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.forms.forms.ExtrudedForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.function.Supplier;

public class ExtrudedFormRenderer extends FormRenderer<ExtrudedForm>
{
    public ExtrudedFormRenderer(ExtrudedForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* TODO(1.21.11 render): context.batcher.getContext().getMatrices() now returns a 2D
         * Matrix3x2fStack; the extruded model draw needs a 3D MatrixStack. Build a fresh 3D stack
         * from the UI matrix so the model-building math below stays intact (the actual draw is a
         * stub until the model RenderPipeline path is wired). */
        MatrixStack stack = new MatrixStack();

        stack.push();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        this.applyTransforms(uiMatrix, context.getTransition());
        MatrixStackUtils.multiply(stack, uiMatrix);
        stack.translate(0F, 1F, 0F);
        stack.scale(1.5F, 1.5F, 4F);
        stack.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        /* Shading fix */
        stack.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        stack.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        /* Was: RenderSystem.depthFunc(GL_LEQUAL) ... depthFunc(GL_ALWAYS). Depth test is now
         * per-pipeline (the model pipeline declares LEQUAL_DEPTH_TEST). */
        this.renderModel(BBSShaders::getModel,
            stack,
            OverlayTexture.DEFAULT_UV, LightmapTextureManager.MAX_LIGHT_COORDINATE, Colors.WHITE,
            context.getTransition()
        );

        stack.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        boolean shading = this.form.shading.get();

        if (BBSRendering.isIrisShadersEnabled())
        {
            shading = true;
        }

        /* TODO(1.21.11 render): ShaderProgram and GameRenderer::getRenderTypeEntityTranslucentProgram
         * /getPositionTexColorProgram are removed; the old getShader(...) picking path (which set the
         * Target GlUniform via setupTarget) is gone. For now always select the BBS model
         * RenderPipeline; the picker pipeline (BBSShaders.getPickerBillboard[NoShading]Program) must
         * be selected + its Target UBO uniform supplied once the picking foundation lands. */
        Supplier<RenderPipeline> shader = BBSShaders::getModel;

        this.renderModel(shader, context.stack, context.overlay, context.light, context.color, context.getTransition());
    }

    private void renderModel(Supplier<RenderPipeline> shader, MatrixStack matrices, int overlay, int light, int overlayColor, float transition)
    {
        Link texture = this.form.texture.get();
        ModelVAO data = BBSModClient.getTextures().getExtruder().get(texture);

        if (data != null)
        {
            if (this.form.billboard.get())
            {
                Matrix4f modelMatrix = matrices.peek().getPositionMatrix();
                Vector3f scale = Vectors.TEMP_3F;

                modelMatrix.getScale(scale);

                modelMatrix.m00(1).m01(0).m02(0);
                modelMatrix.m10(0).m11(1).m12(0);
                modelMatrix.m20(0).m21(0).m22(1);

                modelMatrix.scale(scale);

                matrices.peek().getNormalMatrix().identity();
            }

            Color color = Colors.COLOR.set(overlayColor, true);
            Color formColor = this.form.color.get();

            FormColorBlend.blend(color, formColor, this.form.additiveColor.get());

            BBSModClient.getTextures().bindTexture(texture);

            RenderPipeline finalShader = shader.get();

            /* TODO(1.21.11 render): the extruded model draw is stubbed.
             * Removed/changed since 1.21.5:
             *  - RenderSystem.enableBlend/defaultBlendFunc/disableBlend (blend is per-pipeline now);
             *  - LightmapTextureManager.enable()/disable() and OverlayTexture.setup/teardownOverlayColor()
             *    (lightmap/overlay are bound via the RenderLayer/RenderSetup useLightmap()/useOverlay());
             *  - ModelVAORenderer.render(...) still takes a ShaderProgram and binds it via the removed
             *    ShaderProgram.bind()/uniform fields. It must instead bind the model RenderPipeline
             *    ({@code finalShader}) through RenderSystem.getDevice()/RenderPass and upload the
             *    ColorModulator/Light/IViewRotMat/NormalMat UBO entries per draw.
             * Re-enable the draw once ModelVAORenderer is migrated to RenderPipeline. */
        }
    }
}
