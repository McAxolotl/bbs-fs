package mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories;

import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.Axis;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.joml.Vectors;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3d;

import java.util.function.Consumer;

public abstract class UIKeyframePropTransform extends UIPropTransform
{
    protected abstract void applyTransform(Consumer<Transform> consumer);

    @Override
    public void pasteTranslation(Vector3d translation)
    {
        this.applyTransform((poseT) -> poseT.translate.set(translation));
        this.refillTransform();
    }

    @Override
    public void pasteScale(Vector3d scale)
    {
        this.applyTransform((poseT) -> poseT.scale.set(scale));
        this.refillTransform();
    }

    @Override
    public void pasteRotation(Vector3d rotation)
    {
        this.applyTransform((poseT) -> poseT.rotate.set(Vectors.toRad(rotation)));
        this.refillTransform();
    }

    @Override
    public void pasteRotation2(Vector3d rotation)
    {
        this.applyTransform((poseT) -> poseT.rotate2.set(Vectors.toRad(rotation)));
        this.refillTransform();
    }

    @Override
    public void setT(Axis axis, double x, double y, double z)
    {
        Transform transform = this.getTransform();
        float dx = (float) (x - transform.translate.x);
        float dy = (float) (y - transform.translate.y);
        float dz = (float) (z - transform.translate.z);

        this.applyTransform((poseT) ->
        {
            poseT.translate.x += dx;
            poseT.translate.y += dy;
            poseT.translate.z += dz;
        });
    }

    @Override
    public void setS(Axis axis, double x, double y, double z)
    {
        Transform transform = this.getTransform();
        float dx = (float) (x - transform.scale.x);
        float dy = (float) (y - transform.scale.y);
        float dz = (float) (z - transform.scale.z);

        this.applyTransform((poseT) ->
        {
            poseT.scale.x += dx;
            poseT.scale.y += dy;
            poseT.scale.z += dz;
        });
    }

    @Override
    public void setR(Axis axis, double x, double y, double z)
    {
        Transform transform = this.getTransform();
        float dx = MathUtils.toRad((float) x) - transform.rotate.x;
        float dy = MathUtils.toRad((float) y) - transform.rotate.y;
        float dz = MathUtils.toRad((float) z) - transform.rotate.z;

        this.applyTransform((poseT) ->
        {
            poseT.rotate.x += dx;
            poseT.rotate.y += dy;
            poseT.rotate.z += dz;
        });
    }

    @Override
    public void setR2(Axis axis, double x, double y, double z)
    {
        Transform transform = this.getTransform();
        float dx = MathUtils.toRad((float) x) - transform.rotate2.x;
        float dy = MathUtils.toRad((float) y) - transform.rotate2.y;
        float dz = MathUtils.toRad((float) z) - transform.rotate2.z;

        this.applyTransform((poseT) ->
        {
            poseT.rotate2.x += dx;
            poseT.rotate2.y += dy;
            poseT.rotate2.z += dz;
        });
    }
}
