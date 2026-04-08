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
    private static final float ANCHOR_TRANSLATION_DRAG = 0.15F;

    /* Temporary pool to avoid allocations */
    private static final Vector3f V1 = new Vector3f();
    private static final Vector3f V2 = new Vector3f();
    private static final Vector3f V3 = new Vector3f();
    private static final Vector3f V4 = new Vector3f();
    private static final Vector3f V5 = new Vector3f();
    private static final Vector3f V6 = new Vector3f();
    private static final Vector3f TARGET = new Vector3f();
    private static final Quaternionf Q1 = new Quaternionf();
    private static final Quaternionf Q2 = new Quaternionf();
    private static final Quaternionf Q3 = new Quaternionf();
    private static final Matrix4f M1 = new Matrix4f();

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

        applyCompiled(entity.getAge(), transition, model, instance, compiled.chains(), constraints, state);
    }

    private static void applyCompiled(int age, float transition, Model model, ModelInstance instance, List<ModelPhysicsCache.CompiledChain> compiledChains, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, InstanceState state)
    {
        Set<String> wanted = new HashSet<>();
        Set<String> chainIds = new HashSet<>();

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            chainIds.add(chain.id());
            wanted.addAll(chain.chainRootToEnd());

            if (chain.targetBone() != null && !chain.targetBone().isEmpty())
            {
                wanted.add(chain.targetBone());
            }
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
        CubicRenderer.collectPivotFrames(model, wanted, frames);

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            applyChain(age, transition, model, instance, chain, constraints, frames, state);
        }
    }

    private static void applyChain(int age, float transition, Model model, ModelInstance instance, ModelPhysicsCache.CompiledChain chain, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Map<String, PivotFrame> frames, InstanceState instanceState)
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

        Vector3f target = null;
        if (instance != null && instance.form instanceof mchorse.bbs_mod.forms.forms.ModelForm modelForm)
        {
            String rootBone = ids.get(0);
            Vector3f worldPos = modelForm.physicsTargetOverrides.get(rootBone);

            if (worldPos != null)
            {
                TARGET.set(worldPos);
                if (instance.lastBaseTransform != null)
                {
                    M1.set(instance.lastBaseTransform).invert().transformPosition(TARGET);
                }
                target = TARGET;
            }
        }

        if (target != null)
        {
            if (state.lastAge == Integer.MIN_VALUE)
            {
                state.pos[state.pos.length - 1].set(target);
                state.prev[state.pos.length - 1].set(target);
            }
        }
        else if (chain.targetBone() != null && !chain.targetBone().isEmpty())
        {
            PivotFrame targetFrame = frames.get(chain.targetBone());
            if (targetFrame != null)
            {
                target = targetFrame.position();
                if (state.lastAge == Integer.MIN_VALUE)
                {
                    state.pos[state.pos.length - 1].set(target);
                    state.prev[state.pos.length - 1].set(target);
                }
            }
        }

        step(age, model, ids, chain, constraints, anchor, anchorRotation, target, chainFrames, state);
        Vector3f[] positions = interpolate(state, transition);
        applyRenderAnchorFollow(state, positions, anchor, anchorRotation, target, transition);
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

    private static void applyRenderAnchorFollow(ChainState state, Vector3f[] positions, Vector3f anchorPosition, Quaternionf anchorRotation, Vector3f targetPosition, float transition)
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
        V1.set(anchorPosition).sub(oldAnchor).mul(t); // delta

        Q1.set(anchorRotation).mul(Q2.set(state.anchorRotation).invert()).normalize(); // dq
        Q3.identity().slerp(Q1, t); // dqPartial

        if (Math.abs(V1.x) < EPS && Math.abs(V1.y) < EPS && Math.abs(V1.z) < EPS && Math.abs(Q3.x) < EPS && Math.abs(Q3.y) < EPS && Math.abs(Q3.z) < EPS)
        {
            if (targetPosition != null)
            {
                positions[positions.length - 1].set(targetPosition);
            }

            return;
        }

        for (int i = 0; i < positions.length; i++)
        {
            V2.set(positions[i]).sub(oldAnchor); // rel
            Q3.transform(V2);
            positions[i].set(oldAnchor).add(V1).add(V2);
        }

        if (targetPosition != null)
        {
            positions[positions.length - 1].set(targetPosition);
        }
    }

    private static void step(int age, Model model, List<String> ids, ModelPhysicsCache.CompiledChain chain, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Vector3f anchorPosition, Quaternionf anchorRotation, Vector3f targetPosition, List<PivotFrame> chainFrames, ChainState state)
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

            if (chainFrames.size() >= 2)
            {
                V1.set(state.pos[chainFrames.size() - 1]).sub(state.pos[chainFrames.size() - 2]); // tipDir

                if (V1.lengthSquared() < EPS * EPS)
                {
                    V1.set(0F, -1F, 0F);
                }
                else
                {
                    V1.normalize();
                }
            }
            else
            {
                V1.set(0F, -1F, 0F);
            }

            state.pos[state.pos.length - 1].set(state.pos[chainFrames.size() - 1]).add(V1.mul(lengths[lengths.length - 1]));
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

        V1.set(state.anchor); // oldAnchorTick
        V2.set(newAnchor).sub(V1).mul(1F / dt); // velAnchor
        V3.set(V2).sub(state.anchorVelocity); // accelAnchor
        state.anchorVelocity.set(V2);

        for (int t = 0; t < dt; t++)
        {
            V4.set(state.anchor); // oldAnchor
            Q1.set(newAnchorRotation).mul(Q2.set(state.anchorRotation).invert()).normalize(); // dq
            Q2.identity().slerp(Q1, ANCHOR_ROTATION_POS_FOLLOW); // dqPos
            Q3.identity().slerp(Q1, ANCHOR_ROTATION_PREV_FOLLOW); // dqPrev

            for (int i = 1; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                V5.set(p).sub(V4); // rel
                Q2.transform(V5);
                p.set(newAnchor).add(V5);

                Vector3f prev = state.prev[i];
                V6.set(prev).sub(V4); // relPrev
                Q3.transform(V6);
                prev.set(newAnchor).add(V6);
            }

            state.anchor.set(newAnchor);
            state.anchorRotation.set(newAnchorRotation);
            state.pos[0].set(newAnchor);
            state.prev[0].set(newAnchor);

            for (int i = 1; i < state.pos.length; i++)
            {
                Vector3f p = state.pos[i];
                Vector3f prev = state.prev[i];

                V5.set(p).sub(prev).mul(1F - damping); // vel

                prev.set(p);
                p.add(V5);
                if (t == 0 && ANCHOR_TRANSLATION_INERTIA > 0F)
                {
                    p.x -= V3.x * ANCHOR_TRANSLATION_INERTIA;
                    p.y -= V3.y * ANCHOR_TRANSLATION_INERTIA;
                    p.z -= V3.z * ANCHOR_TRANSLATION_INERTIA;
                }
                if (t == 0 && ANCHOR_TRANSLATION_DRAG > 0F)
                {
                    p.x -= V2.x * ANCHOR_TRANSLATION_DRAG;
                    p.y -= V2.y * ANCHOR_TRANSLATION_DRAG;
                    p.z -= V2.z * ANCHOR_TRANSLATION_DRAG;
                }
                p.y -= gravity;
            }

            for (int iter = 0; iter < iterations; iter++)
            {
                /* Backward pass (from target to anchor) */
                if (targetPosition != null)
                {
                    state.pos[state.pos.length - 1].set(targetPosition);
                }

                for (int i = state.pos.length - 2; i >= 0; i--)
                {
                    Vector3f a = state.pos[i];
                    Vector3f b = state.pos[i + 1];

                    V5.set(a).sub(b); // dir
                    float lenSq = V5.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    V5.mul((float) (lengths[i] / Math.sqrt(lenSq)));
                    a.set(b).add(V5);
                }

                /* Forward pass (from anchor to target) */
                state.pos[0].set(state.anchor);

                for (int i = 1; i < state.pos.length; i++)
                {
                    Vector3f a = state.pos[i - 1];
                    Vector3f b = state.pos[i];

                    V5.set(b).sub(a); // dir
                    float lenSq = V5.lengthSquared();

                    if (lenSq < EPS * EPS)
                    {
                        continue;
                    }

                    V5.mul((float) (lengths[i - 1] / Math.sqrt(lenSq)));
                    b.set(a).add(V5);
                }

                if (targetPosition != null)
                {
                    state.pos[state.pos.length - 1].set(targetPosition);
                }

                if (constraints != null && !constraints.isEmpty())
                {
                    applyAngleConstraints(model, ids, state.pos, lengths, constraints, chainFrames.get(0).parentRotation());
                    state.pos[0].set(state.anchor);

                    if (targetPosition != null)
                    {
                        state.pos[state.pos.length - 1].set(targetPosition);
                    }
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
