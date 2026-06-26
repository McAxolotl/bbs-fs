package mchorse.bbs_mod.cubic.physics;

import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Force fields acting on the chains: the per-chain gravity (with its relative-gravity rotation) and the
 * global wind. Both produce a per-step acceleration the solver adds during Verlet integration.
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

    /**
     * Global wind acceleration in world space: the configured direction normalised and scaled by the base
     * gravity magnitude and the wind strength. It is one field for the whole model, so the solver computes it
     * once per step. Leaves {@code out} at zero when there is no wind.
     */
    static void computeWind(ModelPhysicsConfig.Wind wind, float base, Vector3f out)
    {
        out.set(0F, 0F, 0F);

        if (wind == null || !wind.active())
        {
            return;
        }

        out.set(wind.x(), wind.y(), wind.z());

        if (out.lengthSquared() < EPS * EPS)
        {
            out.set(0F, 0F, 0F);
            return;
        }

        out.normalize().mul(base * wind.strength());
    }
}
