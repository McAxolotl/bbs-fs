package mchorse.bbs_mod.cubic.constraints;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class ModelConstraintsCache
{
    public record Compiled(File file, long lastModified, Map<String, ModelConstraintsConfig.BoneConstraint> bones)
    {
    }

    private static final Map<String, Compiled> CACHE = new HashMap<>();

    private ModelConstraintsCache()
    {
    }

    public static void clear()
    {
        CACHE.clear();
    }

    public static void invalidate(String modelId)
    {
        if (modelId != null)
        {
            CACHE.remove(modelId);
        }
    }

    public static Compiled get(String modelId)
    {
        if (modelId == null || modelId.isEmpty())
        {
            return null;
        }

        File file = ModelConstraintsRuntime.getConstraintsFile(modelId);
        long lm = file != null && file.exists() ? file.lastModified() : -1L;
        Compiled cached = CACHE.get(modelId);

        if (cached != null && cached.lastModified == lm)
        {
            return cached;
        }

        ModelConstraintsConfig config = lm < 0 ? null : ModelConstraintsIO.read(modelId);
        Map<String, ModelConstraintsConfig.BoneConstraint> bones = config == null || config.bones() == null
            ? Collections.emptyMap()
            : Collections.unmodifiableMap(new HashMap<>(config.bones()));

        Compiled next = new Compiled(file, lm, bones);
        CACHE.put(modelId, next);

        return next;
    }
}

