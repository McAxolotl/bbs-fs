package mchorse.bbs_mod.forms;

import mchorse.bbs_mod.forms.renderers.utils.RecolorVertexConsumer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;

import java.util.function.Consumer;
import java.util.function.Function;

public class CustomVertexConsumerProvider implements VertexConsumerProvider
{
    private static Consumer<RenderLayer> runnables;

    private final VertexConsumerProvider.Immediate delegate;
    private Function<VertexConsumer, VertexConsumer> substitute;
    private boolean ui;

    public static void drawLayer(RenderLayer layer)
    {
        if (runnables != null)
        {
            runnables.accept(layer);
        }
    }

    public static void hijackVertexFormat(Consumer<RenderLayer> runnable)
    {
        runnables = runnable;
    }

    public static void clearRunnables()
    {
        runnables = null;
    }

    public CustomVertexConsumerProvider(VertexConsumerProvider.Immediate delegate)
    {
        this.delegate = delegate;
    }

    public void setSubstitute(Function<VertexConsumer, VertexConsumer> substitute)
    {
        this.substitute = substitute;

        if (this.substitute == null)
        {
            RecolorVertexConsumer.newColor = null;
        }
    }

    public void setUI(boolean ui)
    {
        this.ui = ui;
    }

    @Override
    public VertexConsumer getBuffer(RenderLayer renderLayer)
    {
        VertexConsumer buffer = this.delegate.getBuffer(renderLayer);

        if (this.substitute != null)
        {
            VertexConsumer apply = this.substitute.apply(buffer);

            if (apply != null)
            {
                return apply;
            }
        }

        return buffer;
    }

    public void draw()
    {
        this.delegate.draw();

        if (this.ui)
        {
            /* In 1.21.1 this forced the depth func back to GL_ALWAYS because stuff
             * rendered by a vertex consumer was resetting the depth func to GL_LESS.
             *
             * As of 1.21.5 the GPU-pipeline rewrite removed imperative GL state from
             * RenderSystem (RenderSystem.depthFunc is gone) — depth testing is now baked
             * into each RenderLayer's RenderPipeline via DepthTestFunction. The UI layers
             * therefore have to carry a NO_DEPTH_TEST / GL_ALWAYS-equivalent pipeline
             * themselves; there is no longer a global func to "force back" here.
             *
             * TODO(1.21.11 render): verify at runtime. If UI vertex-consumer draws still
             * leak a depth func that hides later UI, encode DepthTestFunction.NO_DEPTH_TEST
             * on the affected BBS UI RenderLayer pipelines (see BBSShaders) rather than
             * trying to mutate global state from here.
             */
        }
    }
}
