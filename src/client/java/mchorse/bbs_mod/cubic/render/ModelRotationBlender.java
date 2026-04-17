package mchorse.bbs_mod.cubic.render;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Applies weighted blending between current local bone rotations and solver output.
 */
public final class ModelRotationBlender
{
    private static final float EPS = 1.0e-6f;

    private ModelRotationBlender()
    {
    }

    public static void applyWeightedRotations(Model model, Quaternionf rootParentRotation, List<String> ids, Vector3f[] positions, float weight)
    {
        float factor = clamp01(weight);

        if (factor <= EPS)
        {
            return;
        }

        if (factor >= 1F - EPS)
        {
            CubicRenderer.applyRotations(model, rootParentRotation, ids, positions);
            return;
        }

        if (model == null || rootParentRotation == null || ids == null || positions == null || ids.isEmpty() || positions.length < 2)
        {
            return;
        }

        int rotCount = getRotationCount(ids, positions);

        if (rotCount <= 0)
        {
            return;
        }

        ModelGroup[] bones = new ModelGroup[rotCount];
        Quaternionf[] baseLocal = new Quaternionf[rotCount];
        float[] baseX = new float[rotCount];
        float[] baseY = new float[rotCount];
        float[] baseZ = new float[rotCount];

        for (int i = 0; i < rotCount; i++)
        {
            ModelGroup bone = model.getGroup(ids.get(i));

            if (bone == null)
            {
                return;
            }

            bones[i] = bone;
            baseX[i] = bone.current.rotate.x;
            baseY[i] = bone.current.rotate.y;
            baseZ[i] = bone.current.rotate.z;
            baseLocal[i] = toLocalRotation(bone.current.rotate, bone.current.rotate2);
        }

        CubicRenderer.applyRotations(model, rootParentRotation, ids, positions);

        for (int i = 0; i < rotCount; i++)
        {
            ModelGroup bone = bones[i];
            Quaternionf solved = toLocalRotation(bone.current.rotate, bone.current.rotate2);
            Quaternionf blended = new Quaternionf(baseLocal[i]).slerp(solved, factor);
            Vector3f euler = Matrices.toEulerZYXDegrees(blended);

            euler.x = wrapDegreesNear(euler.x, baseX[i]);
            euler.y = wrapDegreesNear(euler.y, baseY[i]);
            euler.z = wrapDegreesNear(euler.z, baseZ[i]);

            bone.current.rotate.set(euler);
            bone.current.rotate2.set(0F, 0F, 0F);
        }
    }

    private static Quaternionf toLocalRotation(Vector3f rotate, Vector3f rotate2)
    {
        Quaternionf q = Matrices.toQuaternionZYXDegrees(rotate.x, rotate.y, rotate.z);

        if (rotate2.x != 0F || rotate2.y != 0F || rotate2.z != 0F)
        {
            q.mul(Matrices.toQuaternionZYXDegrees(rotate2.x, rotate2.y, rotate2.z));
        }

        return q;
    }

    private static int getRotationCount(List<String> ids, Vector3f[] positions)
    {
        int boneCount = ids.size();
        boolean hasTip = positions.length >= boneCount + 1;

        return boneCount - 1 + (hasTip ? 1 : 0);
    }

    private static float clamp01(float value)
    {
        if (value < 0F)
        {
            return 0F;
        }

        return Math.min(value, 1F);
    }

    private static float wrapDegreesNear(float angle, float reference)
    {
        float delta = angle - reference;

        while (delta > 180F)
        {
            angle -= 360F;
            delta -= 360F;
        }

        while (delta < -180F)
        {
            angle += 360F;
            delta += 360F;
        }

        return angle;
    }
}
