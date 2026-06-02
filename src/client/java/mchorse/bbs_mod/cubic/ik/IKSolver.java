package mchorse.bbs_mod.cubic.ik;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Single-chain IK modeled after Blender: the positions are solved to reach the
 * target (analytic for a two-bone limb, FABRIK otherwise), then the whole bend
 * plane is rotated about the root-to-tip axis towards the pole and offset by the
 * pole angle. With no pole the chain keeps the side it was posed towards.
 */
final class IKSolver
{
    private static final float EPS = 1.0e-6f;

    /* Keep the goal a hair inside reach so the chain never locks dead straight
     * (a fully extended chain has an undefined bend plane and rolls). */
    private static final float REACH_LIMIT = 0.999F;

    private IKSolver()
    {
    }

    public static List<Vector3f> solve(List<Vector3f> positions, Vector3f target, boolean applyPole, float poleAngleRad, float softness, int maxIterations, float tolerance)
    {
        int n = positions.size();

        if (n < 2)
        {
            return positions;
        }

        float[] d = new float[n - 1];
        float total = 0F;

        for (int i = 0; i < n - 1; i++)
        {
            d[i] = positions.get(i).distance(positions.get(i + 1));
            total += d[i];
        }

        if (total <= EPS)
        {
            return positions;
        }

        Vector3f root = new Vector3f(positions.get(0));
        Vector3f goal = clampReach(root, target, total, softness);
        Vector3f hinge = applyPole ? captureHingeAxis(positions) : null;

        if (n == 3)
        {
            solveTwoBone(positions, root, goal, d[0], d[1]);
        }
        else
        {
            solveFabrik(positions, root, goal, d, maxIterations, tolerance);
        }

        orientBend(positions, hinge, poleAngleRad);

        return positions;
    }

    /**
     * Maps the target onto an effective reach distance. With {@code softness > 0}
     * this is "soft IK": near full extension the effective distance approaches the
     * chain length asymptotically (and C1-continuously), so the limb never snaps
     * dead straight when the target is pulled out of reach. With softness 0 it is
     * a hard clamp at {@code total * REACH_LIMIT}.
     */
    private static Vector3f clampReach(Vector3f root, Vector3f target, float total, float softness)
    {
        Vector3f goal = new Vector3f(target);
        float dist = root.distance(target);

        if (dist < EPS)
        {
            return goal;
        }

        Vector3f dir = new Vector3f(target).sub(root).div(dist);

        if (softness > EPS)
        {
            float soft = Math.min(softness, 1F) * total;
            float da = total - soft;

            if (dist > da)
            {
                float eff = total - soft * (float) Math.exp(-(dist - da) / soft);
                goal.set(root).fma(eff, dir);
            }
        }
        else if (dist > total * REACH_LIMIT)
        {
            goal.set(root).fma(total * REACH_LIMIT, dir);
        }

        return goal;
    }

    private static void solveTwoBone(List<Vector3f> p, Vector3f root, Vector3f goal, float l1, float l2)
    {
        Vector3f dir = new Vector3f(goal).sub(root);
        float dist = dir.length();

        if (dist < EPS)
        {
            return;
        }

        dir.div(dist);

        float cosA = (l1 * l1 + dist * dist - l2 * l2) / (2F * l1 * dist);
        cosA = Math.max(-1F, Math.min(1F, cosA));
        float sinA = (float) Math.sqrt(Math.max(0F, 1F - cosA * cosA));

        /* Seed the bend on any valid plane; orientBend fixes the direction. */
        Vector3f bend = perpendicular(root, p.get(1), goal);

        if (bend == null)
        {
            bend = new Vector3f();
            anyPerpendicular(dir, bend);
        }

        p.get(1).set(root).fma(l1 * cosA, dir).fma(l1 * sinA, bend);
        p.get(2).set(goal);
    }

    private static void solveFabrik(List<Vector3f> p, Vector3f root, Vector3f goal, float[] d, int maxIterations, float tolerance)
    {
        int n = p.size();
        Vector3f dir = new Vector3f();

        for (int iter = 0; iter < maxIterations; iter++)
        {
            if (p.get(n - 1).distanceSquared(goal) <= tolerance * tolerance)
            {
                break;
            }

            p.get(n - 1).set(goal);

            for (int i = n - 2; i >= 0; i--)
            {
                Vector3f pi = p.get(i);
                Vector3f pj = p.get(i + 1);

                dir.set(pi).sub(pj);
                float lenSq = dir.lengthSquared();

                if (lenSq < 1.0e-10f)
                {
                    continue;
                }

                dir.mul((float) (d[i] / Math.sqrt(lenSq)));
                pi.set(pj).add(dir);
            }

            p.get(0).set(root);

            for (int i = 0; i < n - 1; i++)
            {
                Vector3f pi = p.get(i);
                Vector3f pj = p.get(i + 1);

                dir.set(pj).sub(pi);
                float lenSq = dir.lengthSquared();

                if (lenSq < 1.0e-10f)
                {
                    continue;
                }

                dir.mul((float) (d[i] / Math.sqrt(lenSq)));
                pj.set(pi).add(dir);
            }
        }
    }

