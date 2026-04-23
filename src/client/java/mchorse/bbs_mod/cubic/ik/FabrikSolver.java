package mchorse.bbs_mod.cubic.ik;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

final class FabrikSolver
{
    private static final float EPS = 1.0e-6f;

    private FabrikSolver()
    {
    }

    public static List<Vector3f> solve(List<Vector3f> startPositions, Vector3f target, int maxIterations, float tolerance)
    {
        return solve(startPositions, target, null, null, 0F, 0F, maxIterations, tolerance);
    }

    public static List<Vector3f> solve(List<Vector3f> startPositions, Vector3f target, Vector3f pole, Vector3f prevNormal, float hysteresisRad, float singularityRad, int maxIterations, float tolerance)
    {
        int n = startPositions.size();

        if (n < 2)
        {
            return startPositions;
        }

        List<Vector3f> p = startPositions;

        float[] d = new float[n - 1];
        float total = 0F;

        for (int i = 0; i < n - 1; i++)
        {
            float len = p.get(i).distance(p.get(i + 1));
            d[i] = len;
            total += len;
        }

        Vector3f root = new Vector3f(p.get(0));
        Vector3f dir = new Vector3f();
        float rootToTarget = root.distance(target);

        if (rootToTarget > total)
        {
            dir.set(target).sub(root);

            if (dir.lengthSquared() < 1.0e-10f)
            {
                return p;
            }

            dir.normalize();

            p.get(0).set(root);

            for (int i = 0; i < n - 1; i++)
            {
                p.get(i + 1).set(p.get(i)).fma(d[i], dir);
            }

            return p;
        }

        if (p.get(n - 1).distanceSquared(target) <= tolerance * tolerance)
        {
            return p;
        }

        for (int iter = 0; iter < maxIterations; iter++)
        {
            p.get(n - 1).set(target);

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

            if (p.get(n - 1).distanceSquared(target) <= tolerance * tolerance)
            {
                break;
            }
        }

        if (pole != null)
        {
            applyPoleVector(p, pole, prevNormal, hysteresisRad, singularityRad);
        }

        return p;
    }

    private static void applyPoleVector(List<Vector3f> p, Vector3f pole, Vector3f prevNormal, float hysteresisRad, float singularityRad)
    {
        if (p == null || pole == null || p.size() < 3)
        {
            return;
        }

        Vector3f axis = new Vector3f();
        Vector3f ab = new Vector3f();
        Vector3f ap = new Vector3f();
        Vector3f tmp = new Vector3f();
        Vector3f cross = new Vector3f();
        Quaternionf q = new Quaternionf();
        Vector3f ba = new Vector3f();
        Vector3f cb = new Vector3f();
        Vector3f bRel = new Vector3f();
        Vector3f bPlus = new Vector3f();
        Vector3f bMinus = new Vector3f();
        Vector3f nPlus = new Vector3f();
        Vector3f nMinus = new Vector3f();
        Vector3f tmp2 = new Vector3f();

        float hysteresisMargin = hysteresisRad > 0F ? (1F - (float) Math.cos(hysteresisRad)) : 0F;

        for (int i = 1; i < p.size() - 1 && i < 2; i++)
        {
            Vector3f a = p.get(i - 1);
            Vector3f b = p.get(i);
            Vector3f c = p.get(i + 1);

            axis.set(c).sub(a);
            float axisLenSq = axis.lengthSquared();

            if (axisLenSq <= EPS * EPS)
            {
                continue;
            }

            axis.mul(1F / (float) Math.sqrt(axisLenSq));

            ab.set(b).sub(a);
            ap.set(pole).sub(a);

            float dot = ab.dot(axis);
            tmp.set(axis).mul(dot);
            ab.sub(tmp);

            dot = ap.dot(axis);
            tmp.set(axis).mul(dot);
            ap.sub(tmp);

            float abLenSq = ab.lengthSquared();
            float apLenSq = ap.lengthSquared();

            if (abLenSq <= EPS * EPS || apLenSq <= EPS * EPS)
            {
                continue;
            }

            ab.mul(1F / (float) Math.sqrt(abLenSq));
            ap.mul(1F / (float) Math.sqrt(apLenSq));

            cross.set(ab).cross(ap);
            float sin = axis.dot(cross);
            float cos = ab.dot(ap);
            float angleBase = (float) Math.atan2(sin, cos);

            float chosenAngle = angleBase;

            if (prevNormal != null && prevNormal.lengthSquared() > EPS * EPS)
            {
                ba.set(b).sub(a);
                cb.set(c).sub(b);

                float baLenSq = ba.lengthSquared();
                float cbLenSq = cb.lengthSquared();

                if (baLenSq > EPS * EPS && cbLenSq > EPS * EPS && singularityRad > 0F)
                {
                    float inv = 1F / (float) Math.sqrt(baLenSq * cbLenSq);
                    float cosElbow = ba.dot(cb) * inv;
                    if (cosElbow < -1F) cosElbow = -1F;
                    else if (cosElbow > 1F) cosElbow = 1F;
                    float elbowAngle = (float) Math.acos(cosElbow);
                    if (elbowAngle <= singularityRad)
                    {
                        continue;
                    }
                }

                bRel.set(b).sub(a);

                q.identity().fromAxisAngleRad(axis.x, axis.y, axis.z, angleBase);
                bPlus.set(bRel);
                q.transform(bPlus);
                bPlus.add(a);

                q.identity().fromAxisAngleRad(axis.x, axis.y, axis.z, -angleBase);
                bMinus.set(bRel);
                q.transform(bMinus);
                bMinus.add(a);

                nPlus.set(bPlus).sub(a);
                tmp2.set(c).sub(bPlus);
                nPlus.cross(tmp2);
                float nPlusLenSq = nPlus.lengthSquared();
                float dotPlus = -Float.MAX_VALUE;
                if (nPlusLenSq > EPS * EPS)
                {
                    nPlus.mul(1F / (float) Math.sqrt(nPlusLenSq));
                    dotPlus = nPlus.dot(prevNormal);
                }

                nMinus.set(bMinus).sub(a);
                tmp2.set(c).sub(bMinus);
                nMinus.cross(tmp2);
                float nMinusLenSq = nMinus.lengthSquared();
                float dotMinus = -Float.MAX_VALUE;
                if (nMinusLenSq > EPS * EPS)
                {
                    nMinus.mul(1F / (float) Math.sqrt(nMinusLenSq));
                    dotMinus = nMinus.dot(prevNormal);
                }

                if (dotMinus > dotPlus + hysteresisMargin)
                {
                    chosenAngle = -angleBase;
                }
            }

            q.identity().fromAxisAngleRad(axis.x, axis.y, axis.z, chosenAngle);
            tmp.set(b).sub(a);
            q.transform(tmp);
            b.set(a).add(tmp);
        }
    }
}
