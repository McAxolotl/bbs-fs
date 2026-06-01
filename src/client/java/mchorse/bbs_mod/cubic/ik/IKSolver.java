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

    public static List<Vector3f> solve(List<Vector3f> positions, Vector3f target, Vector3f pole, float poleAngleRad, int maxIterations, float tolerance)
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
        Vector3f goal = clampReach(root, target, total);
        Vector3f poseRef = pole == null ? captureBendReference(positions) : null;

        if (n == 3)
        {
            solveTwoBone(positions, root, goal, d[0], d[1]);
        }
        else
        {
            solveFabrik(positions, root, goal, d, maxIterations, tolerance);
        }

        orientBend(positions, pole, poseRef, poleAngleRad);

        return positions;
    }

    private static Vector3f clampReach(Vector3f root, Vector3f target, float total)
    {
        Vector3f goal = new Vector3f(target);
        float dist = root.distance(target);

        if (dist > total * REACH_LIMIT)
        {
            Vector3f dir = new Vector3f(target).sub(root);

            if (dir.lengthSquared() >= 1.0e-10f)
            {
                dir.normalize();
                goal.set(root).fma(total * REACH_LIMIT, dir);
            }
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
     * Rotates the whole chain about the root-to-tip axis so the bend plane aims
     * at the pole (or keeps the posed side), then adds the pole angle. The root
     * and tip lie on the axis, so reach is preserved.
     */
    private static void orientBend(List<Vector3f> p, Vector3f pole, Vector3f poseRef, float poleAngleRad)
    {
        int n = p.size();

        if (n < 3)
        {
            return;
        }

        Vector3f root = p.get(0);
        Vector3f axis = new Vector3f(p.get(n - 1)).sub(root);

        if (!normalize(axis))
        {
            return;
        }

        Vector3f current = new Vector3f(p.get(1)).sub(root);

        if (!project(current, axis))
        {
            return;
        }

        float theta = poleAngleRad;
        Vector3f desired = new Vector3f();

        if (pole != null)
        {
            desired.set(pole).sub(root);
        }
        else if (poseRef != null)
        {
            desired.set(poseRef);
        }
        else
        {
            desired = null;
        }

        if (desired != null && project(desired, axis))
        {
            theta += signedAngle(current, desired, axis);
        }

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

    private static Vector3f captureBendReference(List<Vector3f> p)
    {
        if (p.size() < 3)
        {
            return null;
        }

        return perpendicular(p.get(0), p.get(1), p.get(2));
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
