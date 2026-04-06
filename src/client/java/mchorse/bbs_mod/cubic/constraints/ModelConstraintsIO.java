package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public final class ModelConstraintsIO
{
    private static final float DEFAULT_MIN = -180F;
    private static final float DEFAULT_MAX = 180F;

    private ModelConstraintsIO()
    {
    }

    public static ModelConstraintsConfig read(String modelId)
    {
        File file = getFile(modelId);

        if (file == null || !file.exists())
        {
            return null;
        }

        MapType root = readMap(file);

        if (root == null || !root.has("bones", BaseType.TYPE_MAP))
        {
            return null;
        }

        MapType bones = root.getMap("bones");
        Map<String, ModelConstraintsConfig.BoneConstraint> out = new HashMap<>();

        for (String key : bones.keys())
        {
            if (!bones.has(key, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = bones.getMap(key);
            boolean enabled = entry.getBool("enabled", true);

            if (!enabled)
            {
                continue;
            }

            float minX = DEFAULT_MIN;
            float minY = DEFAULT_MIN;
            float minZ = DEFAULT_MIN;
            float maxX = DEFAULT_MAX;
            float maxY = DEFAULT_MAX;
            float maxZ = DEFAULT_MAX;

            if (entry.has("min", BaseType.TYPE_LIST))
            {
                ListType list = entry.getList("min");
                minX = getFloat(list, 0, minX);
                minY = getFloat(list, 1, minY);
                minZ = getFloat(list, 2, minZ);
            }

            if (entry.has("max", BaseType.TYPE_LIST))
            {
                ListType list = entry.getList("max");
                maxX = getFloat(list, 0, maxX);
                maxY = getFloat(list, 1, maxY);
                maxZ = getFloat(list, 2, maxZ);
            }

            out.put(key, new ModelConstraintsConfig.BoneConstraint(true, minX, minY, minZ, maxX, maxY, maxZ));
        }

        return out.isEmpty() ? null : new ModelConstraintsConfig(out);
    }

    public static boolean write(String modelId, ModelConstraintsConfig config)
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
            for (Map.Entry<String, ModelConstraintsConfig.BoneConstraint> entry : config.bones().entrySet())
            {
                String bone = entry.getKey();
                ModelConstraintsConfig.BoneConstraint c = entry.getValue();

                if (bone == null || bone.isEmpty() || c == null || !c.enabled())
                {
                    continue;
                }

                MapType map = new MapType();
                map.putBool("enabled", true);

                ListType min = new ListType();
                min.addFloat(c.minX());
                min.addFloat(c.minY());
                min.addFloat(c.minZ());

                ListType max = new ListType();
                max.addFloat(c.maxX());
                max.addFloat(c.maxY());
                max.addFloat(c.maxZ());

                map.put("min", min);
                map.put("max", max);

                bones.put(bone, map);
            }
        }

        root.put("bones", bones);

        return DataToString.writeSilently(file, root, true);
    }

    private static float getFloat(ListType list, int index, float def)
    {
        BaseType element = list == null ? null : list.get(index);

        if (BaseType.isNumeric(element))
        {
            return element.asNumeric().floatValue();
        }

        return def;
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

    private static File getFile(String modelId)
    {
        return modelId == null ? null : BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelId + "/constraints.json");
    }
}
