package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.IOUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ModelIKIO
{
    private ModelIKIO()
    {
    }

    public static File getFile(String modelId)
    {
        if (modelId == null || modelId.isEmpty())
        {
            return null;
        }

        return BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelId + "/ik.json");
    }

    public static ModelIKConfig read(String modelId)
    {
        File file = getFile(modelId);

        if (file == null || !file.exists())
        {
            return null;
        }

        MapType map = readMap(file);

        if (map == null || map.isEmpty())
        {
            return null;
        }

        List<ModelIKConfig.Chain> chains = new ArrayList<>();
        List<String> controllers = new ArrayList<>(map.keys());

        for (String controller : controllers)
        {
            if (!map.has(controller, BaseType.TYPE_MAP))
            {
                continue;
            }

            MapType entry = map.getMap(controller);
            String locator = entry.getString("locator");
            String root = entry.getString("root");
            boolean enabled = entry.getBool("enabled", true);
            float poleX = (float) entry.getDouble("pole_x", 0D);
            float poleY = (float) entry.getDouble("pole_y", 0D);
            float poleZ = (float) entry.getDouble("pole_z", 0D);
            ModelIKConfig.PoleSpace poleSpace = parsePoleSpace(entry.getString("pole_space", "root"));

            if (locator.isEmpty() || root.isEmpty())
            {
                continue;
            }

            chains.add(new ModelIKConfig.Chain(controller, locator, root, enabled, poleX, poleY, poleZ, poleSpace));
        }

        return chains.isEmpty() ? null : new ModelIKConfig(chains);
    }

    public static boolean write(String modelId, ModelIKConfig config)
    {
        File file = getFile(modelId);

        if (file == null)
        {
            return false;
        }

        file.getParentFile().mkdirs();

        MapType ik = new MapType();

        if (config != null && config.chains() != null)
        {
            for (ModelIKConfig.Chain chain : config.chains())
            {
                if (chain == null || chain.controller() == null || chain.controller().isEmpty())
                {
                    continue;
                }

                String locator = chain.locator();
                String root = chain.root();

                if (locator == null || locator.isEmpty() || root == null || root.isEmpty())
                {
                    continue;
                }

                MapType boneData = new MapType();
                boneData.putString("locator", locator);
                boneData.putString("root", root);
                boneData.putBool("enabled", chain.enabled());
                if (chain.poleX() != 0F) boneData.putDouble("pole_x", chain.poleX());
                if (chain.poleY() != 0F) boneData.putDouble("pole_y", chain.poleY());
                if (chain.poleZ() != 0F) boneData.putDouble("pole_z", chain.poleZ());
                if (chain.poleSpace() != null && chain.poleSpace() != ModelIKConfig.PoleSpace.ROOT)
                {
                    boneData.putString("pole_space", poleSpaceToString(chain.poleSpace()));
                }
                ik.put(chain.controller(), boneData);
            }
        }

        return DataToString.writeSilently(file, ik, true);
    }

    private static ModelIKConfig.PoleSpace parsePoleSpace(String value)
    {
        if (value == null || value.isEmpty())
        {
            return ModelIKConfig.PoleSpace.ROOT;
        }

        if ("world".equalsIgnoreCase(value))
        {
            return ModelIKConfig.PoleSpace.WORLD;
        }

        if ("controller".equalsIgnoreCase(value))
        {
            return ModelIKConfig.PoleSpace.CONTROLLER;
        }

        return ModelIKConfig.PoleSpace.ROOT;
    }

    private static String poleSpaceToString(ModelIKConfig.PoleSpace value)
    {
        if (value == null)
        {
            return "root";
        }

        return value == ModelIKConfig.PoleSpace.WORLD ? "world" : (value == ModelIKConfig.PoleSpace.CONTROLLER ? "controller" : "root");
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
