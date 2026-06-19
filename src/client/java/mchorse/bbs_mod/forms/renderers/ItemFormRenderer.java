package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.forms.CustomVertexConsumerProvider;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.ItemForm;
import mchorse.bbs_mod.forms.renderers.utils.FormColorBlend;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.joml.Vectors;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;

public class ItemFormRenderer extends FormRenderer<ItemForm>
{
    public ItemFormRenderer(ItemForm form)
    {
        super(form);
    }

    @Override
    public void renderInUI(UIContext context, int x1, int y1, int x2, int y2)
    {
        /* DrawContext.draw() was removed in the 1.21.5 UI rewrite; the engine flushes immediate draws. */
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        /* DrawContext.getMatrices() now returns a 2D Matrix3x2fStack; item rendering needs a 3D
         * MatrixStack, so build a dedicated one and apply the UI matrix to it. */
        MatrixStack matrices = new MatrixStack();

        Matrix4f uiMatrix = ModelFormRenderer.getUIMatrix(context, x1, y1, x2, y2);

        matrices.push();
        MatrixStackUtils.multiply(matrices, uiMatrix);
        matrices.scale(this.form.uiScale.get(), this.form.uiScale.get(), this.form.uiScale.get());

        matrices.peek().getNormalMatrix().getScale(Vectors.EMPTY_3F);
        matrices.peek().getNormalMatrix().scale(1F / Vectors.EMPTY_3F.x, -1F / Vectors.EMPTY_3F.y, 1F / Vectors.EMPTY_3F.z);

        Color set = Color.white();
        FormColorBlend.blend(set, this.form.color.get(), this.form.additiveColor.get());

        consumers.setSubstitute(BBSRendering.getColorConsumer(set));
        consumers.setUI(true);

        /* TODO(1.21.11 render): the item-model system was rewritten in 1.21.4. The old
         * ItemRenderer.renderItem(ItemStack, ItemDisplayContext, light, overlay, MatrixStack,
         * VertexConsumerProvider, World, seed) overload was removed. Rendering now goes through
         * ItemModelManager.update(ItemRenderState, ...) + ItemRenderState.render(MatrixStack,
         * OrderedRenderCommandQueue, light, overlay, ...), which emits into an
         * OrderedRenderCommandQueue rather than a VertexConsumerProvider. The BBS recolor/picking
         * pipeline relies on intercepting the VertexConsumerProvider (CustomVertexConsumerProvider
         * substitution), so it cannot be wired to the new command-queue path until the render
         * foundation provides an equivalent hook. Neutralized so the form compiles and renders
         * nothing; restore faithful item rendering when the item-model foundation is ported. */

        consumers.draw();
        consumers.setUI(false);
        consumers.setSubstitute(null);

        matrices.pop();
    }

    @Override
    protected void render3D(FormRenderingContext context)
    {
        CustomVertexConsumerProvider consumers = FormUtilsClient.getProvider();
        int light = context.light;

        context.stack.push();

        if (context.isPicking())
        {
            CustomVertexConsumerProvider.hijackVertexFormat((layer) ->
            {
                /* TODO(1.21.11 render): RenderSystem.setShader and ShaderProgram-based setupTarget were
                 * removed in 1.21.5. The picker_models pipeline must be bound via its RenderLayer and the
                 * per-object Target uniform supplied through the pipeline's UBO/DynamicUniforms. */
            });

            light = 0;
        }
        else
        {
            /* TODO(1.21.11 render): RenderSystem.enableBlend() is gone; blend state now lives in each
             * RenderLayer's RenderPipeline. */
            CustomVertexConsumerProvider.hijackVertexFormat((l) -> {});
        }

        BlockFormRenderer.color.set(context.color);
        FormColorBlend.blend(BlockFormRenderer.color, this.form.color.get(), this.form.additiveColor.get());

        consumers.setSubstitute(BBSRendering.getColorConsumer(BlockFormRenderer.color));

        /* TODO(1.21.11 render): see renderInUI — the high-level ItemRenderer.renderItem(...) overload
         * was removed by the 1.21.4 item-model rewrite and the new ItemRenderState path emits into an
         * OrderedRenderCommandQueue, which the CustomVertexConsumerProvider recolor/picking hook cannot
         * intercept yet. Neutralized; restore faithful item rendering once the foundation is ported.
         * Note: this previously used context.light/context.overlay, context.stack, and the form's
         * ItemDisplayContext (this.form.modelTransform.get()). */

        consumers.draw();
        consumers.setSubstitute(null);

        CustomVertexConsumerProvider.clearRunnables();

        context.stack.pop();

        /* TODO(1.21.11 render): RenderSystem.enableDepthTest() was removed in 1.21.5; depth testing is
         * now encoded per RenderLayer via DepthTestFunction on its pipeline. */
    }
}
