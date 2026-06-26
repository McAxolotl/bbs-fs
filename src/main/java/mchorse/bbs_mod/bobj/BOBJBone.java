package mchorse.bbs_mod.bobj;

import mchorse.bbs_mod.utils.joml.Matrices;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class BOBJBone
{
    /* Meta information */
    public int index;
    public String name;
    public String parent;
    public BOBJBone parentBone;

    /* Transformations */
    public final Transform transform = new Transform();

    /**
     * Computed bone matrix which is used for transformations. This 
     * matrix isn't multiplied by inverse bone matrix. 
     */
    public Matrix4f mat = new Matrix4f();

    public Matrix4f originMat = new Matrix4f();

    /**
     * Bone matrix 
     */
    public Matrix4f boneMat;

    /**
     * Inverse bone matrix 
     */
    public Matrix4f invBoneMat = new Matrix4f();

    /**
     * Relative-to-parent bone matrix
     */
    public Matrix4f relBoneMat = new Matrix4f();

    /**
     * Transient full local orientation an IK solve gives this bone, applied raw in
     * place of the euler rotate triple so the pole owns the whole orientation
     * (bypassing the swing/twist euler reconstruction). Null when not IK-driven.
     */
    public Quaternionf orient;

    public BOBJBone(int index, String name, String parent, Matrix4f boneMat)
    {
        this.index = index;
        this.name = name;
        this.parent = parent;
        this.boneMat = boneMat;

        this.invBoneMat.set(boneMat);
        this.invBoneMat.invert();

        this.relBoneMat.identity();
    }

    public Matrix4f compute()
    {
        Matrix4f mat = this.computeMatrix(new Matrix4f());

        this.mat.set(mat);
        mat.mul(this.invBoneMat);

        return mat;
    }

    public Matrix4f computeMatrix(Matrix4f m)
    {
        this.mat.set(this.relBoneMat);
        this.originMat.set(this.relBoneMat);
        this.applyTransformations();

        if (this.parentBone != null)
        {
            m.set(this.parentBone.mat).mul(this.originMat);
            this.originMat.set(m);
            m.identity().set(this.parentBone.mat);
        }

        m.mul(this.mat);

        return m;
    }

    public void applyTransformations()
    {
        this.mat.translate(this.transform.translate);
        this.originMat.translate(this.transform.translate);

        if (this.orient != null)
        {
            /* orient is the full local rotation (it already folds rotate2), so the euler triples are skipped. */
            this.mat.rotate(this.orient);
        }
        else
        {
            if (this.transform.rotate.z != 0F) this.mat.rotateZ(this.transform.rotate.z);
            if (this.transform.rotate.y != 0F) this.mat.rotateY(this.transform.rotate.y);
            if (this.transform.rotate.x != 0F) this.mat.rotateX(this.transform.rotate.x);

            if (this.transform.rotate2.z != 0F) this.mat.rotateZ(this.transform.rotate2.z);
            if (this.transform.rotate2.y != 0F) this.mat.rotateY(this.transform.rotate2.y);
            if (this.transform.rotate2.x != 0F) this.mat.rotateX(this.transform.rotate2.x);
        }

        this.mat.scale(this.transform.scale);
    }

    /**
     * Composes one rotation layer into {@link #orient} (BOBJ rotations are radians). Mirrors
     * {@link mchorse.bbs_mod.cubic.data.model.ModelGroup#composeOrient}: the first layer seeds from the euler
     * accumulated so far (rotate folded with rotate2) so a single layer is byte-identical; later layers
     * multiply their delta. Call AFTER the layer's additive euler readback to {@code transform.rotate}.
     */
    public void composeOrient(Quaternionf delta)
    {
        if (this.orient == null)
        {
            this.orient = Matrices.toQuaternionZYXRadians(this.transform.rotate.x, this.transform.rotate.y, this.transform.rotate.z);

            if (this.transform.rotate2.x != 0F || this.transform.rotate2.y != 0F || this.transform.rotate2.z != 0F)
            {
                this.orient.mul(Matrices.toQuaternionZYXRadians(this.transform.rotate2.x, this.transform.rotate2.y, this.transform.rotate2.z));
            }
        }
        else
        {
            this.orient.mul(delta);
        }
    }

    public void reset()
    {
        this.transform.identity();
        this.orient = null;
    }
}