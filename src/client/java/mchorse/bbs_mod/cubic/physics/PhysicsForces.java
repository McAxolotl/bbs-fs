package mchorse.bbs_mod.cubic.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Force fields acting on the chains. Currently just gravity (with the per-chain relative-gravity rotation);
 * the home for wind and other fields later.
 */
final class PhysicsForces
{
    private static final float EPS = 1.0e-6f;

    private PhysicsForces()
    {
    }

    static void computeGravityDirection(ModelPhysicsCache.CompiledChain chain, Quaternionf parentRotation, float gravity, Vector3f out)
    {
        out.set(0F, -1F, 0F);

        if (chain.relativeGravity() && parentRotation != null)
        {
            /* Model bone forward axis is -Y in this rig convention. */
            parentRotation.transform(out);
        }

        if (chain.hasGravityRotation())
        {
            if (parentRotation != null)
            {
                /* Apply user rotation in chain local space, then convert back to world space. */
                Quaternionf inverseParent = new Quaternionf(parentRotation).invert();
                inverseParent.transform(out);
                chain.applyGravityRotation(out);
                parentRotation.transform(out);
            }
            else
            {
                chain.applyGravityRotation(out);
            }
        }

        if (out.lengthSquared() < EPS * EPS)
        {
            out.set(0F, -1F, 0F);
        }

        out.normalize().mul(gravity);
    }
}