    /**
     * Rotates the whole chain about the root-to-tip axis so its bend plane keeps
     * the captured hinge orientation (the limb behaves like a hinge and never
     * inverts), then tilts it by the pole angle. Aligning the bend-plane NORMAL
     * (the hinge axis, which stays perpendicular to the limb as it swings)
     * instead of the bend direction is what prevents the flip. The root and tip
     * lie on the axis, so reach is preserved.
     */
    private static void orientBend(List<Vector3f> p, Vector3f hinge, float poleAngleRad)
    {
        int n = p.size();

        if (n < 3 || hinge == null)
        {
            return;
        }

        Vector3f root = p.get(0);
        Vector3f axis = new Vector3f(p.get(n - 1)).sub(root);

        if (!normalize(axis))
        {
            return;
        }

        /* Current bend-plane normal = (elbow - root) x axis. */
        Vector3f current = new Vector3f(p.get(1)).sub(root).cross(axis);

        if (!normalize(current))
        {
            return;
        }

        /* Desired normal = the captured hinge, projected onto the plane across
         * the axis. Degenerate only when the limb points straight along the
         * hinge (rare) — then we hold the current bend instead of flipping. */
        Vector3f desired = new Vector3f(hinge);

        if (!project(desired, axis))
        {
            return;
        }

        if (poleAngleRad != 0F)
        {
            new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, poleAngleRad).transform(desired);
        }

        float theta = signedAngle(current, desired, axis);

        if (Math.abs(theta) < EPS)
        {
            return;
        }

        Quaternionf q = new Quaternionf().fromAxisAngleRad(axis.x, axis.y, axis.z, theta);
        Vector3f rel = new Vector3f();

        for (int i = 1; i < n - 1; i++)
        {
            rel.set(p.get(i)).sub(root);
            q.transform(rel);
            p.get(i).set(root).add(rel);
        }
    }

    /**
     * The hinge axis: the side direction the limb bends around. When the limb is
     * posed bent, it is the normal of that bend plane, {@code (elbow-root) x
     * (tip-root)}. When the limb is straight (no posed plane — common for rest
     * rigs), it falls back to the limb's side axis {@code limbDir x worldForward}
     * (or x worldUp), which is a fixed direction independent of the target, so
     * locking the bend to it never flips. Null only for a degenerate chain.
     */
    private static Vector3f captureHingeAxis(List<Vector3f> p)
    {
        int n = p.size();

        if (n < 3)
        {
            return null;
        }

        Vector3f a = p.get(0);
        Vector3f normal = new Vector3f(p.get(1)).sub(a).cross(new Vector3f(p.get(2)).sub(a));

        if (normalize(normal))
        {
            return normal;
        }

        /* Straight limb: derive a stable side axis from the limb direction. */
        Vector3f limb = new Vector3f(p.get(n - 1)).sub(a);

        if (!normalize(limb))
        {
            return null;
        }

        Vector3f hinge = new Vector3f(limb).cross(0F, 0F, 1F);

        if (normalize(hinge))
        {
            return hinge;
        }

        hinge = new Vector3f(limb).cross(0F, 1F, 0F);

        return normalize(hinge) ? hinge : null;
    }

    private static Vector3f perpendicular(Vector3f a, Vector3f b, Vector3f c)
    {
        Vector3f axis = new Vector3f(c).sub(a);

        if (!normalize(axis))
        {
            return null;
        }

        Vector3f out = new Vector3f(b).sub(a);

        return project(out, axis) ? out : null;
    }

    private static void anyPerpendicular(Vector3f axis, Vector3f out)
    {
        Vector3f ref = Math.abs(axis.x) < 0.9F ? new Vector3f(1F, 0F, 0F) : new Vector3f(0F, 1F, 0F);

        out.set(axis).cross(ref);

        if (!normalize(out))
        {
            out.set(0F, 1F, 0F);
        }
    }

    private static float signedAngle(Vector3f from, Vector3f to, Vector3f axis)
    {
        Vector3f cross = new Vector3f(from).cross(to);
        float sin = axis.dot(cross);
        float cos = from.dot(to);

        return (float) Math.atan2(sin, cos);
    }

    private static boolean project(Vector3f v, Vector3f axis)
    {
        float dot = v.dot(axis);
        v.x -= axis.x * dot;
        v.y -= axis.y * dot;
        v.z -= axis.z * dot;

        return normalize(v);
    }

    private static boolean normalize(Vector3f v)
    {
        float lenSq = v.lengthSquared();

        if (lenSq <= EPS * EPS)
        {
            return false;
        }

        v.mul(1F / (float) Math.sqrt(lenSq));

        return true;
    }
}
