package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.utils.joml.Matrices;
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

    public static void apply(Model model, List<ModelIKCache.CompiledChain> chains, Map<String, Vector3f> controllerTargets)
    {
        if (model == null || chains == null || chains.isEmpty())
        {
            return;
        }

        Set<String> wanted = new HashSet<>();

        for (ModelIKCache.CompiledChain chain : chains)
        {
            wanted.add(chain.controller());
            wanted.addAll(chain.chainRootToEffector());
        }

        if (wanted.isEmpty())
        {
            return;
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        CubicRenderer.collectPivotFrames(model, wanted, frames);

        for (ModelIKCache.CompiledChain chain : chains)
        {
            applyChain(model, chain, frames, controllerTargets);
        }
    }

    private static void applyChain(Model model, ModelIKCache.CompiledChain chain, Map<String, PivotFrame> frames, Map<String, Vector3f> controllerTargets)
    {
        PivotFrame controllerFrame = frames.get(chain.controller());

        if (controllerFrame == null)
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

        Vector3f override = controllerTargets == null ? null : controllerTargets.get(chain.controller());
        Vector3f target = override != null ? new Vector3f(override) : new Vector3f(controllerFrame.position());
        List<Vector3f> solved = FabrikSolver.solve(currentPositions, target, MAX_ITERATIONS, TOLERANCE);
        if (rootParentRotation == null)
        {
            return;
        }

        Vector3f[] solvedArray = solved.toArray(new Vector3f[0]);
        CubicRenderer.applyRotations(model, rootParentRotation, chainIds, solvedArray);
    }
}
