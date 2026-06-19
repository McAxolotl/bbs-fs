package mchorse.bbs_mod.cubic.render.vao;

import com.mojang.blaze3d.vertex.VertexFormat;
import mchorse.bbs_mod.client.BBSShaders;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BuiltBuffer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

public class ModelVAORenderer
{
    /**
     * Draw a static {@link ModelVAO} through the immediate model RenderLayer. The 1.21.5+ rewrite
     * removed ShaderProgram.bind()/unbind() and the imperative uniform/sampler/fog/light setup; the
     * built-in uniforms now live in the std140 UBOs (DynamicTransforms / Projection / Fog / Lighting)
     * that {@link BBSShaders#getModelLayer()} uploads per draw. The geometry is baked CPU-side into a
     * BufferBuilder (matching the cubic immediate path) and submitted through that layer.
     */
    public static void render(ModelVAO modelVAO, MatrixStack stack, float r, float g, float b, float a, int light, int overlay)
    {
        BufferBuilder builder = Tessellator.getInstance().begin(VertexFormat.DrawMode.TRIANGLES, VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL);

        modelVAO.writeImmediate(builder, stack, r, g, b, a, light, overlay);

        BuiltBuffer built = builder.endNullable();

        if (built != null)
        {
            BBSShaders.getModelLayer().draw(built);
        }
    }
}
