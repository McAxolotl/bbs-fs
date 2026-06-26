package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.IModel;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsConfig;
import mchorse.bbs_mod.cubic.constraints.ModelConstraintsRuntime;
import mchorse.bbs_mod.cubic.render.CubicRenderer.PivotFrame;
import mchorse.bbs_mod.cubic.render.ModelPivotFrames;
import mchorse.bbs_mod.cubic.render.ModelRotationBlender;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.ModelForm;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Orchestrates bone physics: owns the per-entity simulation state, decides what to simulate and when
 * (forward stepping, scrub/jump re-simulation), and feeds each chain to the {@link ChainSolver}. The
 * solver itself ({@link ChainSolver}, {@link ChainState}, {@link PhysicsForces}) holds the maths.
 */
public final class ModelPhysicsRuntime
{
    static final class InstanceState
    {
        public final Map<String, ChainState> chains = new HashMap<>();

        /** Last film tick this instance simulated, to detect a scrub/jump that needs re-simulation. */
        public int lastTick = Integer.MIN_VALUE;
    }

    private static final WeakHashMap<IEntity, Map<String, InstanceState>> STATES = new WeakHashMap<>();

    /**
     * Poses a model at an arbitrary integer tick off the render loop, so the solver can be replayed
     * deterministically across a scrub or jump. Supplied by the film controller; null in non-film
     * contexts (world, UI preview), where the chain simply re-seeds at the pose on a jump instead.
     */
    public interface PoseSampler
    {
        /**
         * Pose the instance's model at integer {@code tick} (animation, IK, and the film's per-tick
         * property/target overrides) and return the entity's world baseTransform at that tick, or null
         * if it cannot be sampled.
         */
        Matrix4f sample(IEntity entity, ModelInstance instance, int tick);
    }

    private static PoseSampler sampler;

    public static void setSampler(PoseSampler poseSampler)
    {
        sampler = poseSampler;
    }

    /**
     * Ticks re-simulated before the target on a scrub/jump. The window seeds at the pose at its start with
     * zero velocity; the seed error decays through damping well before the target, so a fixed window is
     * enough to land on the settled shape without replaying the whole timeline from the start.
     */
    private static final int RESIM_WINDOW = 60;

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
        if (entity == null || instance == null || instance.model == null)
        {
            return;
        }

        IModel model = instance.model;

        ModelPhysicsCache.Compiled compiled = null;
        if (instance.form instanceof ModelForm modelForm && modelForm.physics.get() instanceof MapType map)
        {
            compiled = ModelPhysicsCache.getFromData(model, map);
        }

        if (compiled == null || compiled.chains() == null || compiled.chains().isEmpty())
        {
            return;
        }

        Map<String, ModelConstraintsConfig.BoneConstraint> constraints = ModelConstraintsRuntime.getBones(instance);

        Map<String, InstanceState> byModel = STATES.computeIfAbsent(entity, (e) -> new HashMap<>());
        InstanceState state = byModel.computeIfAbsent(instance.id, (k) -> new InstanceState());

        int tick = entity.getAge();

        /* A scrub or long jump (target tick before the last one, or far ahead): replay the simulation
         * deterministically over a window ending at the target tick, re-posing the model at each step
         * through the sampler, so the chain shows its settled shape for the target tick instead of a snap.
         * Forward play (small delta) skips this and steps from the live render pose. */
        if (sampler != null && state.lastTick != Integer.MIN_VALUE && (tick < state.lastTick || tick - state.lastTick > ChainSolver.MAX_TICK_CATCHUP))
        {
            resimulate(entity, model, instance, compiled.chains(), constraints, state, tick);
        }

