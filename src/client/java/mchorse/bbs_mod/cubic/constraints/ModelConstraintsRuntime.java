package mchorse.bbs_mod.cubic.constraints;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.cubic.ModelInstance;
import mchorse.bbs_mod.cubic.data.model.Model;
import mchorse.bbs_mod.cubic.data.model.ModelGroup;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.utils.MathUtils;

import java.io.File;
import java.util.Collections;
import java.util.Map;

public final class ModelConstraintsRuntime
{
    private ModelConstraintsRuntime()
    {
    }

    public static void clearCache()
    {
        ModelConstraintsCache.clear();
    }

    public static void invalidate(String modelId)
    {
        ModelConstraintsCache.invalidate(modelId);
    }

    public static void apply(ModelInstance instance)
    {
        if (instance == null || instance.id == null || instance.id.isEmpty() || !(instance.model instanceof Model model))
        {
            return;
        }

        ModelConstraintsCache.Compiled compiled = ModelConstraintsCache.get(instance.id);

        if (compiled == null || compiled.bones() == null || compiled.bones().isEmpty())
        {
            return;
        }

        applyToModel(model, compiled.bones());
    }

    public static Map<String, ModelConstraintsConfig.BoneConstraint> getBones(String modelId)
    {
        if (modelId == null || modelId.isEmpty())
        {
            return Collections.emptyMap();
        }

        ModelConstraintsCache.Compiled compiled = ModelConstraintsCache.get(modelId);

        if (compiled == null || compiled.bones() == null || compiled.bones().isEmpty())
        {
            return Collections.emptyMap();
        }

        return compiled.bones();
    }

    private static void applyToModel(Model model, Map<String, ModelConstraintsConfig.BoneConstraint> bones)
    {
        for (ModelGroup group : model.getAllGroups())
        {
            if (group == null)
            {
                continue;
            }

            ModelConstraintsConfig.BoneConstraint c = bones.get(group.id);

            if (c == null || !c.enabled())
            {
                continue;
            }

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

            group.current.rotate.x = MathUtils.clamp(group.current.rotate.x, minX, maxX);
            group.current.rotate.y = MathUtils.clamp(group.current.rotate.y, minY, maxY);
            group.current.rotate.z = MathUtils.clamp(group.current.rotate.z, minZ, maxZ);
        }
    }

    static File getConstraintsFile(String modelId)
    {
        return modelId == null ? null : BBSMod.getAssetsPath(ModelManager.MODELS_PREFIX + modelId + "/constraints.json");
    }
}
