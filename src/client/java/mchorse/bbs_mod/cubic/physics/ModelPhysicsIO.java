package mchorse.bbs_mod.cubic.physics;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class ModelPhysicsIO
{
    private static final float DEFAULT_GRAVITY = 1F;
    private static final float DEFAULT_DAMPING = 0.15F;
    private static final int DEFAULT_ITERATIONS = 4;

    private ModelPhysicsIO()
    {
    }

    public static File getFile(String modelId)
    {
        if (modelId == null || modelId.isEmpty())
        {
            return null;
        }

        return BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelId + "/physics.json");
    }

    public static ModelPhysicsConfig read(String modelId)
    {
        File file = getFile(modelId);

        if (file == null || !file.exists())
        {
            return null;
        }

        MapType map = readMap(file);

        if (map == null || !map.has("bones", BaseType.TYPE_MAP))
        {
            return null;
        }

        MapType bones = map.getMap("bones");
        Map<String, ModelPhysicsConfig.Bone> out = new HashMap<>();

        for (String root : bones.keys())
        {
            if (!bones.has(root, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = bones.getMap(root);
            String end = entry.getString("end");
            String targetBone = entry.getString("target_bone", "");

            if (root == null || root.isEmpty() || end == null || end.isEmpty())
            {
                continue;
            }

            float gravity = entry.getFloat("gravity", DEFAULT_GRAVITY);
            float damping = entry.getFloat("damping", DEFAULT_DAMPING);
            int iterations = entry.getInt("iterations", DEFAULT_ITERATIONS);

            if (iterations < 1)
            {
                iterations = 1;
            }

            out.put(root, new ModelPhysicsConfig.Bone(end, targetBone, gravity, damping, iterations));
        }

        return out.isEmpty() ? null : new ModelPhysicsConfig(out);
    }

    public static boolean write(String modelId, ModelPhysicsConfig config)
    {
        File file = getFile(modelId);

        if (file == null)
        {
            return false;
        }

        file.getParentFile().mkdirs();

        MapType root = new MapType();
        MapType bones = new MapType();

        if (config != null && config.bones() != null)
        {
            for (Map.Entry<String, ModelPhysicsConfig.Bone> entry : config.bones().entrySet())
            {
                String rootId = entry.getKey();
                ModelPhysicsConfig.Bone bone = entry.getValue();

                if (rootId == null || rootId.isEmpty() || bone == null || bone.end() == null || bone.end().isEmpty())
                {
                    continue;
                }

                MapType map = new MapType();
                map.putString("end", bone.end());

                if (bone.targetBone() != null && !bone.targetBone().isEmpty())
                {
                    map.putString("target_bone", bone.targetBone());
                }

                map.putFloat("gravity", bone.gravity());
                map.putFloat("damping", bone.damping());
                map.putInt("iterations", Math.max(1, bone.iterations()));
                bones.put(rootId, map);
            }
        }

        root.put("bones", bones);

        return DataToString.writeSilently(file, root, true);
    }

    private static MapType readMap(File file)
    {
        try
        {
            BaseType parsed = DataToString.fromString(IOUtils.readText(file));

            if (parsed instanceof MapType map)
            {
                return map;
            }
        }
        catch (Exception e)
        {}

        return null;
    }
}
