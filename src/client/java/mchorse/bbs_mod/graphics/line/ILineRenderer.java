package mchorse.bbs_mod.graphics.line;

import net.minecraft.client.render.VertexConsumer;
import org.joml.Matrix3x2fc;

/**
 * 2D line vertex emitter.
 *
 * Ported to 1.21.11: the two-phase GUI emits geometry through a {@link VertexConsumer} transformed by
 * the 2D GUI pose ({@link Matrix3x2fc}) rather than an immediate {@code BufferBuilder} with a 4x4
 * matrix (see {@link LineBuilder}).
 */
public interface ILineRenderer <T>
{
    public void render(VertexConsumer builder, Matrix3x2fc matrix, LinePoint<T> point);
}
