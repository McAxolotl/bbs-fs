package mchorse.bbs_mod.cubic.render.vao;

import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import org.lwjgl.opengl.GL30;

public class ModelVAORenderer
{
    public static void render(ShaderProgram shader, IModelVAO modelVAO, MatrixStack stack, float r, float g, float b, float a, int light, int overlay)
    {
        int currentVAO = GL30.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int currentElementArrayBuffer = GL30.glGetInteger(GL30.GL_ELEMENT_ARRAY_BUFFER_BINDING);

        setupUniforms(stack, shader);

        /* TODO(1.21.11 render): the model RenderPipeline (BBSShaders.getModel()) must be bound via
         * RenderSystem.getDevice()/RenderPass before issuing the raw-GL VAO draw. ShaderProgram.bind()/
         * unbind() no longer exist in 1.21.5+. The VAO geometry is still uploaded/drawn via raw GL below;
         * wire the pipeline bind + UBO uploads here once the GPU-pipeline foundation is in place. */
        modelVAO.render(VertexFormats.POSITION_COLOR_TEXTURE_OVERLAY_LIGHT_NORMAL, r, g, b, a, light, overlay);

        GL30.glBindVertexArray(currentVAO);
        GL30.glBindBuffer(GL30.GL_ELEMENT_ARRAY_BUFFER, currentElementArrayBuffer);
    }

    public static void setupUniforms(MatrixStack stack, ShaderProgram shader)
    {
        /* TODO(1.21.11 render): the entire imperative uniform/sampler/fog/light setup below was removed
         * in 1.21.5+. ShaderProgram no longer exposes the public uniform fields (projectionMat,
         * modelViewMat, fogStart/End/Color/Shape, colorModulator, gameTime, textureMat) nor addSampler();
         * GlUniform is now a near-empty interface (no set(...)); and RenderSystem.getShaderTexture/
         * getProjectionMatrix, getShaderFog, getShaderGameTime, getTextureMatrix, setupShaderLights were all
         * deleted. These built-in uniforms now live in std140 UBOs (Projection / Fog / Lighting /
         * DynamicTransforms) and the custom ones (NormalMat, ViewRotationMat, ColorModulator, light
         * directions) must be supplied as RenderPipeline UBO entries / DynamicUniforms and uploaded per
         * RenderPass. Restore this when the GPU-pipeline foundation is wired up.
         *
         * The matrices that were fed in (preserved for the migration):
         *   ProjectionMat  = RenderSystem.getProjectionMatrix()
         *   ModelViewMat   = RenderSystem.getModelViewMatrix() * stack.peek().getPositionMatrix()
         *   NormalMat      = stack.peek().getNormalMatrix()
         *   ViewRotationMat= InverseView.get()
         *   ColorModulator = (1, 1, 1, 1)
         * plus fog (start/end/color/shape), gameTime, textureMatrix and the 12 Sampler bindings. */
    }
}
