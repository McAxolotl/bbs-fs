package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.vao.ModelVAO;
import mchorse.bbs_mod.cubic.render.vao.ModelVAORenderer;
import mchorse.bbs_mod.obj.shapes.ShapeKeys;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.StencilMap;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.interps.Lerps;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;

import java.util.Map;
import java.util.function.Function;

public class CubicVAORenderer extends CubicCubeRenderer
{
    private ModelInstance model;
    private Function<String, Link> textureResolver;

    // TODO(1.21.11 render merge): per-material ModelVAO texture resolver — re-port against pipeline API
    // (was: 1.21.1 ctor took a ShaderProgram program + Function<String,Link> textureResolver and stored the
    //  resolver to bind each material's texture before its draw; HEAD draws every material through the pipeline
    //  RenderLayer with no per-material shader/texture bind).
    public CubicVAORenderer(ModelInstance model, int light, int overlay, StencilMap stencilMap, ShapeKeys shapeKeys)
    {
        super(light, overlay, stencilMap, shapeKeys);

        this.model = model;
    }

    @Override
    public boolean renderGroup(BufferBuilder builder, MatrixStack stack, ModelGroup group, Model model)
    {
        Map<String, ModelVAO> groupVaos = this.model.getVaos().get(group);

        if (groupVaos == null || groupVaos.isEmpty() || !group.visible)
        {
            return false;
        }

        float r = this.r * group.color.r;
        float g = this.g * group.color.g;
        float b = this.b * group.color.b;
        float a = this.a * group.color.a;
        int light = this.light;

        if (this.stencilMap != null)
        {
            light = this.stencilMap.increment ? group.index : 0;
        }
        else
        {
            int u = (int) Lerps.lerp(light & '\uffff', LightmapTextureManager.MAX_BLOCK_LIGHT_COORDINATE, MathUtils.clamp(group.lighting, 0F, 1F));
            int v = light >> 16 & '\uffff';

            light = u | v << 16;
        }

        /* One draw per material (multi-material ModelVAO map from the 1.21.1 merge). */
        // TODO(1.21.11 render merge): per-material ModelVAO texture resolver — re-port against pipeline API
        // (was: 1.21.1 resolved textureResolver.apply(entry.getKey()) per material and bound it via
        //  BBSModClient.getTextures().bindTexture(link) before drawing, using the ModelVAORenderer.render(program, ...)
        //  overload; HEAD draws every material through the pipeline RenderLayer with no per-material texture bind).
        for (Map.Entry<String, ModelVAO> entry : groupVaos.entrySet())
        {
            ModelVAORenderer.render(entry.getValue(), stack, r, g, b, a, light, this.overlay);
        }

        return false;
    }
}