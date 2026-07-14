package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.utils.colors.Color;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import net.minecraft.client.model.ModelPart;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
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
    private final List<VanillaBoneHierarchy.Hierarchy> hierarchies;
    private final IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> bones;
    private final IdentityHashMap<ModelPart, PoseTransform> transforms;
    private final IdentityHashMap<ModelPart, Matrix4f> origins = new IdentityHashMap<>();
    private final IdentityHashMap<ModelPart, Integer> pickingOffsets = new IdentityHashMap<>();
    private final Map<String, Integer> pickingIds = new HashMap<>();
    private final MatrixCache matrices = new MatrixCache();
    private final List<String> pickedBoneIds = new ArrayList<>();
    private final Color color;
    private final Matrix4f inverseBase;
    private final boolean picking;
    private final boolean incrementPicking;
    private boolean closed;

    private MobRenderContext(Object renderer, Pose pose, Pose overlay, Color color, Matrix4f base, boolean picking, boolean incrementPicking)
    {
        this.previous = CURRENT.get();
        VanillaRendererBones.Discovery discovery = VanillaRendererBones.discover(renderer);
        Pose mergedPose = mergePose(pose, overlay);

        this.hierarchies = discovery.getRuntimeHierarchies();
        this.bones = resolveBones(this.hierarchies);
        discovery.getBoneHierarchy().migratePose(mergedPose);
        this.transforms = resolveTransforms(discovery, mergedPose);
        this.color = color == null ? Color.white() : color.copy();
        this.inverseBase = base == null || Math.abs(base.determinant()) < 1.0E-8F
            ? null
            : new Matrix4f(base).invert();
        this.picking = picking;
        this.incrementPicking = incrementPicking;

        CURRENT.set(this);
    }

    public static MobRenderContext push(Object renderer, Pose pose, Pose overlay)
    {
        return push(renderer, pose, overlay, Color.white());
    }

    public static MobRenderContext push(Object renderer, Pose pose, Pose overlay, Color color)
    {
        return new MobRenderContext(renderer, pose, overlay, color, null, false, false);
    }

    public static MobRenderContext push(Object renderer, Pose pose, Pose overlay, Color color, Matrix4f base, boolean picking, boolean incrementPicking)
    {
        return new MobRenderContext(renderer, pose, overlay, color, base, picking, incrementPicking);
    }

    public static PoseTransform getTransform(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        return context == null ? null : context.transforms.get(part);
    }

    public static boolean isActive()
    {
        return CURRENT.get() != null;
    }

    public static Color getColor(ModelPart part)
    {
        MobRenderContext context = CURRENT.get();

        if (context == null)
        {
            return null;
        }

        if (context.bones.containsKey(part))
        {
            return context.color;
        }

        VanillaBoneHierarchy.Hierarchy hierarchy = VanillaBoneHierarchy.getHierarchy(part).orElse(null);

        return hierarchy == null || !context.hierarchies.contains(hierarchy) ? null : context.color;
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
            if (!context.matrices.has(bone.getId()))
            {
                context.matrices.put(bone.getId(), context.toLocal(matrix), origin);
            }
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
            offset = context.pickingIds.get(bone.getId());

            if (offset == null)
            {
                offset = context.pickedBoneIds.size() + 1;
                context.pickingIds.put(bone.getId(), offset);
                context.pickedBoneIds.add(bone.getId());
            }

            context.pickingOffsets.put(part, offset);
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

    private static IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> resolveBones(List<VanillaBoneHierarchy.Hierarchy> hierarchies)
    {
        IdentityHashMap<ModelPart, VanillaBoneHierarchy.Bone> bones = new IdentityHashMap<>();

        for (VanillaBoneHierarchy.Hierarchy hierarchy : hierarchies)
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
            for (VanillaBoneHierarchy.Bone bone : discovery.resolveAll(entry.getKey()))
            {
                ModelPart part = bone.getPart();

                if (part != null)
                {
                    transforms.put(part, entry.getValue());
                }
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