        applyCompiled(entity.getWorld(), tick, transition, model, instance, compiled.chains(), constraints, state, baseTransform, false);
        state.lastTick = tick;
    }

    /**
     * Replays the solver from {@link #RESIM_WINDOW} ticks before the target up to it, re-posing the model
     * at each tick via the sampler so the result reflects the current animation (not a stale cache). The
     * window is seeded at the pose at its start with zero velocity; damping erases that seed error before
     * the target. Leaves every chain stepped up to {@code targetTick}, ready for the normal render pass.
     */
    private static void resimulate(IEntity entity, IModel model, ModelInstance instance, List<ModelPhysicsCache.CompiledChain> chains, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, InstanceState state, int targetTick)
    {
        int start = Math.max(0, targetTick - RESIM_WINDOW);

        Matrix4f baseTransform = sampler.sample(entity, instance, start);

        if (baseTransform == null)
        {
            return; // can't sample — let the normal pass re-seed at the current pose
        }

        /* Force every existing chain to re-seed at the window's start pose. */
        for (ChainState chainState : state.chains.values())
        {
            chainState.lastAge = Integer.MIN_VALUE;
        }

        World world = entity.getWorld();

        applyCompiled(world, start, 0F, model, instance, chains, constraints, state, baseTransform, true);

        for (int t = start + 1; t <= targetTick; t++)
        {
            baseTransform = sampler.sample(entity, instance, t);

            if (baseTransform == null)
            {
                break;
            }

            applyCompiled(world, t, 0F, model, instance, chains, constraints, state, baseTransform, true);
        }
    }

    private static void applyCompiled(World world, int age, float transition, IModel model, ModelInstance instance, List<ModelPhysicsCache.CompiledChain> compiledChains, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, InstanceState state, Matrix4f baseTransform, boolean simulateOnly)
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
            Iterator<String> it = state.chains.keySet().iterator();

            while (it.hasNext())
            {
                if (!chainIds.contains(it.next()))
                {
                    it.remove();
                }
            }
        }

        Map<String, PivotFrame> frames = new HashMap<>(wanted.size() * 2);
        ModelPivotFrames.collect(model, wanted, frames, baseTransform);

        for (ModelPhysicsCache.CompiledChain chain : compiledChains)
        {
            applyChain(world, age, transition, model, instance, chain, constraints, frames, state, simulateOnly);
        }
    }

    private static void applyChain(World world, int age, float transition, IModel model, ModelInstance instance, ModelPhysicsCache.CompiledChain chain, Map<String, ModelConstraintsConfig.BoneConstraint> constraints, Map<String, PivotFrame> frames, InstanceState instanceState, boolean simulateOnly)
    {
        List<String> ids = chain.chainRootToEnd();
        int pivotCount = ids.size();
        int pointCount = pivotCount + 1;

        if (pivotCount < 1)
        {
            return;
        }

        /* The film physics track layers a per-chain control over the config, keyed by the chain's
         * root bone, replacing its dynamic scalars wholesale (mirrors the IK track). */
        PhysicsControl control = null;

        if (instance != null && instance.form instanceof ModelForm modelForm && !modelForm.physicsControlOverrides.isEmpty())
        {
            control = modelForm.physicsControlOverrides.get(ids.get(0));
        }

        if (control != null && !control.enabled)
        {
            return;
        }

        float weight = control != null ? control.weight : chain.weight();

        if (weight <= 0F)
        {
            return;
        }

        float gravity = control != null ? control.gravity : chain.gravity();
        float damping = control != null ? control.damping : chain.damping();
        float stiffness = control != null ? control.stiffness : chain.stiffness();

        ChainState state = instanceState.chains.computeIfAbsent(chain.id(), (k) -> new ChainState());

        if (state.pos == null || state.pos.length != pointCount)
        {
            state.pos = new Vector3f[pointCount];
            state.prev = new Vector3f[pointCount];
            state.settled = new Vector3f[pointCount];
            state.settledPrev = new Vector3f[pointCount];
            state.render = new Vector3f[pointCount];
            state.poseLocal = new Vector3f[pointCount];

            for (int i = 0; i < pointCount; i++)
            {
                state.pos[i] = new Vector3f();
                state.prev[i] = new Vector3f();
                state.settled[i] = new Vector3f();
                state.settledPrev[i] = new Vector3f();
                state.render[i] = new Vector3f();
                state.poseLocal[i] = new Vector3f();
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
        if (instance != null && instance.form instanceof ModelForm modelForm)
        {
            String rootBone = ids.get(0);
            Vector3f worldPos = modelForm.physicsTargetOverrides.get(rootBone);

            if (worldPos != null)
            {
                float targetWeight = modelForm.physicsTargetWeights.getOrDefault(rootBone, 1F);

                if (targetWeight >= 1F)
                {
                    target = new Vector3f(worldPos);
                }
                else if (targetWeight > 0F)
                {
                    /* The binding is fading in or out (it crossed a no-target keyframe). Easing the pin point
                     * from the chain's current tip toward the full target by the fade amount lets the chain
                     * travel there smoothly instead of snapping, with no soft-target mode in the solver. */
                    Vector3f tip = state.pos[state.pos.length - 1];

                    target = state.lastAge == Integer.MIN_VALUE
                        ? new Vector3f(worldPos)
                        : new Vector3f(tip).lerp(worldPos, targetWeight);
                }
                /* targetWeight <= 0: fully faded out — leave the chain free this frame. */
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

        ChainSolver.computePoseTargets(model, ids, chainFrames, chain.restLengths(), anchor, anchorRotation, target != null, state);
        ChainSolver.step(world, age, transition, model, ids, chain, gravity, damping, stiffness, constraints, anchor, anchorRotation, chainFrames.get(0).parentRotation(), target, chainFrames, state);

        if (simulateOnly)
        {
            return; // re-simulation pass: advance the state only, the final render pass writes the rotations
        }

        Vector3f[] positions = ChainSolver.renderInterpolate(state, state.renderAlpha, anchor, anchorRotation, target);
        ModelRotationBlender.applyWeightedRotations(model, chainFrames.get(0).parentRotation(), ids, positions, weight);
    }
}
