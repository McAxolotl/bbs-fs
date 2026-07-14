package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.model.ModelPart;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The pose state scoped to one MobForm entity render.
 *
 * <p>Contexts restore the previous value when closed so nested entity renders cannot leak pose
 * state into their caller. The part map is identity based and lives only for the duration of the
 * render, matching the renderer/model instances discovered for that call.</p>
 */
public final class MobRenderContext implements AutoCloseable
{
    private static final ThreadLocal<MobRenderContext> CURRENT = new ThreadLocal<>();

    private final MobRenderContext previous;
    private final IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> bones;
    private final IdentityHashMap<ModelPart, PoseTransform> transforms;
    private final IdentityHashMap<ModelPart, Matrix4f> origins = new IdentityHashMap<>();
    private final IdentityHashMap<ModelPart, Integer> pickingOffsets = new IdentityHashMap<>();
    private final MatrixCache matrices = new MatrixCache();
    private final List<String> pickedBoneIds = new ArrayList<>();
    private final Matrix4f inverseBase;
    private final boolean picking;
    private final boolean incrementPicking;
    private boolean closed;

    private MobRenderContext(Object renderer, Pose pose, Pose overlay, Matrix4f base, boolean picking, boolean incrementPicking)
    {
        this.previous = CURRENT.get();
        VanillaRendererBones.Discovery discovery = VanillaRendererBones.discover(renderer);

        this.bones = resolveBones(discovery);
        this.transforms = resolveTransforms(discovery, mergePose(pose, overlay));
        this.inverseBase = base == null || Math.abs(base.determinant()) < 1.0E-8F
            ? null
            : new Matrix4f(base).invert();
        this.picking = picking;
        this.incrementPicking = incrementPicking;

        CURRENT.set(this);
    }

    public static MobRenderContext push(Object renderer, Pose pose, Pose overlay)
    {
        return new MobRenderContext(renderer, pose, overlay, null, false, false);
    }

    public static MobRenderContext push(Object renderer, Pose pose, Pose overlay, Matrix4f base, boolean picking, boolean incrementPicking)
    {
        return new MobRenderContext(renderer, pose, overlay, base, picking, incrementPicking);
    }

    public static PoseTransform getTransform(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        return context == null ? null : context.transforms.get(part);
    }

    public static boolean isTracked(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        return context != null && context.bones.containsKey(part);
    }

    public static void captureOrigin(ModelPart part, Matrix4f matrix)
    {
        MobRenderContext context = CURRENT.get();

        if (context != null && context.inverseBase != null && context.bones.containsKey(part))
        {
            context.origins.put(part, context.toLocal(matrix));
        }
    }

    public static void captureMatrix(ModelPart part, Matrix4f matrix)
    {
        MobRenderContext context = CURRENT.get();

        if (context == null || context.inverseBase == null)
        {
            return;
        }

        VanillaBoneHierarchy.Bone bone = context.bones.get(part);
        Matrix4f origin = context.origins.get(part);

        if (bone != null && origin != null)
        {
            context.matrices.put(bone.getId(), context.toLocal(matrix), origin);
        }
    }

    /**
     * Returns the lightmap-U offset consumed by the model picker shader, or {@code -1} outside a
     * picking pass. Offset zero is reserved for non-bone entity geometry.
     */
    public static int getPickingOffset(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        if (context == null || !context.picking)
        {
            return -1;
        }

        if (!context.incrementPicking)
        {
            return 0;
        }

        VanillaBoneHierarchy.Bone bone = context.bones.get(part);

        if (bone == null || part.isEmpty())
        {
            return 0;
        }

        Integer offset = context.pickingOffsets.get(part);

        if (offset == null)
        {
            offset = context.pickedBoneIds.size() + 1;
            context.pickingOffsets.put(part, offset);
            context.pickedBoneIds.add(bone.getId());
        }

        return offset;
    }

    public MatrixCache getMatrices()
    {
        return this.matrices;
    }

    public List<String> getPickedBoneIds()
    {
        return List.copyOf(this.pickedBoneIds);
    }

    private Matrix4f toLocal(Matrix4f matrix)
    {
        return new Matrix4f(this.inverseBase).mul(matrix);
    }

    private static Pose mergePose(Pose pose, Pose overlay)
    {
        Pose result = pose.copy();

        for (Map.Entry<String, PoseTransform> entry : overlay.transforms.entrySet())
        {
            PoseTransform transform = result.get(entry.getKey());
            PoseTransform value = entry.getValue();

            if (value.fix != 0F)
            {
                transform.translate.lerp(value.translate, value.fix);
                transform.scale.lerp(value.scale, value.fix);
                transform.rotate.lerp(value.rotate, value.fix);
                transform.rotate2.lerp(value.rotate2, value.fix);
            }
            else
            {
                transform.translate.add(value.translate);
                transform.scale.add(value.scale).sub(1F, 1F, 1F);
                transform.rotate.add(value.rotate);
                transform.rotate2.add(value.rotate2);
            }
        }

        return result;
    }

    private static IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> resolveBones(VanillaRendererBones.Discovery discovery)
    {
        IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> bones = new IdentityHashMap<>();

        for (VanillaBoneHierarchy.Hierarchy hierarchy : discovery.getHierarchies())
        {
            for (VanillaBoneHierarchy.Bone bone : hierarchy.getBones())
            {
                ModelPart part = bone.getPart();

                if (part != null)
                {
                    bones.put(part, bone);
                }
            }
        }

        return bones;
    }

    private static IdentityHashMap<ModelPart, PoseTransform> resolveTransforms(VanillaRendererBones.Discovery discovery, Pose pose)
    {
        IdentityHashMap<ModelPart, PoseTransform> transforms = new IdentityHashMap<>();

        for (Map.Entry<String, PoseTransform> entry : pose.transforms.entrySet())
        {
            ModelPart part = discovery.resolve(entry.getKey())
                .map(VanillaBoneHierarchy.Bone::getPart)
                .orElse(null);

            if (part != null)
            {
                transforms.put(part, entry.getValue());
            }
        }

        return transforms;
    }

    @Override
    public void close()
    {
        if (this.closed)
        {
            return;
        }

        this.closed = true;

        if (CURRENT.get() == this)
        {
            if (this.previous == null)
            {
                CURRENT.remove();
            }
            else
            {
                CURRENT.set(this.previous);
            }
        }
    }
}
