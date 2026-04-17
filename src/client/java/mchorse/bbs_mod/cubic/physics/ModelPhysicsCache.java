package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.joml.Matrices;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

final class ModelPhysicsCache
{
    public static final class CompiledChain
    {
        private final String id;
        private final String attach;
        private final String targetBone;
        private final List<String> chainRootToEnd;
        private final float[] restLengths;
        private final float gravity;
        private final float damping;
        private final int iterations;
        private final boolean relativeGravity;
        private final boolean hasGravityRotation;
        private final Quaternionf gravityRotation;
        private final boolean collisions;
        private final float radius;
        private final float weight;

        public CompiledChain(String id, String attach, String targetBone, List<String> chainRootToEnd, float[] restLengths, ModelPhysicsConfig.Bone bone)
        {
            this.id = id;
            this.attach = attach;
            this.targetBone = targetBone;
            this.chainRootToEnd = chainRootToEnd;
            this.restLengths = restLengths;
            this.gravity = bone.gravity();
            this.damping = bone.damping();
            this.iterations = bone.iterations();
            this.relativeGravity = bone.relativeGravity();
            this.hasGravityRotation = bone.hasRelativeGravityRotation();
            this.gravityRotation = this.hasGravityRotation
                ? Matrices.toQuaternionZYXDegrees(bone.relativeGravityRotateX(), bone.relativeGravityRotateY(), bone.relativeGravityRotateZ())
                : new Quaternionf();
            this.collisions = bone.collisions();
            this.radius = bone.radius();
            this.weight = bone.weight();
        }

        public String id()
        {
            return this.id;
        }

        public String attach()
        {
            return this.attach;
        }

        public String targetBone()
        {
            return this.targetBone;
        }

        public List<String> chainRootToEnd()
        {
            return this.chainRootToEnd;
        }

        public float[] restLengths()
        {
            return this.restLengths;
        }

        public float gravity()
        {
            return this.gravity;
        }

        public float damping()
        {
            return this.damping;
        }

        public int iterations()
        {
            return this.iterations;
        }

        public boolean relativeGravity()
        {
            return this.relativeGravity;
        }

        public boolean hasGravityRotation()
        {
            return this.hasGravityRotation;
        }

        public void applyGravityRotation(Vector3f direction)
        {
            if (this.hasGravityRotation)
            {
                this.gravityRotation.transform(direction);
            }
        }

        public boolean collisions()
        {
            return this.collisions;
        }

        public float radius()
        {
            return this.radius;
        }

        public float weight()
        {
            return this.weight;
        }
    }

    public record Compiled(List<CompiledChain> chains)
    {
    }

    private static final WeakHashMap<MapType, EmbeddedCompiled> EMBEDDED = new WeakHashMap<>();

    private record EmbeddedCompiled(Model model, List<CompiledChain> chains)
    {
    }

    private ModelPhysicsCache()
    {
    }

    public static void clear()
    {
        EMBEDDED.clear();
    }

    public static Compiled getFromData(Model model, MapType data)
    {
        if (model == null || data == null)
        {
            return null;
        }

        EmbeddedCompiled cached = EMBEDDED.get(data);

        if (cached != null && cached.model == model)
        {
            return new Compiled(cached.chains);
        }

        ModelPhysicsConfig config = ModelPhysicsIO.fromData(data);
        List<CompiledChain> compiled = compile(model, config);

        EmbeddedCompiled next = new EmbeddedCompiled(model, compiled);
        EMBEDDED.put(data, next);

        return new Compiled(compiled);
    }

    private static List<CompiledChain> compile(Model model, ModelPhysicsConfig config)
    {
        if (config == null || config.bones() == null || config.bones().isEmpty())
        {
            return java.util.Collections.emptyList();
        }

        List<CompiledChain> out = new ArrayList<>();

        List<String> roots = new ArrayList<>(config.bones().keySet());
        java.util.Collections.sort(roots);

        for (String rootId : roots)
        {
            ModelPhysicsConfig.Bone chain = config.bones().get(rootId);

            if (chain == null)
            {
                continue;
            }

            if (chain.weight() <= 0F)
            {
                continue;
            }

            String endId = chain.end();

            ModelGroup root = model.getGroup(rootId);
            ModelGroup end = model.getGroup(endId);

            if (root == null || end == null)
            {
                continue;
            }

            List<String> ids = buildChainIds(end, root);

            if (ids.isEmpty())
            {
                continue;
            }

            float[] lengths = computeRestLengths(model, ids);

            if (lengths == null)
            {
                continue;
            }

            String attach = rootId;

            String id = rootId + ":" + endId;
            out.add(new CompiledChain(id, attach, chain.targetBone(), ids, lengths, chain));
        }

        return out;
    }

    private static List<String> buildChainIds(ModelGroup end, ModelGroup root)
    {
        List<String> list = new ArrayList<>();
        ModelGroup group = end;

        while (group != null)
        {
            list.add(group.id);

            if (group == root)
            {
                java.util.Collections.reverse(list);
                return list;
            }

            group = group.parent;
        }

        return java.util.Collections.emptyList();
    }

    private static float[] computeRestLengths(Model model, List<String> ids)
    {
        int n = ids.size();

        float[] lengths = new float[n];

        if (n == 1)
        {
            ModelGroup bone = model.getGroup(ids.get(0));

            if (bone == null)
            {
                return null;
            }

            float len = 0.25F;

            if (bone.children != null && !bone.children.isEmpty())
            {
                ModelGroup child = bone.children.get(0);
                float dx = (child.initial.translate.x - bone.initial.translate.x) / 16F;
                float dy = (child.initial.translate.y - bone.initial.translate.y) / 16F;
                float dz = (child.initial.translate.z - bone.initial.translate.z) / 16F;
                len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            }

            if (len <= 1.0e-6f)
            {
                len = 1.0e-6f;
            }

            lengths[0] = len;

            return lengths;
        }

        for (int i = 0; i < n - 1; i++)
        {
            ModelGroup a = model.getGroup(ids.get(i));
            ModelGroup b = model.getGroup(ids.get(i + 1));

            if (a == null || b == null)
            {
                return null;
            }

            float dx = (b.initial.translate.x - a.initial.translate.x) / 16F;
            float dy = (b.initial.translate.y - a.initial.translate.y) / 16F;
            float dz = (b.initial.translate.z - a.initial.translate.z) / 16F;

            lengths[i] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);

            if (lengths[i] <= 1.0e-6f)
            {
                lengths[i] = 1.0e-6f;
            }
        }

        lengths[n - 1] = lengths[n - 2];

        return lengths;
    }
}
