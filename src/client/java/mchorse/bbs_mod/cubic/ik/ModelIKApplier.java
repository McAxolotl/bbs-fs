package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ModelIKApplier
{
    private static final int MAX_ITERATIONS = 12;
    private static final float TOLERANCE = 1.0e-4f;

    private ModelIKApplier()
    {
    }

    public static void apply(IModel model, List<ModelIKCache.CompiledChain> chains, Map<String, Vector3f> controllerTargets, Map<String, Float> poseFixByBone)
    {
        if (model == null || chains == null || chains.isEmpty())
        {
            return;
        }

        Set<String> wanted = new HashSet<>();

        for (ModelIKCache.CompiledChain chain : chains)
        {
            wanted.add(chain.target());
            wanted.addAll(chain.chainRootToEffector());

            if (!chain.pole().isEmpty())
            {
                wanted.add(chain.pole());
            }
        }

        if (wanted.isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            applyChain(model, chain, frames, controllerTargets, poseFixByBone);
        }
    }

    private static void applyChain(IModel model, ModelIKCache.CompiledChain chain, Map<String, PivotFrame> frames, Map<String, Vector3f> controllerTargets, Map<String, Float> poseFixByBone)
    {
        float poseFix = getChainPoseFix(chain, poseFixByBone);
        float weight = chain.weight() * (1F - poseFix);

        if (weight <= 0F)
        {
            return;
        }

        PivotFrame targetFrame = frames.get(chain.target());

        if (targetFrame == null)
        {
            return;
        }

        List<String> chainIds = chain.chainRootToEffector();
        List<Vector3f> currentPositions = new ArrayList<>(chainIds.size());
        Quaternionf rootParentRotation = null;

        for (String id : chainIds)
        {
            PivotFrame frame = frames.get(id);

            if (frame == null)
            {
                return;
            }

            currentPositions.add(new Vector3f(frame.position()));

            if (rootParentRotation == null)
            {
                rootParentRotation = new Quaternionf(frame.parentRotation());
            }
        }

        Vector3f override = controllerTargets == null ? null : controllerTargets.get(chain.target());
        Vector3f target = override != null ? new Vector3f(override) : new Vector3f(targetFrame.position());

        PivotFrame poleFrame = chain.pole().isEmpty() ? null : frames.get(chain.pole());
        Vector3f pole = poleFrame == null ? null : new Vector3f(poleFrame.position());

        float poleAngleRad = (float) Math.toRadians(chain.poleAngle());

        List<Vector3f> solved = IKSolver.solve(currentPositions, target, pole, poleAngleRad, MAX_ITERATIONS, TOLERANCE);
        if (rootParentRotation == null)
        {
            return;
        }

        Vector3f[] solvedArray = solved.toArray(new Vector3f[solved.size()]);
        ModelRotationBlender.applyWeightedRotations(model, rootParentRotation, chainIds, solvedArray, weight);
    }

    private static float getChainPoseFix(ModelIKCache.CompiledChain chain, Map<String, Float> poseFixByBone)
    {
        if (poseFixByBone == null || poseFixByBone.isEmpty() || chain == null)
        {
            return 0F;
        }

        float maxFix = getFix(poseFixByBone, chain.target());

        for (String bone : chain.chainRootToEffector())
        {
            maxFix = Math.max(maxFix, getFix(poseFixByBone, bone));

            if (maxFix >= 1F)
            {
                return 1F;
            }
        }

        return maxFix;
    }

    private static float getFix(Map<String, Float> poseFixByBone, String bone)
    {
        Float value = poseFixByBone.get(bone);

        if (value == null)
        {
            return 0F;
        }

        if (value <= 0F)
        {
            return 0F;
        }

        return Math.min(value, 1F);
    }
}
