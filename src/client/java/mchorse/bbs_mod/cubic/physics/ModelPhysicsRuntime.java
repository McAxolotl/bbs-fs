package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.render.CubicRenderer;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public final class ModelPhysicsRuntime
{
    private static final float BASE_GRAVITY = 0.08F;
    private static final float EPS = 1.0e-6f;
    private static final float ANCHOR_ROTATION_POS_FOLLOW = 0.85F;
    private static final float ANCHOR_ROTATION_PREV_FOLLOW = 0.75F;
    private static final float ANCHOR_TRANSLATION_INERTIA = 0.5F;

    private static final class ChainState
    {
        public int lastAge = Integer.MIN_VALUE;
        public Vector3f anchor = new Vector3f();
        public Quaternionf anchorRotation = new Quaternionf();
        public Vector3f anchorVelocity = new Vector3f();
        public Vector3f[] pos;
        public Vector3f[] prev;
        public Vector3f[] render;
    }

    private static final class InstanceState
    {
        public final Map<String, ChainState> chains = new HashMap<>();
    }

    private static final WeakHashMap<IEntity, Map<String, InstanceState>> STATES = new WeakHashMap<>();

    private ModelPhysicsRuntime()
    {
    }

    public static void clearCache()
    {
        ModelPhysicsCache.clear();
        STATES.clear();
    }

    public static void invalidate(String modelId)
    {
        ModelPhysicsCache.invalidate(modelId);

        for (Map<String, InstanceState> byModel : STATES.values())
        {
            if (byModel != null)
            {
                byModel.remove(modelId);
            }
        }
    }

    public static void apply(IEntity entity, ModelInstance instance, float transition, Matrix4f baseTransform)
    {
        if (entity == null || instance == null || !(instance.model instanceof Model model))
        {
            return;
        }

        ModelPhysicsCache.Compiled compiled = ModelPhysicsCache.get(instance.id, model);

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, ModelConstraintsConfig.BoneConstraint> constraints = ModelConstraintsRuntime.getBones(instance.id);

        Map<String, InstanceState> byModel = STATES.computeIfAbsent(entity, (e) -> new HashMap<>());
        InstanceState state = byModel.computeIfAbsent(instance.id, (k) -> new InstanceState());

        applyCompiled(entity.getAge(), transition, model, compiled.chains(), constraints, state, baseTransform);
    }

    private static void applyCompiled(int age, float transition, Model model, List<ModelPhysicsCache.CompiledChain> compiledChains, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, InstanceState state, Matrix4f baseTransform)
    {
        Set<String> wanted = new HashSet<>();
        Set<String> chainIds = new HashSet<>();

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            chainIds.add(chain.id());
            wanted.addAll(chain.chainRootToEnd());
        }

        if (!state.chains.isEmpty())
        {
            java.util.Iterator<String> it = state.chains.keySet().iterator();

            while (it.hasNext())
            {
                if (!chainIds.contains(it.next()))
                {
                    it.remove();
                }
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        CubicRenderer.collectPivotFrames(model, wanted, frames, baseTransform);

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            applyChain(age, transition, model, chain, constraints, frames, state);
        }
    }

    private static void applyChain(int age, float transition, Model model, ModelPhysicsCache.CompiledChain chain, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Map<String, PivotFrame> frames, InstanceState instanceState)
    {
        List<String> ids = chain.chainRootToEnd();
        int pivotCount = ids.size();
        int pointCount = pivotCount + 1;

        if (pivotCount < 1)
        {
            return;
        }

        ChainState state = instanceState.chains.computeIfAbsent(chain.id(), (k) -> new ChainState());

        if (state.pos == null || state.pos.length != pointCount)
        {
            state.pos = new Vector3f[pointCount];
            state.prev = new Vector3f[pointCount];
            state.render = new Vector3f[pointCount];

            for (int i = 0; i < pointCount; i++)
            {
                state.pos[i] = new Vector3f();
                state.prev[i] = new Vector3f();
                state.render[i] = new Vector3f();
            }

            state.lastAge = Integer.MIN_VALUE;
        }

        List<PivotFrame> chainFrames = new ArrayList<>(pivotCount);

        for (int i = 0; i < pivotCount; i++)
        {
            PivotFrame frame = frames.get(ids.get(i));

            if (frame == null)
            {
                return;
            }

            chainFrames.add(frame);
        }

        PivotFrame rootFrame = chainFrames.get(0);
        Vector3f anchor = rootFrame.position();
        Quaternionf anchorRotation = rootFrame.worldRotation();

        step(age, model, ids, chain, constraints, anchor, anchorRotation, chainFrames, state);
        Vector3f[] positions = interpolate(state, transition);
        applyRenderAnchorFollow(state, positions, anchor, anchorRotation, transition);
        CubicRenderer.applyRotations(model, chainFrames.get(0).parentRotation(), ids, positions);
    }

    private static Vector3f[] interpolate(ChainState state, float transition)
    {
        if (state.prev == null || state.pos == null || state.render == null || state.prev.length != state.pos.length || state.render.length != state.pos.length)
        {
            return state.pos;
        }

        float t = transition;

        if (t < 0F)
        {
            t = 0F;
        }
        else if (t > 1F)
        {
            t = 1F;
        }

        for (int i = 0; i < state.pos.length; i++)
        {
            state.render[i].set(state.prev[i]).lerp(state.pos[i], t);
        }

        return state.render;
    }

    private static void applyRenderAnchorFollow(ChainState state, Vector3f[] positions, Vector3f anchorPosition, Quaternionf anchorRotation, float transition)
    {
        if (state.lastAge == Integer.MIN_VALUE || positions == null || positions.length == 0)
        {
            return;
        }

        float t = transition;

        if (t <= 0F)
        {
            return;
        }
        else if (t > 1F)
        {
            t = 1F;
        }

        Vector3f oldAnchor = state.anchor;
        Vector3f delta = new Vector3f(anchorPosition).sub(oldAnchor).mul(t);

        Quaternionf dq = new Quaternionf(anchorRotation).mul(new Quaternionf(state.anchorRotation).invert()).normalize();
        Quaternionf dqPartial = new Quaternionf().identity().slerp(dq, t);

        if (Math.abs(delta.x) < EPS && Math.abs(delta.y) < EPS && Math.abs(delta.z) < EPS && Math.abs(dqPartial.x) < EPS && Math.abs(dqPartial.y) < EPS && Math.abs(dqPartial.z) < EPS)
        {
            return;
        }

        for (int i = 0; i < positions.length; i++)
        {
            Vector3f rel = new Vector3f(positions[i]).sub(oldAnchor);
            dqPartial.transform(rel);
            positions[i].set(oldAnchor).add(delta).add(rel);
        }
    }

    private static void step(int age, Model model, List<String> ids, ModelPhysicsCache.CompiledChain chain, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Vector3f anchorPosition, Quaternionf anchorRotation, List<PivotFrame> chainFrames, ChainState state)
    {
        Vector3f newAnchor = anchorPosition;
        Quaternionf newAnchorRotation = anchorRotation;
        float[] lengths = chain.restLengths();

        if (lengths == null || lengths.length != state.pos.length - 1)
        {
            return;
        }

        if (state.lastAge == Integer.MIN_VALUE)
        {
            state.anchor.set(newAnchor);
            state.anchorRotation.set(newAnchorRotation);
            state.anchorVelocity.set(0F, 0F, 0F);

            state.pos[0].set(newAnchor);
            state.prev[0].set(newAnchor);

            for (int i = 1; i < chainFrames.size(); i++)
            {
                Vector3f p = chainFrames.get(i).position();
                state.pos[i].set(p);
                state.prev[i].set(p);
            }

            Vector3f tipDir;

            if (chainFrames.size() >= 2)
            {
                tipDir = new Vector3f(state.pos[chainFrames.size() - 1]).sub(state.pos[chainFrames.size() - 2]);

                if (tipDir.lengthSquared() < EPS * EPS)
                {
                    tipDir.set(0F, -1F, 0F);
                }
                else
                {
                    tipDir.normalize();
                }
            }
            else
            {
                tipDir = new Vector3f(0F, -1F, 0F);
            }

            state.pos[state.pos.length - 1].set(state.pos[chainFrames.size() - 1]).add(tipDir.mul(lengths[lengths.length - 1]));
            state.prev[state.prev.length - 1].set(state.pos[state.pos.length - 1]);

            state.lastAge = age;
            return;
        }

        int dt = age - state.lastAge;

        if (dt <= 0)
        {
            return;
        }

        if (dt > 10)
        {
            dt = 10;
            state.lastAge = age - dt;
        }

        float gravity = BASE_GRAVITY * chain.gravity();
        float damping = clamp01(chain.damping());
        int iterations = chain.iterations();

        Vector3f oldAnchorTick = new Vector3f(state.anchor);
        Vector3f velAnchor = new Vector3f(newAnchor).sub(oldAnchorTick).mul(1F / dt);
        Vector3f accelAnchor = new Vector3f(velAnchor).sub(state.anchorVelocity);
        state.anchorVelocity.set(velAnchor);

        for (int t = 0; t < dt; t++)
        {
            Vector3f oldAnchor = new Vector3f(state.anchor);
            Quaternionf dq = new Quaternionf(newAnchorRotation).mul(new Quaternionf(state.anchorRotation).invert()).normalize();
            Quaternionf dqPos = new Quaternionf().identity().slerp(dq, ANCHOR_ROTATION_POS_FOLLOW);
            Quaternionf dqPrev = new Quaternionf().identity().slerp(dq, ANCHOR_ROTATION_PREV_FOLLOW);

            for (int i = 1; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                Vector3f rel = new Vector3f(p).sub(oldAnchor);
                dqPos.transform(rel);
                p.set(newAnchor).add(rel);

                Vector3f prev = state.prev[i];
                Vector3f relPrev = new Vector3f(prev).sub(oldAnchor);
                dqPrev.transform(relPrev);
                prev.set(newAnchor).add(relPrev);
            }

            state.anchor.set(newAnchor);
            state.anchorRotation.set(newAnchorRotation);
            state.pos[0].set(newAnchor);
            state.prev[0].set(newAnchor);

            for (int i = 1; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                Vector3f prev = state.prev[i];

                Vector3f vel = new Vector3f(p).sub(prev).mul(1F - damping);

                prev.set(p);
                p.add(vel);
                if (t == 0 && ANCHOR_TRANSLATION_INERTIA > 0F)
                {
                    p.x -= accelAnchor.x * ANCHOR_TRANSLATION_INERTIA;
                    p.y -= accelAnchor.y * ANCHOR_TRANSLATION_INERTIA;
                    p.z -= accelAnchor.z * ANCHOR_TRANSLATION_INERTIA;
                }
                p.y -= gravity;
            }

            for (int iter = 0; iter < iterations; iter++)
            {
                for (int i = state.pos.length - 2; i >= 0; i--)
                {
                    Vector3f a = state.pos[i];
                    Vector3f b = state.pos[i + 1];

                    Vector3f dir = new Vector3f(a).sub(b);
                    float lenSq = dir.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    dir.mul((float) (lengths[i] / Math.sqrt(lenSq)));
                    a.set(b).add(dir);
                }

                state.pos[0].set(state.anchor);

                for (int i = 1; i < state.pos.length; i++)
                {
                    Vector3f a = state.pos[i - 1];
                    Vector3f b = state.pos[i];

                    Vector3f dir = new Vector3f(b).sub(a);
                    float lenSq = dir.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    dir.mul((float) (lengths[i - 1] / Math.sqrt(lenSq)));
                    b.set(a).add(dir);
                }

                if (constraints != null && !constraints.isEmpty())
                {
                    applyAngleConstraints(model, ids, state.pos, lengths, constraints, chainFrames.get(0).parentRotation());
                    state.pos[0].set(state.anchor);
                }
            }

            state.lastAge++;
        }
    }

    private static void applyAngleConstraints(Model model, List<String> ids, Vector3f[] pos, float[] lengths, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Quaternionf rootParentRotation)
    {
        int boneCount = ids.size();

        if (boneCount == 0 || pos == null || pos.length < 2 || lengths == null || lengths.length < 1 || rootParentRotation == null)
        {
            return;
        }

        Quaternionf parentWorld = new Quaternionf(rootParentRotation);

        for (int i = 0; i < boneCount; i++)
        {
            String boneId = ids.get(i);
            ModelConstraintsConfig.BoneConstraint c = boneId == null ? null : constraints.get(boneId);

            ModelGroup bone = model.getGroup(boneId);
            ModelGroup child = i + 1 < boneCount ? model.getGroup(ids.get(i + 1)) : null;

            if (bone == null)
            {
                return;
            }

            Vector3f restDirLocal;

            if (child != null)
            {
                restDirLocal = new Vector3f(child.initial.translate).sub(bone.initial.translate).mul(1.0f / 16.0f);
            }
            else if (bone.children != null && !bone.children.isEmpty())
            {
                ModelGroup firstChild = bone.children.get(0);
                restDirLocal = new Vector3f(firstChild.initial.translate).sub(bone.initial.translate).mul(1.0f / 16.0f);
            }
            else
            {
                restDirLocal = new Vector3f(0F, -1F, 0F);
            }

            Vector3f desiredDirWorld = new Vector3f(pos[i + 1]).sub(pos[i]);

            if (restDirLocal.lengthSquared() < EPS * EPS || desiredDirWorld.lengthSquared() < EPS * EPS)
            {
                continue;
            }

            restDirLocal.normalize();
            desiredDirWorld.normalize();

            Quaternionf invParent = new Quaternionf(parentWorld).invert();
            Vector3f desiredDirLocal = new Vector3f(desiredDirWorld);
            invParent.transform(desiredDirLocal);

            if (desiredDirLocal.lengthSquared() < EPS * EPS)
            {
                continue;
            }

            desiredDirLocal.normalize();

            Quaternionf localRot = Matrices.fromToMirroredX(restDirLocal, desiredDirLocal);
            Vector3f eulerDeg = Matrices.toEulerZYXDegrees(localRot);

            Quaternionf applied = localRot;

            if (c != null && c.enabled())
            {
                float minX = c.minX();
                float minY = c.minY();
                float minZ = c.minZ();
                float maxX = c.maxX();
                float maxY = c.maxY();
                float maxZ = c.maxZ();

                if (minX > maxX)
                {
                    float t = minX;
                    minX = maxX;
                    maxX = t;
                }

                if (minY > maxY)
                {
                    float t = minY;
                    minY = maxY;
                    maxY = t;
                }

                if (minZ > maxZ)
                {
                    float t = minZ;
                    minZ = maxZ;
                    maxZ = t;
                }

                eulerDeg.x = eulerDeg.x < minX ? minX : Math.min(eulerDeg.x, maxX);
                eulerDeg.y = eulerDeg.y < minY ? minY : Math.min(eulerDeg.y, maxY);
                eulerDeg.z = eulerDeg.z < minZ ? minZ : Math.min(eulerDeg.z, maxZ);

                applied = Matrices.toQuaternionZYXDegrees(eulerDeg.x, eulerDeg.y, eulerDeg.z);
                Vector3f dirLocal = new Vector3f(restDirLocal);
                applied.transform(dirLocal);
                parentWorld.transform(dirLocal);

                if (dirLocal.lengthSquared() >= EPS * EPS)
                {
                    dirLocal.normalize().mul(lengths[i]);
                    pos[i + 1].set(pos[i]).add(dirLocal);
                }
            }

            parentWorld.mul(applied);
        }
    }

    private static float clamp01(float v)
    {
        if (v < 0F)
        {
            return 0F;
        }

        return v > 1F ? 1F : v;
    }
}
