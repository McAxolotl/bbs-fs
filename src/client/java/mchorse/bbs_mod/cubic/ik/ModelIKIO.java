package mchorse.bbs_mod.cubic.ik;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

public final class ModelIKIO
{
    private static final String KEY_LOCATOR = "locator";
    private static final String KEY_ROOT = "root";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_POLE_X = "pole_x";
    private static final String KEY_POLE_Y = "pole_y";
    private static final String KEY_POLE_Z = "pole_z";
    private static final String KEY_POLE_SPACE = "pole_space";
    private static final String KEY_WEIGHT = "weight";

    private static final boolean DEFAULT_ENABLED = true;
    private static final float DEFAULT_POLE = 0F;

    private ModelIKIO()
    {
    }

    public static ModelIKConfig fromData(MapType map)
    {
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
            String locator = entry.getString(KEY_LOCATOR);
            String root = entry.getString(KEY_ROOT);
            boolean enabled = entry.getBool(KEY_ENABLED, DEFAULT_ENABLED);
            float poleX = (float) entry.getDouble(KEY_POLE_X, DEFAULT_POLE);
            float poleY = (float) entry.getDouble(KEY_POLE_Y, DEFAULT_POLE);
            float poleZ = (float) entry.getDouble(KEY_POLE_Z, DEFAULT_POLE);
            ModelIKConfig.PoleSpace poleSpace = parsePoleSpace(entry.getString(KEY_POLE_SPACE, "root"));
            float weight = (float) entry.getDouble(KEY_WEIGHT, ModelIKConfig.DEFAULT_WEIGHT);

            if (locator.isEmpty() || root.isEmpty())
            {
                continue;
            }

            chains.add(new ModelIKConfig.Chain(controller, locator, root, enabled, poleX, poleY, poleZ, poleSpace, weight));
        }

        return chains.isEmpty() ? null : new ModelIKConfig(chains);
    }

    public static MapType toData(ModelIKConfig config)
    {
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
                boneData.putString(KEY_LOCATOR, locator);
                boneData.putString(KEY_ROOT, root);
                boneData.putBool(KEY_ENABLED, chain.enabled());
                if (chain.poleX() != DEFAULT_POLE) boneData.putDouble(KEY_POLE_X, chain.poleX());
                if (chain.poleY() != DEFAULT_POLE) boneData.putDouble(KEY_POLE_Y, chain.poleY());
                if (chain.poleZ() != DEFAULT_POLE) boneData.putDouble(KEY_POLE_Z, chain.poleZ());
                if (chain.poleSpace() != null && chain.poleSpace() != ModelIKConfig.PoleSpace.ROOT)
                {
                    boneData.putString(KEY_POLE_SPACE, poleSpaceToString(chain.poleSpace()));
                }

                if (chain.weight() != ModelIKConfig.DEFAULT_WEIGHT)
                {
                    boneData.putDouble(KEY_WEIGHT, chain.weight());
                }
                ik.put(chain.controller(), boneData);
            }
        }

        return ik;
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
}
